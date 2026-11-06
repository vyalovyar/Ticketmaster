package com.ticket.ai.application;

public record SearchDiagnostics(
        QueryIntent intent,
        boolean semanticRequested,
        String embeddingError,
        int indexed,
        int matchingFilters,
        int withEmbeddings,
        int keywordHits
) {

    public SearchDiagnostics withEmbeddingError(String error) {
        return new SearchDiagnostics(intent, semanticRequested, error,
                indexed, matchingFilters, withEmbeddings, keywordHits);
    }

    public boolean semanticSkipped() {
        return semanticRequested && embeddingError != null && !embeddingError.isBlank();
    }

    public boolean hasKeywords() {
        return intent != null && intent.keywords() != null && !intent.keywords().isBlank();
    }
}
