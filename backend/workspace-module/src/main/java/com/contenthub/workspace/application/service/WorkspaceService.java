package com.contenthub.workspace.application.service;

import com.contenthub.workspace.adapter.out.persistence.WorkspaceRepository;
import com.contenthub.workspace.application.port.in.CreateWorkspaceUseCase;
import com.contenthub.workspace.domain.model.Workspace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceService implements CreateWorkspaceUseCase {

    private final WorkspaceRepository workspaceRepository;

    @Override
    @Transactional
    public UUID createWorkspace(String name, String plan, UUID createdBy) {
        Workspace workspace = Workspace.builder()
                .name(name)
                .plan(plan)
                .createdBy(createdBy)
                .build();
        return workspaceRepository.save(workspace).getId();
    }
}
