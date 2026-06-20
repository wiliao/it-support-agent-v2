package com.itsupport.service;

import com.itsupport.model.KbEntry;  // ✅ Use shared domain class

import java.util.List;
import java.util.Optional;

/**
 * Common interface for knowledge base implementations.
 * Enables easy swapping between in-memory PoC and production Redis.
 */
public interface KnowledgeBaseProvider {

    List<KbEntry> search(String query);

    List<KbEntry> search(String query, String category);

    Optional<KbEntry> getById(String id);

    boolean addEntry(KbEntry entry);  // ✅ Now uses shared KbEntry type

    boolean deleteEntry(String id);
}
