package com.contenthub.workspace.adapter.out.persistence;

import com.contenthub.workspace.application.port.out.MemberPersistencePort;
import com.contenthub.workspace.domain.model.WorkspaceMember;
import com.contenthub.workspace.domain.model.WorkspaceMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository
		extends
			JpaRepository<WorkspaceMember, WorkspaceMemberId>,
			MemberPersistencePort {
}
