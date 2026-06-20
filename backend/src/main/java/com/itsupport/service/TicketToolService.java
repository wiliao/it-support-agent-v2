package com.itsupport.service;

import com.itsupport.model.KbEntry;
import com.itsupport.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring AI tool service exposing two search tools to the LLM:
 * <ol>
 *   <li>{@code searchResolvedTickets} — full-text LIKE search over H2 resolved tickets</li>
 *   <li>{@code searchKnowledgeBase}   — vector similarity search over Redis KB</li>
 * </ol>
 *
 * The LLM is instructed (via the system prompt in {@link TicketAiService}) to call
 * {@code searchResolvedTickets} first and only fall back to {@code searchKnowledgeBase}
 * if no relevant resolved ticket is found.
 * <p>
 * Tool return values are plain strings — the LLM reads them as context and reasons
 * about confidence and routing without any numeric similarity score from the app.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketToolService {

    private final TicketRepository ticketRepository;
    private final KnowledgeBaseProvider knowledgeBase;

    // Maximum results returned by each tool to keep context window manageable
    private static final int MAX_H2_RESULTS = 3;
    private static final int MAX_KB_RESULTS = 3;

    /**
     * Tool 1: Search previously resolved tickets in H2.
     * <p>
     * Performs a full-text LIKE search on ticket title and description,
     * filtered to RESOLVED status only. Returns a formatted summary of
     * matching tickets including their resolution notes.
     *
     * @param query natural language search query from the LLM
     * @return formatted string of matching resolved tickets, or a no-match message
     */
    @Tool(description = """
            Search previously resolved IT support tickets in the database using a full-text search.
            Use this tool FIRST before searching the knowledge base.
            Returns resolved tickets whose title or description matches the query.
            Provides real resolution history from actual past tickets.
            """)
    public String searchResolvedTickets(String query) {
        log.debug("Tool: searchResolvedTickets query='{}'", query);
        try {
            var tickets = ticketRepository.findResolvedByFullText(query, MAX_H2_RESULTS);

            if (tickets.isEmpty()) {
                log.debug("Tool: searchResolvedTickets — no results");
                return "NO_RESULTS: No previously resolved tickets found matching: " + query;
            }

            String result = tickets.stream()
                    .map(t -> """
                            RESOLVED TICKET [%s]
                            Title: %s
                            Category: %s
                            Description: %s
                            Resolution: %s
                            """.formatted(
                            t.getId(),
                            t.getTitle(),
                            t.getCategory(),
                            t.getDescription(),
                            t.getFinalResponse() != null ? t.getFinalResponse() : "No resolution recorded"
                    ))
                    .collect(Collectors.joining("\n---\n"));

            log.debug("Tool: searchResolvedTickets — {} results", tickets.size());
            return result;

        } catch (Exception e) {
            log.error("Tool: searchResolvedTickets failed", e);
            return "ERROR: Could not search resolved tickets — " + e.getMessage();
        }
    }

    /**
     * Tool 2: Search the IT knowledge base in Redis vector store.
     * <p>
     * Performs a vector similarity search using the embedded query.
     * Use this as a fallback when no resolved ticket match is found.
     *
     * @param query natural language search query from the LLM
     * @return formatted string of matching KB entries, or a no-match message
     */
    @Tool(description = """
            Search the IT support knowledge base for known issues and standard resolutions.
            Use this tool ONLY if searchResolvedTickets returns NO_RESULTS.
            Returns KB articles with step-by-step resolution guidance.
            """)
    public String searchKnowledgeBase(String query) {
        log.debug("Tool: searchKnowledgeBase query='{}'", query);
        try {
            List<KbEntry> results = knowledgeBase.search(query);

            if (results.isEmpty()) {
                log.debug("Tool: searchKnowledgeBase — no results");
                return "NO_RESULTS: No knowledge base entries found matching: " + query;
            }

            String result = results.stream()
                    .limit(MAX_KB_RESULTS)
                    .map(kb -> """
                            KB ARTICLE [%s]
                            Category: %s
                            Issue Pattern: %s
                            Resolution:
                            %s
                            """.formatted(
                            kb.getId(),
                            kb.getCategory(),
                            kb.getIssue(),
                            kb.getResolution()
                    ))
                    .collect(Collectors.joining("\n---\n"));

            log.debug("Tool: searchKnowledgeBase — {} results", results.size());
            return result;

        } catch (Exception e) {
            log.error("Tool: searchKnowledgeBase failed", e);
            return "ERROR: Could not search knowledge base — " + e.getMessage();
        }
    }
}
