package com.ticket.infrastructure;

import com.ticket.dto.RefundRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RefundProcessor {

    private static final Logger log = LoggerFactory.getLogger(RefundProcessor.class);

    @KafkaListener(
            topics = "ticketmaster.refunds",
            groupId = "payment-service-group",
            containerFactory = "refundListenerContainerFactory")
    public void processRefund(RefundRequestedEvent event) {
        String refundId = "RFD-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Возврат средств выполнен. Бронь: {}, Сумма: {}, Refund: {}",
                event.bookingId(), event.amount(), refundId);
    }
}
