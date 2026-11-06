package com.ticket.event.infrastructure.ticketmaster;

import com.ticket.event.application.EventService;
import com.ticket.event.application.ImportedEventData;
import com.ticket.event.application.TicketmasterImportService;
import com.ticket.event.infrastructure.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "TICKETMASTER_API_KEY", matches = ".*\\S.*")
class TicketmasterLiveSmokeTest {

    @Test
    void mapsRealTicketmasterResponse() {
        TicketmasterProperties properties = new TicketmasterProperties(
                System.getenv("TICKETMASTER_API_KEY"), null);

        TicketmasterClient client = new TicketmasterClient(RestClient.builder(), properties);
        EventService eventService = mock(EventService.class);
        EventRepository repository = mock(EventRepository.class);

        when(repository.existsByExternalId(anyString())).thenReturn(false);
        when(eventService.createImportedEvent(any())).thenReturn(UUID.randomUUID());

        TicketmasterImportService service = new TicketmasterImportService(client, eventService, repository);
        TicketmasterImportResult result = service.importEvents("rock", "DE", null, "music", "random", 5, 1);

        assertThat(result.fetched()).isGreaterThan(0);
        assertThat(result.imported()).isEqualTo(result.fetched());

        ArgumentCaptor<ImportedEventData> captor = ArgumentCaptor.forClass(ImportedEventData.class);
        verify(eventService, atLeastOnce()).createImportedEvent(captor.capture());

        List<ImportedEventData> imported = captor.getAllValues();
        assertThat(imported).allSatisfy(event -> {
            assertThat(event.name()).isNotBlank();
            assertThat(event.location()).isNotBlank();
            assertThat(event.eventDate()).isNotNull();
            assertThat(event.externalId()).isNotBlank();
            assertThat(event.seats()).hasSize(80);
        });

        imported.forEach(event -> System.out.printf("TM import: %s | %s | %s | seats=%d%n",
                event.name(), event.location(), event.eventDate(), event.seats().size()));
    }
}
