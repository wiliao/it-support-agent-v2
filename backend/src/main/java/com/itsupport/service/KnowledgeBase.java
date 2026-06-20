package com.itsupport.service;

import com.itsupport.model.KbEntry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PoC Knowledge Base: Simulated resolved tickets for semantic matching.
 * Uses simple keyword matching — replace with KnowledgeBaseRedis for production.
 * <p>
 * Sample data is loaded once at class initialisation from the same CSV file used
 * by {@link KnowledgeBaseRedis}: {@code classpath:kb-sample-data.csv}.
 * <p>
 * Expected CSV header:
 * <pre>
 *   id,category,issue,resolution,keywords
 * </pre>
 *
 * @see KnowledgeBaseProvider
 * @see KnowledgeBaseRedis
 */
@Slf4j
public class KnowledgeBase implements KnowledgeBaseProvider {

    private static final String SAMPLE_DATA_PATH = "kb-sample-data.csv";

    private static final String COL_ID         = "id";
    private static final String COL_CATEGORY   = "category";
    private static final String COL_ISSUE      = "issue";
    private static final String COL_RESOLUTION = "resolution";
    private static final String COL_KEYWORDS   = "keywords";

    // Loaded once on first use — same lifecycle as the original static List.of(...)
    public static final List<KbEntry> ENTRIES = loadEntriesFromCsv();

    // ============ CSV Loader ============

    private static List<KbEntry> loadEntriesFromCsv() {
        ClassPathResource resource = new ClassPathResource(SAMPLE_DATA_PATH);

        if (!resource.exists()) {
            log.error("Sample data file not found on classpath: {}", SAMPLE_DATA_PATH);
            return Collections.emptyList();
        }

        List<KbEntry> entries = new ArrayList<>();

        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .get()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                try {
                    entries.add(recordToEntry(record));
                } catch (Exception e) {
                    log.warn("Skipping malformed CSV row {}: {}", record.getRecordNumber(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Failed to read sample data CSV: {}", SAMPLE_DATA_PATH, e);
        }

        log.info("Loaded {} KB entries from {}", entries.size(), SAMPLE_DATA_PATH);
        return Collections.unmodifiableList(entries);
    }

    private static KbEntry recordToEntry(CSVRecord record) {
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

    private static String requireColumn(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            throw new IllegalArgumentException("Missing required column: " + column);
        }
        String value = record.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Blank value for required column: " + column);
        }
        return value;
    }

    // ============ Interface Implementation (PoC Logic) ============

    @Override
    public List<KbEntry> search(String query) {
        return search(query, null);
    }

    @Override
    public List<KbEntry> search(String query, String category) {
        if (query == null && category == null) {
            return new ArrayList<>(ENTRIES);
        }

        return ENTRIES.stream()
                .filter(entry -> entry.matchesQuery(query))
                .filter(entry -> category == null || category.equalsIgnoreCase(entry.getCategory()))
                .limit(5)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<KbEntry> getById(String id) {
        if (id == null) return Optional.empty();
        return ENTRIES.stream()
                .filter(e -> id.equals(e.getId()))
                .findFirst();
    }

    @Override
    public boolean addEntry(KbEntry entry) {
        log.warn("addEntry() called on in-memory KnowledgeBase - changes not persisted. Use KnowledgeBaseRedis for production.");
        return false;
    }

    @Override
    public boolean deleteEntry(String id) {
        log.warn("deleteEntry() called on in-memory KnowledgeBase - operation not supported. Use KnowledgeBaseRedis for production.");
        return false;
    }

    // ============ Utility Methods ============

    public static List<KbEntry> getAllEntries() {
        return new ArrayList<>(ENTRIES);
    }

    public static List<KbEntry> getByCategory(String category) {
        if (category == null) return getAllEntries();
        return ENTRIES.stream()
                .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                .collect(Collectors.toList());
    }
}
