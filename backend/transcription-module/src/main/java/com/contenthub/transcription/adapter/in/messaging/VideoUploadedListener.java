package com.contenthub.transcription.adapter.in.messaging;

import com.contenthub.shared.event.VideoUploadedEvent;
import com.contenthub.transcription.application.port.in.TranscribeMediaUseCase;
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
public class VideoUploadedListener {

    private final TranscribeMediaUseCase transcribeMediaUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${contenthub.topics.video-uploaded:" + VideoUploadedEvent.TOPIC + "}",
            groupId = "${spring.kafka.consumer.group-id:contenthub-monolith}"
    )
    public void onVideoUploaded(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Received {} at offset {}", topic, offset);
        try {
            VideoUploadedEvent event = objectMapper.readValue(payload, VideoUploadedEvent.class);
            transcribeMediaUseCase.transcribe(
                    event.getMediaId(),
                    event.getWorkspaceId(),
                    event.getS3Key(),
                    event.getS3Bucket(),
                    event.getTraceId()
            );
        } catch (Exception e) {
            log.error("Failed to process video.uploaded event at offset {}: {}", offset, e.getMessage(), e);
            throw new RuntimeException("video.uploaded processing failed", e);
        }
    }
}
