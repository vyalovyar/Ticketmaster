package com.ticket.event.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="events")
public class Event {

    @Id
    private UUID id;

    private String name;

    private String artist;

    private String location;

    private LocalDateTime eventDate;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "external_id", unique = true, length = 64)
    private String externalId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(length = 120)
    private String city;

    @Column(name = "venue_name", length = 200)
    private String venueName;

    @ElementCollection
    @CollectionTable(name = "event_genres", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "genre", length = 80)
    private List<String> genres = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name= "event_seats", joinColumns = @JoinColumn(name = "event_id"))
    private List<Seat> seats;

    protected Event() {}

    public static Event create(String name, String location, LocalDateTime eventDate, List<Seat> seats) {
        return create(name, null, location, eventDate, seats, null);
    }

    public static Event create(String name, String artist, String location, LocalDateTime eventDate, List<Seat> seats) {
        return create(name, artist, location, eventDate, seats, null);
    }

    public static Event create(String name, String artist, String location, LocalDateTime eventDate,
                               List<Seat> seats, String externalId) {
        if (eventDate.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Нельзя создать мероприятие в прошлом");
        }
        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("У мероприятия должны быть места");
        }

        Event event = new Event();
        event.id = UUID.randomUUID();
        event.name = name;
        event.artist = artist != null ? artist : name;
        event.location = location;
        event.eventDate = eventDate;
        event.seats = seats;
        event.externalId = externalId;
        return event;
    }

    public static Event createImported(String name, String artist, String location, LocalDateTime eventDate,
                                       String description, String imageUrl, String sourceUrl,
                                       String countryCode, String city, String venueName, List<String> genres,
                                       List<Seat> seats, String externalId) {
        Event event = create(name, artist, location, eventDate, seats, externalId);
        event.description = description;
        event.imageUrl = imageUrl;
        event.sourceUrl = sourceUrl;
        event.countryCode = countryCode;
        event.city = city;
        event.venueName = venueName;
        event.genres = genres == null ? new ArrayList<>() : new ArrayList<>(genres);
        return event;
    }

    public void updateImportedMetadata(String artist, String location, String description, String imageUrl,
                                       String sourceUrl, String countryCode, String city, String venueName,
                                       List<String> genres) {
        this.artist = artist;
        this.location = location;
        this.description = description;
        this.imageUrl = imageUrl;
        this.sourceUrl = sourceUrl;
        this.countryCode = countryCode;
        this.city = city;
        this.venueName = venueName;
        this.genres.clear();
        if (genres != null) {
            this.genres.addAll(genres);
        }
    }

    public void delete(){
        if(this.deletedAt != null){
            throw new IllegalStateException("Уже удалено");
        }
        if(this.eventDate.isBefore(LocalDateTime.now())){
            throw new IllegalStateException("Нельзя удалить прошедшее мероприятие");
        }
        this.deletedAt = LocalDateTime.now();
    }

    public void updateDetails(String name, String artist, String location, LocalDateTime eventDate) {
        if (eventDate.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Нельзя перенести мероприятие в прошлое");
        }
        this.name = name;
        this.artist = artist;
        this.location = location;
        this.eventDate = eventDate;
    }

    public UUID getId() {
        return id;
    }

    public String getName(){
        return name;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getLocation() {
        return location;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public List<Seat> getSeats(){
        return seats;
    }

    public boolean isDeleted(){
        return deletedAt != null;
    }
    public LocalDateTime getDeletedAt(){
        return deletedAt;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getCity() {
        return city;
    }

    public String getVenueName() {
        return venueName;
    }

    public List<String> getGenres() {
        return genres;
    }

}
