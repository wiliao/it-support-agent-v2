package com.itsupport.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Objects;

/**
 * Shared domain model for Knowledge Base entries.
 * Used by both in-memory PoC and Redis production implementations.
 */
@Getter
@Builder
@NoArgsConstructor
// ❌ REMOVED: @AllArgsConstructor - causes duplicate constructor with varargs
public class KbEntry {
    private String id;
    private String category;
    private String issue;
    private String resolution;
    private String[] keywords;

    /**
     * Constructor with varargs keywords.
     * Accepts BOTH:
     *   - new KbEntry(..., "kw1", "kw2")           ← varargs style
     *   - new KbEntry(..., new String[]{"kw1"})    ← array style
     */
    public KbEntry(String id, String category, String issue, String resolution, String... keywords) {
        this.id = id;
        this.category = category;
        this.issue = issue;
        this.resolution = resolution;
        // Defensive copy to ensure immutability
        this.keywords = keywords != null ? Arrays.copyOf(keywords, keywords.length) : null;
    }

    /**
     * Generate searchable text for embedding generation
     */
    public String getSearchableText() {
        return String.format("%s %s %s %s",
                category != null ? category : "",
                issue != null ? issue : "",
                resolution != null ? resolution : "",
                keywords != null ? String.join(" ", keywords) : "");
    }

    /**
     * Check if entry matches a search query (keyword-based, for PoC)
     */
    public boolean matchesQuery(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase().trim();

        if (keywords != null) {
            for (String kw : keywords) {
                if (kw != null && kw.toLowerCase().contains(q)) return true;
                assert kw != null;
                if (q.contains(kw.toLowerCase())) return true;
            }
        }
        return (issue != null && issue.toLowerCase().contains(q))
                || (resolution != null && resolution.toLowerCase().contains(q))
                || (category != null && category.toLowerCase().contains(q));
    }

    /**
     * Safe getter that returns defensive copy
     */
    public String[] getKeywords() {
        return keywords != null ? Arrays.copyOf(keywords, keywords.length) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KbEntry kbEntry)) return false;
        return Objects.equals(id, kbEntry.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "KbEntry{id='" + id + "', category='" + category + "', issue='" + issue + "'}";
    }
}
