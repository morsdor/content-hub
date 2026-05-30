package com.contenthub.workspace.application.port.out;

import com.contenthub.workspace.domain.model.Workspace;

import java.util.Optional;
import java.util.UUID;

public interface WorkspacePersistencePort {

    Workspace save(Workspace workspace);

    Optional<Workspace> findById(UUID id);
}
