package com.contenthub.workspace.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class WorkspaceMemberId implements Serializable {

	@Column(name = "workspace_id", columnDefinition = "uuid", nullable = false)
	private UUID workspaceId;

	@Column(name = "user_id", columnDefinition = "uuid", nullable = false)
	private UUID userId;
}
