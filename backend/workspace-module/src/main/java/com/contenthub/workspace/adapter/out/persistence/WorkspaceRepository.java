package com.contenthub.workspace.adapter.out.persistence;

import com.contenthub.workspace.application.port.out.WorkspacePersistencePort;
import com.contenthub.workspace.domain.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID>, WorkspacePersistencePort {

    List<Workspace> findByCreatedByAndDeletedAtIsNull(UUID createdBy);
}
