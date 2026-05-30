package com.contenthub.workspace.application.port.out;

import com.contenthub.workspace.domain.model.WorkspaceMember;

public interface MemberPersistencePort {

	WorkspaceMember save(WorkspaceMember member);
}
