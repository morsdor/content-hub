package com.contenthub.media.application.service;

import com.contenthub.media.adapter.out.persistence.MediaAssetRepository;
import com.contenthub.media.application.port.in.RequestPresignedUploadUseCase;
import com.contenthub.media.application.port.out.MediaStoragePort;
import com.contenthub.media.domain.model.MediaAsset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URL;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService implements RequestPresignedUploadUseCase {

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaStoragePort mediaStoragePort;

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
                .status("uploading")
                .build();

        MediaAsset saved = mediaAssetRepository.save(asset);
        URL presignedUrl = mediaStoragePort.generatePresignedPutUrl(bucket, key, contentType, sizeBytes);

        return new PresignedUpload(saved.getId(), presignedUrl, key);
    }
}
