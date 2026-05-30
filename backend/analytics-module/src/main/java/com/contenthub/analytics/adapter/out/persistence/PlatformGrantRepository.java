package com.contenthub.analytics.adapter.out.persistence;

import com.contenthub.analytics.domain.model.PlatformGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformGrantRepository extends JpaRepository<PlatformGrant, UUID> {

	List<PlatformGrant> findByWorkspaceId(UUID workspaceId);

	Optional<PlatformGrant> findByWorkspaceIdAndPlatform(UUID workspaceId, String platform);
}
