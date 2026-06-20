package com.itsupport.service;

import com.itsupport.model.KbEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Production Knowledge Base using Redis Stack via Spring AI Vector Store.
 * <p>
 * Compatible with: JDK 21, Spring Boot 3.5.x, Spring AI 1.1.2
 * <p>
 * Sample data is loaded from {@code classpath:kb-sample-data.csv} on first boot
 * (when the vector store is found to be empty). The CSV is parsed with
 * Apache Commons CSV and must have the following header row:
 * <pre>
 *   id,category,issue,resolution,keywords
 * </pre>
 * The {@code keywords} column is a comma-separated list enclosed in the CSV
 * cell (i.e. the entire cell value is {@code "password,login,locked,..."}), which
 * Commons CSV handles correctly when the cell is quoted.
 * <p>
 * Required dependency (add to pom.xml if not already present):
 * <pre>{@code
 * <dependency>
 *     <groupId>org.apache.commons</groupId>
 *     <artifactId>commons-csv</artifactId>
 *     <version>1.11.0</version>
 * </dependency>
 * }</pre>
 *
 * @see KnowledgeBaseProvider
 */
@Slf4j
@RequiredArgsConstructor
public class KnowledgeBaseRedis implements KnowledgeBaseProvider {

    private final VectorStore vectorStore;

    // CSV column names — must match the header row in kb-sample-data.csv
    private static final String COL_ID         = "id";
    private static final String COL_CATEGORY   = "category";
    private static final String COL_ISSUE      = "issue";
    private static final String COL_RESOLUTION = "resolution";
    private static final String COL_KEYWORDS   = "keywords";

    // Classpath location of the seed data file (src/main/resources/kb-sample-data.csv)
    private static final String SAMPLE_DATA_PATH = "kb-sample-data.csv";

    // Search configuration
    private static final int    MAX_RESULTS    = 5;
    private static final double MIN_SIMILARITY = 0.5; // nomic-embed-text scores 0.5–0.7 range

    // Document metadata keys
    private static final String META_CATEGORY   = "category";
    private static final String META_RESOLUTION = "resolution";
    private static final String META_KEYWORDS   = "keywords";
    private static final String META_ISSUE      = "issue";

    // ============ Lifecycle ============

    @PostConstruct
    public void init() {
        try {
            var probe = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("password reset")   // ✅ domain-relevant, not generic "system"
                            .topK(1)
                            .similarityThreshold(MIN_SIMILARITY)  // ✅ consistent with search()
                            .build());

            if (probe.isEmpty()) {
                log.info("Redis KB empty – loading sample entries from {}", SAMPLE_DATA_PATH);
                loadSampleDataFromCsv();
            } else {
                log.info("Redis KB ready – entries already indexed, skipping seed load"); // ✅ no size
            }
        } catch (Exception e) {
            log.warn("Could not probe Redis KB (may not be ready yet): {}", e.getMessage());
        }
    }

    // ============ KnowledgeBaseProvider Implementation ============

    @Override
    public List<KbEntry> search(String query) {
        return search(query, null);
    }

    @Override
    public List<KbEntry> search(String query, String category) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(MAX_RESULTS)
                    .similarityThreshold(MIN_SIMILARITY);

            if (category != null && !category.isBlank()) {
                builder.filterExpression(
                        String.format("%s == '%s'", META_CATEGORY, category));
            }

            List<Document> results = vectorStore.similaritySearch(builder.build());
            log.debug("Vector search returned {} results for query: {}", results.size(), query);
            return results.stream()
                    .map(this::documentToEntry)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Search failed for query: {}", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * Best-effort retrieval by ID via similarity search with an ID metadata filter.
     * <p>
     * NOTE: Most vector stores (including Redis Stack) do not support filtering by
     * the document's primary ID field through the portable filter expression API.
     * If direct ID lookup is critical, consider maintaining a secondary index
     * (e.g. a Redis HASH or Spring Data repository) keyed by entry ID.
     * <p>
     * This implementation filters on the "id" metadata field that we explicitly
     * store in the document's metadata map, which Redis Stack can index as a text tag.
     */
    @Override
    public Optional<KbEntry> getById(String id) {
        try {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("system")                 // neutral query; result driven by filter
                            .filterExpression(String.format("id == '%s'", id))
                            .topK(1)
                            .similarityThresholdAll()        // accept any score — filter is the discriminator
                            .build());

            if (results.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(documentToEntry(results.getFirst()));

        } catch (Exception e) {
            log.error("Failed to retrieve entry {}", id, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean addEntry(KbEntry entry) {
        try {
            vectorStore.add(List.of(entryToDocument(entry)));
            log.debug("Indexed entry: {}", entry.getId());
            return true;
        } catch (Exception e) {
            log.error("Failed to add entry {}", entry.getId(), e);
            return false;
        }
    }

    @Override
    public boolean deleteEntry(String id) {
        try {
            vectorStore.delete(List.of(id));
            log.debug("Deleted entry: {}", id);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete entry {}", id, e);
            return false;
        }
    }

    // ============ CSV Sample Data Loader ============

    /**
     * Parses {@code kb-sample-data.csv} from the classpath and indexes every row.
     * <p>
     * Expected CSV format (RFC 4180, with header row):
     * <pre>
     *   id,category,issue,resolution,keywords
     * </pre>
     * Multi-line cell values (e.g. numbered resolution steps) must be quoted.
     * The {@code keywords} cell contains a plain comma-separated list inside
     * a single quoted cell, e.g. {@code "password,login,locked,reset"}.
     */
    private void loadSampleDataFromCsv() {
        ClassPathResource resource = new ClassPathResource(SAMPLE_DATA_PATH);

        if (!resource.exists()) {
            log.error("Sample data file not found on classpath: {}", SAMPLE_DATA_PATH);
            return;
        }

        List<KbEntry> entries = new ArrayList<>();

        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()               // first row is the header
                     .setSkipHeaderRecord(true) // skip it when iterating records
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .get()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                try {
                    KbEntry entry = recordToEntry(record);
                    entries.add(entry);
                } catch (Exception e) {
                    log.warn("Skipping malformed CSV row {}: {}", record.getRecordNumber(), e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("Failed to read sample data CSV: {}", SAMPLE_DATA_PATH, e);
            return;
        }

        int success = 0;
        for (KbEntry entry : entries) {
            if (addEntry(entry)) success++;
        }
        log.info("Loaded {}/{} sample entries from {}", success, entries.size(), SAMPLE_DATA_PATH);
    }

    /**
     * Maps a single {@link CSVRecord} to a {@link KbEntry}.
     * <p>
     * The {@code keywords} column value is a comma-separated string within the CSV
     * cell (e.g. {@code "password,login,locked"}). It is split on commas and trimmed
     * to produce the keywords array.
     */
    private KbEntry recordToEntry(CSVRecord record) {
        String id         = requireColumn(record, COL_ID);
        String category   = requireColumn(record, COL_CATEGORY);
        String issue      = requireColumn(record, COL_ISSUE);
        String resolution = requireColumn(record, COL_RESOLUTION);

        String keywordsRaw = record.isMapped(COL_KEYWORDS) ? record.get(COL_KEYWORDS) : "";
        String[] keywords = Arrays.stream(keywordsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);

        return new KbEntry(id, category, issue, resolution, keywords);
    }

    /**
     * Retrieves a required column value, throwing if it is absent or blank.
     */
    private String requireColumn(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            throw new IllegalArgumentException("Missing required column: " + column);
        }
        String value = record.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Blank value for required column: " + column);
        }
        return value;
    }

    // ============ Conversion Methods ============

    /**
     * Convert KbEntry → Spring AI Document.
     * <p>
     * The KbEntry ID is stored both as the document's primary ID and redundantly
     * in metadata, so that the filterExpression("id == '...'") path in
     * {@link #getById(String)} can locate the document via a metadata filter.
     */
    private Document entryToDocument(KbEntry entry) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id",            entry.getId());
        metadata.put(META_CATEGORY,   entry.getCategory());
        metadata.put(META_ISSUE,      entry.getIssue());
        metadata.put(META_RESOLUTION, entry.getResolution());

        String[] keywords = entry.getKeywords();
        if (keywords != null && keywords.length > 0) {
            metadata.put(META_KEYWORDS, String.join(",", keywords));
        }

        return Document.builder()
                .id(entry.getId())
                .text(entry.getSearchableText())  // full text used for embedding similarity
                .metadata(metadata)
                .build();
    }

    /**
     * Convert Spring AI Document → KbEntry.
     * <p>
     * Issue is read from metadata (not getText()) because getText() contains the
     * full combined searchable text rather than the raw issue field.
     */
    private KbEntry documentToEntry(Document doc) {
        try {
            if (doc == null) return null;

            Map<String, Object> meta = doc.getMetadata();

            String id          = doc.getId();
            String issue       = safeGetString(meta, META_ISSUE);
            String category    = safeGetString(meta, META_CATEGORY);
            String resolution  = safeGetString(meta, META_RESOLUTION);
            String keywordsRaw = safeGetString(meta, META_KEYWORDS);

            String[] keywords = (keywordsRaw != null && !keywordsRaw.isBlank())
                    ? keywordsRaw.split(",")
                    : new String[0];

            return new KbEntry(id, category, issue, resolution, keywords);

        } catch (Exception e) {
            log.warn("Failed to parse document: {}", doc.getId(), e);
            return null;
        }
    }

    private String safeGetString(Map<String, Object> meta, String key) {
        Object value = meta.get(key);
        return value != null ? value.toString() : null;
    }
}
