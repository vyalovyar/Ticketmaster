package com.ticket.event;

import com.ticket.event.infrastructure.ticketmaster.TicketmasterAutoImportProperties;
import com.ticket.event.infrastructure.ticketmaster.TicketmasterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({TicketmasterProperties.class, TicketmasterAutoImportProperties.class})
public class EventServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventServiceApplication.class, args);
    }
}
