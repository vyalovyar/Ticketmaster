package com.ticket.event.infrastructure;

import com.ticket.event.domain.Event;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    @Query("""
            select distinct e from Event e
            left join fetch e.seats
            where e.deletedAt is null
              and e.eventDate >= :from
              and (:artist is null or lower(e.artist) like lower(concat('%', :artist, '%')))
              and (:location is null or lower(e.location) like lower(concat('%', :location, '%')))
              and (:dateFrom is null or e.eventDate >= :dateFrom)
              and (:dateTo is null or e.eventDate < :dateTo)
            order by e.eventDate asc
            """)
    List<Event> findUpcomingFiltered(
            @Param("from") LocalDateTime from,
            @Param("artist") String artist,
            @Param("location") String location,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo);

    Optional<Event> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    boolean existsByExternalId(String externalId);

    Optional<Event> findByExternalId(String externalId);

    @Query("select e from Event e where e.deletedAt is null and e.externalId is not null and e.countryCode is null order by e.eventDate asc")
    List<Event> findMissingMetadata(Pageable pageable);

    long countByDeletedAtIsNullAndExternalIdIsNotNullAndCountryCodeIsNull();
}
