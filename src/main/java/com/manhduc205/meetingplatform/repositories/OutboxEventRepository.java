package com.manhduc205.meetingplatform.repositories;

import com.manhduc205.meetingplatform.models.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Collection;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {
    @Query(value = "SELECT * FROM outbox_events WHERE " +
            "(status IN ('PENDING', 'RETRY') AND next_retry_at <= :now) OR " +
            "(status = 'PROCESSING' AND locked_until <= :now) " +
            "ORDER BY created_at LIMIT 20 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEventEntity> lockClaimableEvents(@Param("now") Instant now);

    void deleteAllByAggregateIdIn(Collection<String> aggregateIds);
}
