package com.ticket.booking.infrastructure;

import com.ticket.booking.domain.EventSeat;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventSeatRepository extends JpaRepository<EventSeat, UUID> {

    List<EventSeat> findAllByEventId(UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"))
    Optional<EventSeat> findByEventIdAndSeatRowAndSeatNumber(UUID eventId, String seatRow, Integer seatNumber);
}