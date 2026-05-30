package com.contenthub.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEntry {

	@Id
	@Column(columnDefinition = "uuid", updatable = false, nullable = false)
	@Builder.Default
	private UUID id = UUID.randomUUID();

	@Column(name = "aggregate_type", nullable = false)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false, columnDefinition = "uuid")
	private UUID aggregateId;

	// Logical event type name, e.g. "VideoUploaded". Distinct from the Kafka topic.
	@Column(name = "event_type", nullable = false)
	private String eventType;

	// Kafka topic to publish to, e.g. "video.uploaded".
	@Column(nullable = false)
	private String topic;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb", nullable = false)
	private String payload;

	@Column(name = "trace_id", nullable = false)
	private String traceId;

	@Column(name = "created_at", nullable = false, updatable = false)
	@Builder.Default
	private Instant createdAt = Instant.now();

	@Column(name = "sent_at")
	private Instant sentAt;

	public static OutboxEntry of(String aggregateType, UUID aggregateId, String eventType, String topic, String payload,
			String traceId) {
		return OutboxEntry.builder().aggregateType(aggregateType).aggregateId(aggregateId).eventType(eventType)
				.topic(topic).payload(payload).traceId(traceId).build();
	}
}
