package com.contenthub.workspace.application.port.in;

import com.contenthub.workspace.domain.model.Workspace;

import java.util.List;
import java.util.UUID;

public interface GetWorkspacesUseCase {

	List<Workspace> getWorkspaces(UUID userId);
}
