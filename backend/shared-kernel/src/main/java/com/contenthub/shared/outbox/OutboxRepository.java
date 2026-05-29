package com.contenthub.shared.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    // Used by the relay — FOR UPDATE SKIP LOCKED ensures multiple app instances
    // each claim a disjoint set of rows rather than racing over the same ones.
    // Must be called inside an active transaction so the lock is held until markSent commits.
    @Query(value = "SELECT * FROM outbox WHERE sent_at IS NULL ORDER BY created_at ASC LIMIT 50 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEntry> findUnsentForUpdate();

    @Modifying
    @Query("UPDATE OutboxEntry e SET e.sentAt = :sentAt WHERE e.id = :id")
    void markSent(@Param("id") UUID id, @Param("sentAt") Instant sentAt);
}
