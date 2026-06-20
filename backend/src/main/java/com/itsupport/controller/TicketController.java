package com.itsupport.controller;

import com.itsupport.model.TicketDtos;
import com.itsupport.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class TicketController {

    private final TicketService ticketService;

    /**
     * Submit a new ticket — triggers AI analysis pipeline
     */
    @PostMapping
    public ResponseEntity<TicketDtos.TicketResponse> submitTicket(
            @Valid @RequestBody TicketDtos.SubmitTicketRequest request) {
        return ResponseEntity.ok(ticketService.submitTicket(request));
    }

    /**
     * Get all tickets
     */
    @GetMapping
    public ResponseEntity<List<TicketDtos.TicketResponse>> getAllTickets() {
        return ResponseEntity.ok(ticketService.getAllTickets());
    }

    /**
     * Get single ticket
     */
    @GetMapping("/{id}")
    public ResponseEntity<TicketDtos.TicketResponse> getTicket(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getTicket(id));
    }

    /**
     * IT technician approves, edits, or rejects an AI draft
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<TicketDtos.TicketResponse> approveTicket(
            @PathVariable Long id,
            @RequestBody TicketDtos.ApprovalRequest approval) {
        return ResponseEntity.ok(ticketService.processApproval(id, approval));
    }

    /**
     * Dashboard statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<TicketDtos.DashboardStats> getStats() {
        return ResponseEntity.ok(ticketService.getStats());
    }
}
