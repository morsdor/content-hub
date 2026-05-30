package com.contenthub.workspace.application.port.in;

import java.util.UUID;

public interface CreateWorkspaceUseCase {

    UUID createWorkspace(String name, String plan, UUID createdBy);
}
