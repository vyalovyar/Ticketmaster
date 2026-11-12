package com.ticket.event.application;

import com.ticket.event.infrastructure.ticketmaster.TicketmasterAutoImportProperties;
import com.ticket.event.infrastructure.ticketmaster.TicketmasterImportResult;
import com.ticket.event.infrastructure.ticketmaster.TicketmasterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TicketmasterAutoImportService {

    private static final Logger log = LoggerFactory.getLogger(TicketmasterAutoImportService.class);

    private final TicketmasterImportService importService;
    private final TicketmasterProperties ticketmasterProperties;
    private final TicketmasterAutoImportProperties autoImportProperties;

    public TicketmasterAutoImportService(
            TicketmasterImportService importService,
            TicketmasterProperties ticketmasterProperties,
            TicketmasterAutoImportProperties autoImportProperties) {
        this.importService = importService;
        this.ticketmasterProperties = ticketmasterProperties;
        this.autoImportProperties = autoImportProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void importOnStartup() {
        if (!autoImportProperties.startupImport()) {
            return;
        }
        runImport("startup");
    }

    @Scheduled(cron = "${app.ticketmaster.auto-import.cron:0 0 3 * * *}")
    public void importScheduled() {
        runImport("scheduled");
    }

    private void runImport(String trigger) {
        if (!autoImportProperties.enabled()) {
            return;
        }
        if (!ticketmasterProperties.isConfigured()) {
            log.warn("Ticketmaster auto-import ({}) skipped: TICKETMASTER_API_KEY is not set", trigger);
            return;
        }

        try {
            TicketmasterImportResult result = importService.importEvents(
                    autoImportProperties.keyword(),
                    autoImportProperties.countryCode(),
                    autoImportProperties.city(),
                    autoImportProperties.classificationName(),
                    autoImportProperties.sort(),
                    autoImportProperties.size(),
                    autoImportProperties.pages());
            log.info("Ticketmaster auto-import ({}) finished: fetched={} imported={} existing={} invalid={}",
                    trigger, result.fetched(), result.imported(), result.skippedExisting(), result.skippedInvalid());
        } catch (RuntimeException e) {
            log.warn("Ticketmaster auto-import ({}) failed: {}", trigger, e.getMessage());
        }
    }
}
