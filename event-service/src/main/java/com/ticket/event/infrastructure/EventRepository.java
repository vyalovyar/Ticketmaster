package com.ticket.event.infrastructure;

import com.ticket.event.domain.Event;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    @Query("select e from Event e where e.deletedAt is null")
    List<Event> findAllActive();

    Optional<Event> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    boolean existsByExternalId(String externalId);

    Optional<Event> findByExternalId(String externalId);

    @Query("select e from Event e where e.deletedAt is null and e.externalId is not null and e.countryCode is null order by e.eventDate asc")
    List<Event> findMissingMetadata(Pageable pageable);

    long countByDeletedAtIsNullAndExternalIdIsNotNullAndCountryCodeIsNull();
}
