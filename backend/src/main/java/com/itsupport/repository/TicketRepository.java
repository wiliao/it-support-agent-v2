package com.itsupport.repository;

import com.itsupport.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByStatusOrderByCreatedAtDesc(Ticket.TicketStatus status);

    List<Ticket> findAllByOrderByCreatedAtDesc();

    @Query("SELECT t FROM Ticket t WHERE t.status IN ('OPEN','PENDING_REVIEW') ORDER BY t.createdAt DESC")
    List<Ticket> findActiveTickets();

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = :status")
    long countByStatus(Ticket.TicketStatus status);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.routeDecision = 'DRAFT'")
    long countDrafted();

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.feedback = 'APPROVED'")
    long countApproved();

    /**
     * Full-text LIKE search over resolved tickets for tool calling.
     * <p>
     * Searches title and description (case-insensitive) for the query string.
     * Filters to RESOLVED status with a non-null finalResponse (actual resolution recorded).
     * Uses native query for H2 compatibility with LIMIT.
     * <p>
     * Note: uses {@code final_response} column (maps to {@code Ticket.finalResponse})
     * since the Ticket entity has no dedicated resolutionNotes field.
     *
     * @param query search string from the LLM tool call
     * @param limit max results to return
     * @return resolved tickets ordered by most recently updated
     */
    @Query(value = """
            SELECT * FROM tickets
            WHERE status = 'RESOLVED'
              AND final_response IS NOT NULL
              AND (
                LOWER(title)       LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(description) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            ORDER BY updated_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Ticket> findResolvedByFullText(@Param("query") String query,
                                        @Param("limit") int limit);
}
