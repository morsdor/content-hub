package com.contenthub.media.adapter.out.persistence;

import com.contenthub.media.application.port.out.MediaPersistencePort;
import com.contenthub.media.domain.model.MediaAsset;
import com.contenthub.media.domain.model.MediaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID>, MediaPersistencePort {

    List<MediaAsset> findByWorkspaceIdAndStatus(UUID workspaceId, MediaStatus status);

    java.util.Optional<MediaAsset> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
