package com.contenthub.workspace.application.service;

import com.contenthub.workspace.application.port.in.CreateWorkspaceUseCase;
import com.contenthub.workspace.application.port.out.MemberPersistencePort;
import com.contenthub.workspace.application.port.out.WorkspacePersistencePort;
import com.contenthub.workspace.domain.model.Workspace;
import com.contenthub.workspace.domain.model.WorkspaceMember;
import com.contenthub.workspace.domain.model.WorkspaceMemberId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceService implements CreateWorkspaceUseCase {

	private final WorkspacePersistencePort workspacePersistencePort;
	private final MemberPersistencePort memberPersistencePort;

	@Override
	@Transactional
	public UUID createWorkspace(String name, String plan, UUID createdBy) {
		Workspace workspace = Workspace.builder().name(name).plan(plan).createdBy(createdBy).build();
		Workspace saved = workspacePersistencePort.save(workspace);

		memberPersistencePort.save(
				WorkspaceMember.builder().id(new WorkspaceMemberId(saved.getId(), createdBy)).role("owner").build());

		return saved.getId();
	}
}
