package com.contenthub.shared.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = false)
public class OutboxRelayService {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:1000}")
    public void relay() {
        List<OutboxEntry> unsent = outboxRepository.findUnsentForUpdate();
        if (unsent.isEmpty()) {
            return;
        }
        log.debug("Relaying {} outbox entries to Kafka", unsent.size());
        unsent.forEach(this::publishEntry);
    }

    private void publishEntry(OutboxEntry entry) {
        try {
            kafkaTemplate
                    .send(entry.getEventType(), entry.getAggregateId().toString(), entry.getPayload())
                    .get(10, TimeUnit.SECONDS);
            outboxRepository.markSent(entry.getId(), Instant.now());
            log.debug("Relayed outbox entry {} → topic {}", entry.getId(), entry.getEventType());
        } catch (Exception e) {
            log.warn("Failed to relay outbox entry {} (will retry): {}", entry.getId(), e.getMessage());
        }
    }
}
