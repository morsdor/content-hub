package com.contenthub.media.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "edit_decision_list")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EditDecisionList {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(columnDefinition = "uuid", updatable = false, nullable = false)
	private UUID id;

	@Column(name = "card_id", nullable = false, columnDefinition = "uuid")
	private UUID cardId;

	@Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
	private UUID workspaceId;

	@Version
	private long version;

	@Column(name = "updated_at", nullable = false)
	@Builder.Default
	private Instant updatedAt = Instant.now();
}
