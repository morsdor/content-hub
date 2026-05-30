package com.contenthub.media.adapter.out.persistence;

import com.contenthub.media.domain.model.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    List<MediaAsset> findByWorkspaceIdAndStatus(UUID workspaceId, String status);

    Optional<MediaAsset> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
