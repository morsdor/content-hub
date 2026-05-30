package com.contenthub.media.application.service;

import com.contenthub.media.application.port.in.ConfirmUploadUseCase;
import com.contenthub.media.application.port.in.RequestPresignedUploadUseCase;
import com.contenthub.media.application.port.out.MediaPersistencePort;
import com.contenthub.media.application.port.out.MediaStoragePort;
import com.contenthub.media.domain.model.MediaAsset;
import com.contenthub.media.domain.model.MediaStatus;
import com.contenthub.shared.event.VideoUploadedEvent;
import com.contenthub.shared.outbox.OutboxEntry;
import com.contenthub.shared.outbox.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService implements RequestPresignedUploadUseCase, ConfirmUploadUseCase {

    private final MediaPersistencePort mediaPersistencePort;
    private final MediaStoragePort mediaStoragePort;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PresignedUpload requestUpload(UUID workspaceId, UUID cardId,
                                         String filename, String contentType, long sizeBytes) {
        String bucket = mediaStoragePort.resolveMediaBucket();
        String key = workspaceId + "/" + UUID.randomUUID() + "/" + filename;

        MediaAsset asset = MediaAsset.builder()
                .workspaceId(workspaceId)
                .cardId(cardId)
                .s3Key(key)
                .s3Bucket(bucket)
                .contentType(contentType)
                .sizeBytes(sizeBytes)
                .status(MediaStatus.UPLOADING)
                .build();

        MediaAsset saved = mediaPersistencePort.save(asset);
        URL presignedUrl = mediaStoragePort.generatePresignedPutUrl(bucket, key, contentType, sizeBytes);

        return new PresignedUpload(saved.getId(), presignedUrl, key);
    }

    @Override
    @Transactional
    public void confirmUpload(UUID mediaId, UUID callerUserId, String traceId) {
        MediaAsset asset = mediaPersistencePort.findById(mediaId)
                .orElseThrow(() -> new NoSuchElementException("Media asset not found: " + mediaId));

        // Phase 1: verify callerUserId is a member of asset.getWorkspaceId() via a
        // WorkspaceMembershipPort outbound port (cross-module query over API, not DB).
        // Skipped in Phase 0 — all authenticated users can currently confirm uploads.

        asset.setStatus(MediaStatus.UPLOADED);
        asset.setUpdatedAt(Instant.now());
        mediaPersistencePort.save(asset);

        VideoUploadedEvent event = VideoUploadedEvent.builder()
                .mediaId(mediaId)
                .workspaceId(asset.getWorkspaceId())
                .cardId(asset.getCardId())
                .s3Key(asset.getS3Key())
                .s3Bucket(asset.getS3Bucket())
                .traceId(traceId)
                .build();

        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(OutboxEntry.of(
                    "MediaAsset", mediaId,
                    VideoUploadedEvent.EVENT_TYPE, VideoUploadedEvent.TOPIC,
                    payload, traceId));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize VideoUploadedEvent for mediaId=" + mediaId, e);
        }
    }
}
