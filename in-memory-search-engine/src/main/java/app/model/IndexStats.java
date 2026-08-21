package app.model;

/**
 * Read-only snapshot of index statistics returned by {@code GET /stats}.
 */
public record IndexStats(
        long totalDocuments,
        long uniqueTerms,
        double averageDocumentLength,
        long indexSizeBytes
) {
}
