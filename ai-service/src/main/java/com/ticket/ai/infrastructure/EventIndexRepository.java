package com.ticket.ai.infrastructure;

import com.ticket.ai.application.QueryIntent;
import com.ticket.ai.application.SearchDiagnostics;
import com.ticket.ai.application.SearchResult;
import com.ticket.ai.config.EmbeddingProperties;
import com.ticket.ai.infrastructure.dto.CatalogEvent;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class EventIndexRepository {

    private static final Logger log = LoggerFactory.getLogger(EventIndexRepository.class);

    private static final String COLUMNS = """
            event_id, name, artist, location, event_date, price_from,
            country_code, city, venue_name, genres, description, image_url, source_url
            """;

    private static final String SCHEMA_TEMPLATE = """
            CREATE EXTENSION IF NOT EXISTS vector;

            CREATE TABLE IF NOT EXISTS ai_event_index (
                event_id uuid PRIMARY KEY,
                name text NOT NULL,
                artist text,
                location text,
                city text,
                country_code varchar(2),
                venue_name text,
                event_date timestamp,
                description text,
                image_url text,
                source_url text,
                price_from double precision,
                genres text[] NOT NULL DEFAULT '{}',
                content text NOT NULL,
                content_hash varchar(64) NOT NULL,
                embedding vector(%d),
                search_vector tsvector GENERATED ALWAYS AS (
                    to_tsvector('simple',
                        coalesce(name, '') || ' ' ||
                        coalesce(artist, '') || ' ' ||
                        coalesce(venue_name, '') || ' ' ||
                        coalesce(city, '') || ' ' ||
                        coalesce(country_code, '') || ' ' ||
                        coalesce(genres::text, '') || ' ' ||
                        coalesce(description, ''))
                ) STORED,
                updated_at timestamptz NOT NULL DEFAULT now()
            );

            CREATE INDEX IF NOT EXISTS ai_event_index_embedding_idx
                ON ai_event_index USING hnsw (embedding vector_cosine_ops);
            CREATE INDEX IF NOT EXISTS ai_event_index_search_idx
                ON ai_event_index USING gin (search_vector);
            CREATE INDEX IF NOT EXISTS ai_event_index_country_idx
                ON ai_event_index (country_code);
            CREATE INDEX IF NOT EXISTS ai_event_index_city_idx
                ON ai_event_index (lower(city));
            CREATE INDEX IF NOT EXISTS ai_event_index_date_idx
                ON ai_event_index (event_date);
            CREATE INDEX IF NOT EXISTS ai_event_index_genres_idx
                ON ai_event_index USING gin (genres);
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final EmbeddingProperties embeddingProperties;
    private volatile boolean schemaReady = false;

    public EventIndexRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbc,
                                EmbeddingProperties embeddingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbc = namedJdbc;
        this.embeddingProperties = embeddingProperties;
    }

    @PostConstruct
    public void initSchema() {
        try {
            ensureSchema();
        } catch (RuntimeException e) {
            log.warn("Could not initialize pgvector schema yet: {}", e.getMessage());
        }
    }

    public synchronized void ensureSchema() {
        if (schemaReady) {
            return;
        }
        jdbcTemplate.execute(SCHEMA_TEMPLATE.formatted(embeddingProperties.dimension()));
        syncEmbeddingDimension();
        schemaReady = true;
        log.info("pgvector schema is ready (dimension={})", embeddingProperties.dimension());
    }

    private void syncEmbeddingDimension() {
        String current = jdbcTemplate.queryForObject("""
                SELECT format_type(atttypid, atttypmod)
                FROM pg_attribute
                WHERE attrelid = 'ai_event_index'::regclass AND attname = 'embedding'
                """, String.class);
        String expected = "vector(" + embeddingProperties.dimension() + ")";
        if (current == null || current.equals(expected)) {
            return;
        }
        log.warn("Embedding dimension changed from {} to {}; index will be rebuilt", current, expected);
        jdbcTemplate.execute("UPDATE ai_event_index SET embedding = NULL, content_hash = ''");
        jdbcTemplate.execute("ALTER TABLE ai_event_index ALTER COLUMN embedding TYPE " + expected);
    }

    public long count() {
        ensureSchema();
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM ai_event_index", Long.class);
        return count == null ? 0 : count;
    }

    public Map<UUID, String> findHashes() {
        ensureSchema();
        Map<UUID, String> hashes = new HashMap<>();
        jdbcTemplate.query("SELECT event_id, content_hash FROM ai_event_index",
                (java.sql.ResultSet rs) -> {
                    hashes.put(rs.getObject("event_id", UUID.class), rs.getString("content_hash"));
                });
        return hashes;
    }

    public void upsertAll(List<CatalogEvent> events, List<float[]> embeddings,
                          List<String> contents, List<String> hashes) {
        ensureSchema();
        if (events.size() != embeddings.size() || events.size() != contents.size()
                || events.size() != hashes.size()) {
            throw new IllegalArgumentException("Events, embeddings, contents and hashes must have equal size");
        }

        String sql = """
                INSERT INTO ai_event_index (
                    event_id, name, artist, location, city, country_code, venue_name,
                    event_date, description, image_url, source_url, price_from, genres,
                    content, content_hash, embedding, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), now())
                ON CONFLICT (event_id) DO UPDATE SET
                    name = EXCLUDED.name,
                    artist = EXCLUDED.artist,
                    location = EXCLUDED.location,
                    city = EXCLUDED.city,
                    country_code = EXCLUDED.country_code,
                    venue_name = EXCLUDED.venue_name,
                    event_date = EXCLUDED.event_date,
                    description = EXCLUDED.description,
                    image_url = EXCLUDED.image_url,
                    source_url = EXCLUDED.source_url,
                    price_from = EXCLUDED.price_from,
                    genres = EXCLUDED.genres,
                    content = EXCLUDED.content,
                    content_hash = EXCLUDED.content_hash,
                    embedding = EXCLUDED.embedding,
                    updated_at = now()
                """;

        for (int i = 0; i < events.size(); i++) {
            CatalogEvent event = events.get(i);
            float[] embedding = embeddings.get(i);
            String content = contents.get(i);
            String hash = hashes.get(i);

            jdbcTemplate.update(sql, (PreparedStatement ps) -> {
                ps.setObject(1, event.id());
                ps.setString(2, event.name());
                ps.setString(3, event.artist());
                ps.setString(4, event.location());
                ps.setString(5, event.city());
                ps.setString(6, event.countryCode());
                ps.setString(7, event.venueName());
                ps.setObject(8, event.eventDate());
                ps.setString(9, event.description());
                ps.setString(10, event.imageUrl());
                ps.setString(11, event.sourceUrl());
                Double priceFrom = event.priceFrom();
                if (priceFrom != null) {
                    ps.setDouble(12, priceFrom);
                } else {
                    ps.setNull(12, Types.DOUBLE);
                }
                Array genres = ps.getConnection().createArrayOf("text", event.genres().toArray());
                ps.setArray(13, genres);
                ps.setString(14, content);
                ps.setString(15, hash);
                if (embedding == null || embedding.length == 0) {
                    ps.setNull(16, Types.VARCHAR);
                } else {
                    ps.setString(16, toVectorLiteral(embedding));
                }
            });
        }
    }

    public void delete(UUID eventId) {
        ensureSchema();
        jdbcTemplate.update("DELETE FROM ai_event_index WHERE event_id = ?", eventId);
    }

    public void deleteMissing(Set<UUID> activeIds) {
        ensureSchema();
        if (activeIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM ai_event_index");
            return;
        }
        List<UUID> ids = new ArrayList<>(activeIds);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        jdbcTemplate.update("DELETE FROM ai_event_index WHERE event_id NOT IN (" + placeholders + ")",
                ids.toArray());
    }

    public SearchResult search(QueryIntent intent, float[] queryVector, boolean semanticRequested,
                               String embeddingError, int limit) {
        ensureSchema();
        Filters filters = filters(intent);
        int fetchLimit = Math.max(limit * 5, 50);

        List<CatalogEvent> vectorResults = List.of();
        List<CatalogEvent> textResults = List.of();

        if (queryVector != null && queryVector.length > 0) {
            String vectorSql = "SELECT " + COLUMNS + " FROM ai_event_index" + filters.where()
                    + " AND embedding IS NOT NULL"
                    + " ORDER BY embedding <=> CAST(:vector AS vector) LIMIT :limit";
            MapSqlParameterSource vectorParams = new MapSqlParameterSource(filters.params().getValues())
                    .addValue("vector", toVectorLiteral(queryVector))
                    .addValue("limit", fetchLimit);
            vectorResults = namedJdbc.query(vectorSql, vectorParams, this::mapRow);
        }

        if (hasText(intent.keywords())) {
            String textSql = "SELECT " + COLUMNS + " FROM ai_event_index" + filters.where()
                    + " AND search_vector @@ plainto_tsquery('simple', :query)"
                    + " ORDER BY ts_rank_cd(search_vector, plainto_tsquery('simple', :query)) DESC LIMIT :limit";
            MapSqlParameterSource textParams = new MapSqlParameterSource(filters.params().getValues())
                    .addValue("query", intent.keywords().trim())
                    .addValue("limit", fetchLimit);
            textResults = namedJdbc.query(textSql, textParams, this::mapRow);
        }

        SearchDiagnostics diagnostics = diagnostics(intent, filters, queryVector,
                semanticRequested, embeddingError, textResults.size());
        List<CatalogEvent> fused = reciprocalRankFusion(vectorResults, textResults, limit);
        return new SearchResult(fused, diagnostics);
    }

    private SearchDiagnostics diagnostics(QueryIntent intent, Filters filters, float[] queryVector,
                                          boolean semanticRequested, String embeddingError,
                                          int keywordHits) {
        Long indexed = jdbcTemplate.queryForObject("SELECT count(*) FROM ai_event_index", Long.class);
        Long matching = namedJdbc.queryForObject("SELECT count(*) FROM ai_event_index" + filters.where(),
                filters.params(), Long.class);

        Long withEmbeddings = 0L;
        if (queryVector != null && queryVector.length > 0) {
            withEmbeddings = namedJdbc.queryForObject(
                    "SELECT count(*) FROM ai_event_index" + filters.where() + " AND embedding IS NOT NULL",
                    filters.params(), Long.class);
        }

        return new SearchDiagnostics(intent,
                semanticRequested,
                embeddingError,
                indexed == null ? 0 : indexed.intValue(),
                matching == null ? 0 : matching.intValue(),
                withEmbeddings == null ? 0 : withEmbeddings.intValue(),
                keywordHits);
    }

    private List<CatalogEvent> reciprocalRankFusion(List<CatalogEvent> vectorResults,
                                                    List<CatalogEvent> textResults,
                                                    int limit) {
        double vectorWeight = embeddingProperties.vectorWeight();
        double textWeight = embeddingProperties.textWeight();

        Map<UUID, Double> scores = new HashMap<>();
        Map<UUID, CatalogEvent> byId = new HashMap<>();

        for (int rank = 0; rank < vectorResults.size(); rank++) {
            CatalogEvent event = vectorResults.get(rank);
            byId.put(event.id(), event);
            scores.merge(event.id(), vectorWeight / (60 + rank + 1), Double::sum);
        }
        for (int rank = 0; rank < textResults.size(); rank++) {
            CatalogEvent event = textResults.get(rank);
            byId.put(event.id(), event);
            scores.merge(event.id(), textWeight / (60 + rank + 1), Double::sum);
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> byId.get(entry.getKey()))
                .toList();
    }

    private Filters filters(QueryIntent intent) {
        StringBuilder where = new StringBuilder(" WHERE (event_date IS NULL OR event_date >= :now)");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("now", LocalDateTime.now());

        if (hasText(intent.countryCode())) {
            where.append(" AND country_code = :country");
            params.addValue("country", intent.countryCode().trim().toUpperCase(Locale.ROOT));
        }
        if (hasText(intent.city())) {
            where.append(" AND lower(city) LIKE lower(:city) || '%'");
            params.addValue("city", intent.city().trim());
        }
        if (intent.dateFrom() != null) {
            where.append(" AND event_date >= :dateFrom");
            params.addValue("dateFrom", intent.dateFrom().atStartOfDay());
        }
        if (intent.dateTo() != null) {
            where.append(" AND event_date < :dateTo");
            params.addValue("dateTo", intent.dateTo().plusDays(1).atStartOfDay());
        }
        List<String> genres = intent.genres().stream()
                .filter(this::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT).replace(",", ""))
                .distinct()
                .toList();
        if (!genres.isEmpty()) {
            where.append(" AND genres && string_to_array(:genres, ',')");
            params.addValue("genres", String.join(",", genres));
        }
        return new Filters(where.toString(), params);
    }

    private CatalogEvent mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        UUID id = rs.getObject("event_id", UUID.class);
        java.sql.Timestamp eventDate = rs.getTimestamp("event_date");
        double price = rs.getDouble("price_from");
        Double priceFrom = rs.wasNull() ? null : price;

        Array genresArray = rs.getArray("genres");
        List<String> genres = List.of();
        if (genresArray != null) {
            Object[] values = (Object[]) genresArray.getArray();
            genres = java.util.Arrays.stream(values).map(String::valueOf).toList();
        }

        return new CatalogEvent(
                id,
                rs.getString("name"),
                rs.getString("artist"),
                rs.getString("location"),
                eventDate == null ? null : eventDate.toLocalDateTime(),
                priceFrom,
                rs.getString("country_code"),
                rs.getString("city"),
                rs.getString("venue_name"),
                genres,
                rs.getString("description"),
                rs.getString("image_url"),
                rs.getString("source_url"),
                List.of());
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                value.append(',');
            }
            value.append(vector[i]);
        }
        return value.append(']').toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Filters(String where, MapSqlParameterSource params) {
    }
}
