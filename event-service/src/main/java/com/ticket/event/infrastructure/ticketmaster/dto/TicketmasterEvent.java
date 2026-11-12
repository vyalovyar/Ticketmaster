package com.ticket.event.infrastructure.ticketmaster.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketmasterEvent(
        String id,
        String name,
        String url,
        String description,
        String info,
        String pleaseNote,
        Dates dates,
        List<PriceRange> priceRanges,
        List<Image> images,
        List<Classification> classifications,
        @JsonProperty("_embedded") Embedded embedded
) {

    public Optional<Venue> firstVenue() {
        if (embedded == null || embedded.venues() == null || embedded.venues().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(embedded.venues().get(0));
    }

    public Optional<Attraction> firstAttraction() {
        if (embedded == null || embedded.attractions() == null || embedded.attractions().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(embedded.attractions().get(0));
    }

    public Optional<PriceRange> firstPriceRange() {
        if (priceRanges == null || priceRanges.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(priceRanges.get(0));
    }

    public Optional<String> bestImageUrl() {
        if (images == null || images.isEmpty()) {
            return Optional.empty();
        }
        List<Image> candidates = images.stream()
                .filter(image -> image.url() != null && !image.url().isBlank())
                .toList();

        return candidates.stream()
                .filter(image -> image.width() != null && image.width() >= 640)
                .min(java.util.Comparator.comparingInt(Image::width))
                .or(() -> candidates.stream()
                        .max(java.util.Comparator.comparingInt(image -> image.width() == null ? 0 : image.width())))
                .map(Image::url);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dates(Start start, String timezone) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Start(
            String localDate,
            String localTime,
            String dateTime,
            Boolean dateTBA,
            Boolean dateTBD,
            Boolean timeTBA,
            Boolean noSpecificTime
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceRange(Double min, Double max, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(String ratio, String url, Integer width, Integer height, Boolean fallback) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Embedded(List<Venue> venues, List<Attraction> attractions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Classification(Segment segment, Genre genre, SubGenre subGenre, Boolean primary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Segment(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Genre(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubGenre(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Venue(String name, City city, Country country) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record City(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Country(String name, String countryCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attraction(String name) {
    }
}
