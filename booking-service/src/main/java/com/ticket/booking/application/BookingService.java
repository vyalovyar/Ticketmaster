package com.ticket.booking.application;

import com.ticket.booking.domain.Booking;
import com.ticket.booking.domain.BookingStatus;
import com.ticket.booking.domain.EventSeat;
import com.ticket.booking.domain.SeatStatus;
import com.ticket.booking.infrastructure.BookingProducer;
import com.ticket.booking.infrastructure.BookingRepository;
import com.ticket.booking.infrastructure.EventSeatRepository;
import com.ticket.booking.infrastructure.RefundProducer;
import com.ticket.booking.infrastructure.TicketReturnProducer;
import com.ticket.dto.BookingCreatedEvent;
import com.ticket.dto.RefundRequestedEvent;
import com.ticket.dto.TicketReturnedEvent;
import com.ticket.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EventSeatRepository eventSeatRepository;
    private final RedisLockService redisLockService;
    private final BookingProducer bookingProducer;
    private final TicketReturnProducer ticketReturnProducer;
    private final RefundProducer refundProducer;

    public BookingService(BookingRepository bookingRepository,
                          EventSeatRepository eventSeatRepository,
                          RedisLockService redisLockService,
                          BookingProducer bookingProducer,
                          TicketReturnProducer ticketReturnProducer,
                          RefundProducer refundProducer) {
        this.bookingRepository = bookingRepository;
        this.eventSeatRepository = eventSeatRepository;
        this.redisLockService = redisLockService;
        this.bookingProducer = bookingProducer;
        this.ticketReturnProducer = ticketReturnProducer;
        this.refundProducer = refundProducer;
    }

    public Optional<Booking> findById(UUID bookingId) {
        return bookingRepository.findById(bookingId);
    }

    @Transactional
    public UUID createBooking(UUID eventId, String userId, String seatRow, Integer seatNumber) {
        String lockKey = "lock:booking:" + eventId + ":" + seatRow + ":" + seatNumber;
        String lockValue = UUID.randomUUID().toString();

        if (!redisLockService.tryLock(lockKey, lockValue, Duration.ofMinutes(15))) {
            throw new BusinessException("Это место уже резервируется другим пользователем", HttpStatus.CONFLICT, "место уже резервируется");
        }
        releaseLockAfterCompletion(lockKey, lockValue);

        EventSeat seat = eventSeatRepository.findByEventIdAndSeatRowAndSeatNumber(eventId, seatRow, seatNumber)
                .orElseThrow(() -> new BusinessException("Место не найдено", HttpStatus.NOT_FOUND, "место не найдено"));

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new BusinessException("Место уже забронировано или продано", HttpStatus.CONFLICT, "место уже забронировано или продано");
        }

        seat.markAsReserved();
        eventSeatRepository.save(seat);

        Booking booking = Booking.create(eventId, userId, seatRow, seatNumber, seat.getPrice());
        Booking savedBooking = bookingRepository.save(booking);

        publishAfterCommit(new BookingCreatedEvent(savedBooking.getId(), eventId, userId, seat.getPrice()));

        return savedBooking.getId();
    }

    @Transactional
    public Booking returnTicket(UUID eventId, String userId, String seatRow, Integer seatNumber) {
        Booking booking = bookingRepository.findByEventIdAndUserIdAndSeatRowAndSeatNumber(eventId, userId, seatRow, seatNumber)
                .orElseThrow(() -> new BusinessException("Билет не найден для этого пользователя", HttpStatus.NOT_FOUND, "билет не найден"));

        if (booking.getStatus() != BookingStatus.PAID && booking.getStatus() != BookingStatus.RESERVED) {
            throw new BusinessException("Этот билет нельзя вернуть", HttpStatus.CONFLICT, "билет нельзя вернуть");
        }

        boolean wasPaid = booking.getStatus() == BookingStatus.PAID;
        Double refundAmount = booking.getPrice();

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        EventSeat seat = eventSeatRepository.findByEventIdAndSeatRowAndSeatNumber(eventId, seatRow, seatNumber)
                .orElseThrow(() -> new BusinessException("Место не найдено", HttpStatus.NOT_FOUND, "место не найдено"));

        seat.markAsAvailable();
        eventSeatRepository.save(seat);

        publishAfterCommit(new TicketReturnedEvent(eventId, userId, seatRow, seatNumber));

        if (wasPaid) {
            publishAfterCommit(new RefundRequestedEvent(booking.getId(), eventId, userId, refundAmount));
        }

        return booking;
    }

    private void releaseLockAfterCompletion(String lockKey, String lockValue) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                redisLockService.releaseLock(lockKey, lockValue);
            }
        });
    }

    private void publishAfterCommit(BookingCreatedEvent event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                bookingProducer.publishBookingCreated(event);
            }
        });
    }

    private void publishAfterCommit(TicketReturnedEvent event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ticketReturnProducer.publish(event);
            }
        });
    }

    private void publishAfterCommit(RefundRequestedEvent event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refundProducer.publish(event);
            }
        });
    }
}
