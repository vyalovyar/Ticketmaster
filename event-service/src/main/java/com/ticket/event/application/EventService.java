package com.ticket.event.application;

import com.ticket.dto.EventDeletedEvent;
import com.ticket.dto.EventUpdatedEvent;
import com.ticket.dto.SeatInfo;
import com.ticket.dto.CreatedEvent;
import com.ticket.event.domain.Event;
import com.ticket.event.domain.Seat;

import com.ticket.event.infrastructure.EventProducer;
import com.ticket.event.infrastructure.EventRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class EventService {
    private final EventRepository eventRepository;
    private final EventProducer kafkaProducer;

    public EventService(EventRepository eventRepository, EventProducer kafkaProducer) {
        this.eventRepository = eventRepository;
        this.kafkaProducer = kafkaProducer;
    }

    @Transactional
    public UUID createEvent(String name, String artist, String location, LocalDateTime eventDate, List<Seat> seats){
        return createEvent(name, artist, location, eventDate, seats, null);
    }

    @Transactional
    public UUID createEvent(String name, String artist, String location, LocalDateTime eventDate,
                            List<Seat> seats, String externalId) {
        Event event = Event.create(name, artist, location, eventDate, seats, externalId);
        return saveAndPublish(event);
    }

    @Transactional
    public UUID createImportedEvent(ImportedEventData data) {
        Event event = Event.createImported(
                data.name(),
                data.artist(),
                data.location(),
                data.eventDate(),
                data.description(),
                data.imageUrl(),
                data.sourceUrl(),
                data.countryCode(),
                data.city(),
                data.venueName(),
                data.genres(),
                data.seats(),
                data.externalId());
        return saveAndPublish(event);
    }

    @Transactional
    public void updateImportedMetadata(ImportedEventData data) {
        Event event = eventRepository.findByExternalId(data.externalId())
                .orElseThrow(() -> new EntityNotFoundException("Мероприятие не найдено: " + data.externalId()));
        if (event.isDeleted()) {
            return;
        }
        event.updateImportedMetadata(
                data.artist(),
                data.location(),
                data.description(),
                data.imageUrl(),
                data.sourceUrl(),
                data.countryCode(),
                data.city(),
                data.venueName(),
                data.genres());
        kafkaProducer.publishEventUpdated(new EventUpdatedEvent(event.getId(), LocalDateTime.now()));
    }

    @Transactional
    public UUID backfillMetadata(UUID eventId, TicketmasterMetadata metadata) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Мероприятие не найдено: " + eventId));

        event.updateImportedMetadata(
                metadata.artist() != null ? metadata.artist() : event.getArtist(),
                metadata.location() != null ? metadata.location() : event.getLocation(),
                metadata.description() != null ? metadata.description() : event.getDescription(),
                metadata.imageUrl() != null ? metadata.imageUrl() : event.getImageUrl(),
                metadata.sourceUrl() != null ? metadata.sourceUrl() : event.getSourceUrl(),
                metadata.countryCode(),
                metadata.city(),
                metadata.venueName(),
                metadata.genres());

        kafkaProducer.publishEventUpdated(new EventUpdatedEvent(event.getId(), LocalDateTime.now()));
        return event.getId();
    }

    @Transactional
    public Event findById(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Мероприятие не найдено: " + eventId));
    }

    private UUID saveAndPublish(Event event) {
        Event saveEvent = eventRepository.save(event);

        List<SeatInfo> seatInfos = saveEvent.getSeats().stream().map(seat -> new SeatInfo(seat.row(), seat.seatNumber(), seat.price())).toList();

        CreatedEvent domainEvent = new CreatedEvent(saveEvent.getId(), saveEvent.getName(), seatInfos, LocalDateTime.now());
        kafkaProducer.publishEventCreated(domainEvent);
        return saveEvent.getId();
    }

    @Transactional
    public UUID updateEvent(UUID eventId, String name, String artist, String location, LocalDateTime eventDate) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Мероприятие не найдено: " + eventId));
        event.updateDetails(name, artist, location, eventDate);
        kafkaProducer.publishEventUpdated(new EventUpdatedEvent(event.getId(), LocalDateTime.now()));
        return event.getId();
    }
//Тут хибер неявно сравнивает сущность и бд и сам генерит запрос(можно явно написать eventRepository.save(event))
    @Transactional
    public UUID deleteEvent(UUID idEvent){
        Event event = eventRepository.findByIdAndDeletedAtIsNull(idEvent)
                .orElseThrow(() -> new EntityNotFoundException("Мероприятие не найдено: " + idEvent));

        event.delete();

        kafkaProducer.publishEventDeleted(new EventDeletedEvent(event.getId(), event.getDeletedAt()));

        return event.getId();
    }
}
