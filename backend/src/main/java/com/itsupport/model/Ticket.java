package com.itsupport.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    private TicketCategory category;

    private String submitterEmail;
    private String submitterName;
    private String channel; // EMAIL, PORTAL, SLACK

    // AI-generated fields
    @Column(columnDefinition = "TEXT")
    private String aiDraftResponse;

    private Double similarityScore;
    private String matchedTicketRef;

    @Enumerated(EnumType.STRING)
    private RouteDecision routeDecision; // DRAFT, ESCALATE

    private String assignedTo;

    // Feedback
    @Enumerated(EnumType.STRING)
    private FeedbackType feedback; // APPROVED, APPROVED_EDITED, REJECTED

    @Column(columnDefinition = "TEXT")
    private String finalResponse;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = TicketStatus.OPEN;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum TicketStatus {
        OPEN, PENDING_REVIEW, ESCALATED, RESOLVED, CLOSED
    }

    public enum TicketPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum TicketCategory {
        PASSWORD_RESET, SOFTWARE_INSTALL, HARDWARE_ISSUE, NETWORK_ACCESS,
        EMAIL_ISSUE, PRINTER, VPN, ACCOUNT_ACCESS, SECURITY, OTHER
    }

    public enum RouteDecision {
        DRAFT, ESCALATE
    }

    public enum FeedbackType {
        APPROVED, APPROVED_EDITED, REJECTED
    }
}
