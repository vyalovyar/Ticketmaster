package com.ticket.event.infrastructure.ticketmaster;

import com.ticket.event.application.EventService;
import com.ticket.event.application.ImportedEventData;
import com.ticket.event.application.TicketmasterImportService;
import com.ticket.event.domain.Event;
import com.ticket.event.domain.Seat;
import com.ticket.event.infrastructure.EventRepository;
import com.ticket.event.infrastructure.ticketmaster.dto.TicketmasterEvent;
import com.ticket.event.infrastructure.ticketmaster.dto.TicketmasterSearchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketmasterImportServiceTest {

    @Mock
    private TicketmasterClient ticketmasterClient;

    @Mock
    private EventService eventService;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private TicketmasterImportService importService;

    @Test
    void importsFutureEventAndGeneratesSeats() {
        LocalDate date = LocalDate.now().plusDays(10);
        TicketmasterEvent event = event("tm-1", date, "19:30:00", 50.0, 120.0);

        when(ticketmasterClient.searchEvents("rock", "DE", null, null, null, 20, 0)).thenReturn(response(event));
        when(eventRepository.findByExternalId("tm-1")).thenReturn(Optional.empty());
        UUID eventId = UUID.randomUUID();
        when(eventService.createImportedEvent(any())).thenReturn(eventId);

        TicketmasterImportResult result = importService.importEvents("rock", "DE", null, null, null, 20, 1);

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skippedExisting()).isZero();
        assertThat(result.skippedInvalid()).isZero();
        assertThat(result.createdEventIds()).containsExactly(eventId);

        ArgumentCaptor<ImportedEventData> captor = ArgumentCaptor.forClass(ImportedEventData.class);
        verify(eventService).createImportedEvent(captor.capture());
        ImportedEventData data = captor.getValue();

        assertThat(data.name()).isEqualTo("Concert");
        assertThat(data.artist()).isEqualTo("The Band");
        assertThat(data.location()).isEqualTo("Mercedes-Benz Arena, Berlin");
        LocalDateTime expectedDate = ZonedDateTime.of(date.atTime(19, 30), ZoneId.of("Europe/Berlin"))
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
        assertThat(data.eventDate()).isEqualTo(expectedDate);
        assertThat(data.externalId()).isEqualTo("tm-1");
        assertThat(data.description()).isEqualTo("Great show tonight");
        assertThat(data.imageUrl()).isEqualTo("https://images.example/large.jpg");
        assertThat(data.sourceUrl()).isEqualTo("https://www.ticketmaster.com/event/tm-1");
        assertThat(data.countryCode()).isEqualTo("DE");
        assertThat(data.city()).isEqualTo("Berlin");
        assertThat(data.venueName()).isEqualTo("Mercedes-Benz Arena");
        assertThat(data.genres()).contains("music", "rock", "alternative");

        List<Seat> seats = data.seats();
        assertThat(seats).hasSize(80);
        assertThat(seats.get(0)).isEqualTo(new Seat("A", 1, 50.0));
        assertThat(seats.get(79)).isEqualTo(new Seat("H", 10, 120.0));
    }

    @Test
    void skipsEventsThatWereAlreadyImported() {
        TicketmasterEvent event = event("tm-2", LocalDate.now().plusDays(5), "20:00:00", 40.0, 90.0);

        when(ticketmasterClient.searchEvents(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(response(event));
        when(eventRepository.findByExternalId("tm-2")).thenReturn(Optional.of(org.mockito.Mockito.mock(Event.class)));

        TicketmasterImportResult result = importService.importEvents(null, null, null, null, null, 20, 1);

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.imported()).isZero();
        assertThat(result.skippedExisting()).isEqualTo(1);
        verify(eventService, never()).createImportedEvent(any());
    }

    @Test
    void skipsEventsWithoutConfirmedDate() {
        LocalDate date = LocalDate.now().plusDays(5);
        TicketmasterEvent event = new TicketmasterEvent(
                "tm-3",
                "TBA Concert",
                null,
                null,
                null,
                null,
                new TicketmasterEvent.Dates(
                        new TicketmasterEvent.Start(date.toString(), null, null, false, false, true, false),
                        "Europe/Berlin"),
                null,
                null,
                null,
                new TicketmasterEvent.Embedded(
                        List.of(new TicketmasterEvent.Venue("Arena", new TicketmasterEvent.City("Berlin"), null)),
                        List.of(new TicketmasterEvent.Attraction("Artist"))));

        when(ticketmasterClient.searchEvents(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(response(event));

        TicketmasterImportResult result = importService.importEvents(null, "DE", null, null, null, 20, 1);

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.imported()).isZero();
        assertThat(result.skippedInvalid()).isEqualTo(1);
        verify(eventService, never()).createImportedEvent(any());
    }

    @Test
    void doesNotRequestNextPageWhenCurrentPageIsNotFull() {
        TicketmasterEvent event = event("tm-4", LocalDate.now().plusDays(5), "20:00:00", 40.0, 90.0);

        when(ticketmasterClient.searchEvents(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(response(event));
        when(eventRepository.findByExternalId("tm-4")).thenReturn(Optional.of(org.mockito.Mockito.mock(Event.class)));

        importService.importEvents(null, null, null, null, null, 20, 3);

        verify(ticketmasterClient).searchEvents(null, null, null, null, null, 20, 0);
        verify(ticketmasterClient, never()).searchEvents(any(), any(), any(), any(), any(), anyInt(), eq(1));
    }

    private TicketmasterSearchResponse response(TicketmasterEvent event) {
        return new TicketmasterSearchResponse(
                new TicketmasterSearchResponse.Embedded(List.of(event)),
                new TicketmasterSearchResponse.Page(20, 1, 1, 0));
    }

    private TicketmasterEvent event(String id, LocalDate date, String time, double minPrice, double maxPrice) {
        return new TicketmasterEvent(
                id,
                "Concert",
                "https://www.ticketmaster.com/event/" + id,
                "<p>Great <strong>show</strong> tonight</p>",
                null,
                null,
                new TicketmasterEvent.Dates(
                        new TicketmasterEvent.Start(date.toString(), time, null, false, false, false, false),
                        "Europe/Berlin"),
                List.of(new TicketmasterEvent.PriceRange(minPrice, maxPrice, "EUR")),
                List.of(
                        new TicketmasterEvent.Image("16_9", "https://images.example/small.jpg", 205, 115, false),
                        new TicketmasterEvent.Image("16_9", "https://images.example/large.jpg", 1024, 576, false)),
                List.of(new TicketmasterEvent.Classification(
                        new TicketmasterEvent.Segment("Music"),
                        new TicketmasterEvent.Genre("Rock"),
                        new TicketmasterEvent.SubGenre("Alternative"),
                        true)),
                new TicketmasterEvent.Embedded(
                        List.of(new TicketmasterEvent.Venue(
                                "Mercedes-Benz Arena",
                                new TicketmasterEvent.City("Berlin"),
                                new TicketmasterEvent.Country("Germany", "DE"))),
                        List.of(new TicketmasterEvent.Attraction("The Band"))));
    }
}
