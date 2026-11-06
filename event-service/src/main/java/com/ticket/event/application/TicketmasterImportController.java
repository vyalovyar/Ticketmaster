package com.ticket.event.application;

import com.ticket.event.infrastructure.ticketmaster.TicketmasterImportResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/import")
public class TicketmasterImportController {

    private final TicketmasterImportService ticketmasterImportService;

    public TicketmasterImportController(TicketmasterImportService ticketmasterImportService) {
        this.ticketmasterImportService = ticketmasterImportService;
    }

    @PostMapping("/ticketmaster")
    public TicketmasterImportResult importFromTicketmaster(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String classificationName,
            @RequestParam(defaultValue = "random") String sort,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") int pages) {
        return ticketmasterImportService.importEvents(
                keyword, countryCode, city, classificationName, sort, size, pages);
    }
}
