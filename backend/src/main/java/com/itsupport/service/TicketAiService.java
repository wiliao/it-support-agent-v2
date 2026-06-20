package com.itsupport.service;

import com.itsupport.model.TicketDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Core AI service using Spring AI ChatClient with tool calling.
 * <p>
 * Analysis pipeline:
 * <ol>
 *   <li>LLM calls {@code searchResolvedTickets} tool (H2) first</li>
 *   <li>If no H2 match → LLM calls {@code searchKnowledgeBase} tool (Redis)</li>
 *   <li>LLM reasons about confidence from tool results and drafts or escalates</li>
 * </ol>
 *
 * Similarity scoring ({@code calculateSimilarity}) has been removed — the LLM
 * now reasons about confidence directly from the tool results it receives.
 * Confidence is extracted from the structured LLM response (CONFIDENCE field).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketAiService {

    private final ChatClient chatClient;
    private final TicketToolService ticketToolService;

    // ============ Public API ============

    /**
     * Full AI analysis pipeline for a new ticket.
     * The LLM autonomously calls tools to gather context before responding.
     *
     * @param title       ticket title
     * @param description ticket description
     * @return structured analysis result with category, priority, route, and optional draft
     */
    public TicketDtos.AiAnalysisResult analyzeTicket(String title, String description) {
        log.debug("Analysing ticket: {}", title);

        boolean mustEscalate = containsEscalationKeywords(title + " " + description);

        String userPrompt = buildUserPrompt(title, description, mustEscalate);

        String llmResponse = chatClient.prompt()
                .system(buildSystemPrompt())
                .user(userPrompt)
                .tools(ticketToolService)   // ✅ Spring AI @Tool registration
                .call()
                .content();

        return parseAnalysisResponse(llmResponse, mustEscalate);
    }

    // ============ Prompt Builders ============

    private String buildSystemPrompt() {
        return """
                You are an IT support triage agent with access to two search tools:
                
                1. searchResolvedTickets — searches previously resolved tickets in the database (PREFERRED)
                2. searchKnowledgeBase   — searches the IT knowledge base articles (FALLBACK)
                
                TOOL USAGE RULES:
                - ALWAYS call searchResolvedTickets FIRST using the ticket title and description as the query
                - Only call searchKnowledgeBase if searchResolvedTickets returns NO_RESULTS
                - Do NOT call both tools unless the first returns NO_RESULTS
                - Base your confidence assessment on the relevance of tool results
                
                CONFIDENCE GUIDELINES:
                - High confidence (>=75%): tool returned a directly relevant match with clear resolution steps
                - Low confidence (<75%): no match found, vague match, or ambiguous issue
                
                ROUTING RULES:
                - DRAFT if confidence >= 75% AND no security/legal escalation triggers
                - ESCALATE if confidence < 75%, OR security/breach/legal keywords present, OR issue is ambiguous
                """;
    }

    private String buildUserPrompt(String title, String description, boolean mustEscalate) {
        return """
                Analyse the following IT support ticket. Use your tools to find relevant context,
                then respond in EXACTLY this format:
                
                CATEGORY: [one of: PASSWORD_RESET, SOFTWARE_INSTALL, HARDWARE_ISSUE, NETWORK_ACCESS, EMAIL_ISSUE, PRINTER, VPN, ACCOUNT_ACCESS, SECURITY, OTHER]
                PRIORITY: [one of: LOW, MEDIUM, HIGH, CRITICAL]
                CONFIDENCE: [0-100 integer, your assessment of match quality from tool results]
                SOURCE: [RESOLVED_TICKET or KNOWLEDGE_BASE or NONE]
                MATCHED_REF: [ID of the matched ticket or KB article, or NONE]
                ROUTE: [DRAFT or ESCALATE]
                REASONING: [1-2 sentences explaining the route decision and which tool provided context]
                DRAFT_RESPONSE:
                [If ROUTE is DRAFT: write a professional, empathetic IT support response. Be specific
                and actionable. Include numbered steps from the resolution you found. Address the user
                by referencing their specific issue. End with an offer to follow up.
                If ROUTE is ESCALATE: write "ESCALATE - no draft generated"]
                END_DRAFT
                
                --- TICKET ---
                Title: %s
                Description: %s
                
                --- RULES ---
                - Must escalate: %s
                - Write the draft AS IF you are the IT support agent writing to the user (use "I" and "we")
                - Do NOT include technical metadata or tool names in the draft response
                """.formatted(
                title,
                description,
                mustEscalate ? "YES (security/legal keyword detected)" : "NO"
        );
    }

    // ============ Escalation Guard ============

    private boolean containsEscalationKeywords(String text) {
        String lower = text.toLowerCase();
        List<String> triggers = List.of(
                "breach", "hacked", "ransomware", "legal", "lawsuit",
                "urgent ceo", "exec", "compliance", "gdpr", "data leak"
        );
        return triggers.stream().anyMatch(lower::contains);
    }

    // ============ Response Parser ============

    private TicketDtos.AiAnalysisResult parseAnalysisResponse(String llmResponse,
                                                              boolean mustEscalate) {
        TicketDtos.AiAnalysisResult result = new TicketDtos.AiAnalysisResult();

        try {
            String[] lines = llmResponse.split("\n");
            StringBuilder draft = new StringBuilder();
            boolean inDraft = false;

            for (String line : lines) {
                String trimmed = line.trim();

                if (trimmed.startsWith("CATEGORY:")) {
                    result.setCategory(trimmed.replace("CATEGORY:", "").trim());

                } else if (trimmed.startsWith("PRIORITY:")) {
                    result.setPriority(trimmed.replace("PRIORITY:", "").trim());

                } else if (trimmed.startsWith("CONFIDENCE:")) {
                    String raw = trimmed.replace("CONFIDENCE:", "").trim()
                            .replace("%", "");
                    try {
                        int pct = Integer.parseInt(raw);
                        result.setSimilarityScore(pct / 100.0); // store as 0.0–1.0
                    } catch (NumberFormatException ignored) {
                        result.setSimilarityScore(0.0);
                    }

                } else if (trimmed.startsWith("MATCHED_REF:")) {
                    String ref = trimmed.replace("MATCHED_REF:", "").trim();
                    result.setMatchedTicketRef("NONE".equalsIgnoreCase(ref) ? null : ref);

                } else if (trimmed.startsWith("ROUTE:")) {
                    String route = trimmed.replace("ROUTE:", "").trim();
                    result.setRouteDecision(mustEscalate ? "ESCALATE" : route);

                } else if (trimmed.startsWith("REASONING:")) {
                    result.setReasoning(trimmed.replace("REASONING:", "").trim());

                } else if (trimmed.equals("DRAFT_RESPONSE:")) {
                    inDraft = true;

                } else if (trimmed.equals("END_DRAFT")) {
                    inDraft = false;

                } else if (inDraft) {
                    draft.append(line).append("\n");
                }
            }

            String draftText = draft.toString().trim();
            if (!draftText.isEmpty() && !draftText.startsWith("ESCALATE")) {
                result.setDraftResponse(draftText);
            }

        } catch (Exception e) {
            log.error("Failed to parse LLM response", e);
            result.setCategory("OTHER");
            result.setPriority("MEDIUM");
            result.setRouteDecision("ESCALATE");
            result.setReasoning("Parse error — routing to human review");
        }

        // Safety net: never send a draft if escalating
        if ("ESCALATE".equals(result.getRouteDecision())) {
            result.setDraftResponse(null);
        }

        return result;
    }
}
