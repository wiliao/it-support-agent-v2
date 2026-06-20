package com.itsupport.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class TicketDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitTicketRequest {
        @NotBlank
        private String title;
        @NotBlank
        private String description;
        @Email
        private String submitterEmail;
        private String submitterName;
        private String channel = "PORTAL";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketResponse {
        private Long id;
        private String title;
        private String description;
        private String status;
        private String priority;
        private String category;
        private String submitterEmail;
        private String submitterName;
        private String channel;
        private String aiDraftResponse;
        private Double similarityScore;
        private String matchedTicketRef;
        private String routeDecision;
        private String assignedTo;
        private String feedback;
        private String finalResponse;
        private String createdAt;
        private String updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalRequest {
        private String action; // APPROVE, APPROVE_EDITED, REJECT
        private String editedResponse;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardStats {
        private long totalOpen;
        private long totalPendingReview;
        private long totalEscalated;
        private long totalResolved;
        private long totalDrafted;
        private long totalApproved;
        private double approvalRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiAnalysisResult {
        private String category;
        private String priority;
        private String routeDecision;
        private double similarityScore;
        private String matchedTicketRef;
        private String draftResponse;
        private String reasoning;
    }
}
