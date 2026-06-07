package com.contenthub.media.adapter.in.messaging;

import com.contenthub.media.application.port.in.MarkMediaReadyUseCase;
import com.contenthub.shared.event.TranscriptionCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TranscriptionCompletedListener {

	private final MarkMediaReadyUseCase markMediaReadyUseCase;
	private final ObjectMapper objectMapper;

	@KafkaListener(topics = "${contenthub.topics.transcription-completed:"
			+ TranscriptionCompletedEvent.TOPIC
			+ "}", groupId = "${spring.kafka.consumer.group-id:contenthub-monolith}")
	public void onTranscriptionCompleted(@Payload String payload,
			@Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
			@Header(KafkaHeaders.OFFSET) long offset) {
		log.info("Received {} at offset {}", topic, offset);
		try {
			TranscriptionCompletedEvent event = objectMapper.readValue(payload, TranscriptionCompletedEvent.class);
			markMediaReadyUseCase.markReady(event.getMediaId(), event.getTranscriptId());
		} catch (Exception e) {
			log.error("Failed to process transcription.completed event at offset {}: {}", offset, e.getMessage(), e);
			throw new RuntimeException("transcription.completed processing failed", e);
		}
	}
}
