package com.itsupport.service;

import com.itsupport.model.Ticket;
import com.itsupport.model.TicketDtos;
import com.itsupport.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketAiService aiService;

    @Transactional
    public TicketDtos.TicketResponse submitTicket(TicketDtos.SubmitTicketRequest request) {
        log.info("New ticket submitted: {}", request.getTitle());

        // Create ticket
        Ticket ticket = Ticket.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .submitterEmail(request.getSubmitterEmail())
                .submitterName(request.getSubmitterName())
                .channel(request.getChannel())
                .status(Ticket.TicketStatus.OPEN)
                .build();

        ticket = ticketRepository.save(ticket);

        // Run AI analysis
        try {
            TicketDtos.AiAnalysisResult analysis = aiService.analyzeTicket(
                    request.getTitle(), request.getDescription());

            // Apply AI results
            if (analysis.getCategory() != null) {
                try {
                    ticket.setCategory(Ticket.TicketCategory.valueOf(analysis.getCategory()));
                } catch (IllegalArgumentException e) {
                    ticket.setCategory(Ticket.TicketCategory.OTHER);
                }
            }
            if (analysis.getPriority() != null) {
                try {
                    ticket.setPriority(Ticket.TicketPriority.valueOf(analysis.getPriority()));
                } catch (IllegalArgumentException e) {
                    ticket.setPriority(Ticket.TicketPriority.MEDIUM);
                }
            }

            ticket.setSimilarityScore(analysis.getSimilarityScore());
            ticket.setMatchedTicketRef(analysis.getMatchedTicketRef());

            if ("DRAFT".equals(analysis.getRouteDecision()) && analysis.getDraftResponse() != null) {
                ticket.setRouteDecision(Ticket.RouteDecision.DRAFT);
                ticket.setAiDraftResponse(analysis.getDraftResponse());
                ticket.setStatus(Ticket.TicketStatus.PENDING_REVIEW);
            } else {
                ticket.setRouteDecision(Ticket.RouteDecision.ESCALATE);
                ticket.setStatus(Ticket.TicketStatus.ESCALATED);
                // Auto-assign based on category
                ticket.setAssignedTo(getSpecialistForCategory(ticket.getCategory()));
            }

        } catch (Exception e) {
            log.error("AI analysis failed for ticket {}", ticket.getId(), e);
            ticket.setRouteDecision(Ticket.RouteDecision.ESCALATE);
            ticket.setStatus(Ticket.TicketStatus.ESCALATED);
        }

        ticket = ticketRepository.save(ticket);
        return toResponse(ticket);
    }

    @Transactional
    public TicketDtos.TicketResponse processApproval(Long ticketId, TicketDtos.ApprovalRequest approval) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + ticketId));

        switch (approval.getAction()) {
            case "APPROVE":
                ticket.setFeedback(Ticket.FeedbackType.APPROVED);
                ticket.setFinalResponse(ticket.getAiDraftResponse());
                ticket.setStatus(Ticket.TicketStatus.RESOLVED);
                break;
            case "APPROVE_EDITED":
                ticket.setFeedback(Ticket.FeedbackType.APPROVED_EDITED);
                ticket.setFinalResponse(approval.getEditedResponse());
                ticket.setStatus(Ticket.TicketStatus.RESOLVED);
                break;
            case "REJECT":
                ticket.setFeedback(Ticket.FeedbackType.REJECTED);
                ticket.setStatus(Ticket.TicketStatus.ESCALATED);
                ticket.setAssignedTo(getSpecialistForCategory(ticket.getCategory()));
                break;
        }

        return toResponse(ticketRepository.save(ticket));
    }

    public List<TicketDtos.TicketResponse> getAllTickets() {
        return ticketRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public TicketDtos.TicketResponse getTicket(Long id) {
        return ticketRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + id));
    }

    public TicketDtos.DashboardStats getStats() {
        long totalOpen = ticketRepository.countByStatus(Ticket.TicketStatus.OPEN);
        long totalPending = ticketRepository.countByStatus(Ticket.TicketStatus.PENDING_REVIEW);
        long totalEscalated = ticketRepository.countByStatus(Ticket.TicketStatus.ESCALATED);
        long totalResolved = ticketRepository.countByStatus(Ticket.TicketStatus.RESOLVED);
        long totalDrafted = ticketRepository.countDrafted();
        long totalApproved = ticketRepository.countApproved();
        double approvalRate = totalDrafted > 0 ? (double) totalApproved / totalDrafted * 100 : 0;

        return TicketDtos.DashboardStats.builder()
                .totalOpen(totalOpen)
                .totalPendingReview(totalPending)
                .totalEscalated(totalEscalated)
                .totalResolved(totalResolved)
                .totalDrafted(totalDrafted)
                .totalApproved(totalApproved)
                .approvalRate(Math.round(approvalRate * 10.0) / 10.0)
                .build();
    }

    private String getSpecialistForCategory(Ticket.TicketCategory category) {
        if (category == null) return "it-team@company.com";
        return switch (category) {
            case SECURITY -> "security-team@company.com";
            case NETWORK_ACCESS, VPN -> "network-team@company.com";
            case HARDWARE_ISSUE -> "hardware-team@company.com";
            case SOFTWARE_INSTALL, EMAIL_ISSUE -> "software-team@company.com";
            default -> "it-helpdesk@company.com";
        };
    }

    private TicketDtos.TicketResponse toResponse(Ticket t) {
        return TicketDtos.TicketResponse.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .status(t.getStatus() != null ? t.getStatus().name() : null)
                .priority(t.getPriority() != null ? t.getPriority().name() : null)
                .category(t.getCategory() != null ? t.getCategory().name() : null)
                .submitterEmail(t.getSubmitterEmail())
                .submitterName(t.getSubmitterName())
                .channel(t.getChannel())
                .aiDraftResponse(t.getAiDraftResponse())
                .similarityScore(t.getSimilarityScore())
                .matchedTicketRef(t.getMatchedTicketRef())
                .routeDecision(t.getRouteDecision() != null ? t.getRouteDecision().name() : null)
                .assignedTo(t.getAssignedTo())
                .feedback(t.getFeedback() != null ? t.getFeedback().name() : null)
                .finalResponse(t.getFinalResponse())
                .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().toString() : null)
                .updatedAt(t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null)
                .build();
    }
}
