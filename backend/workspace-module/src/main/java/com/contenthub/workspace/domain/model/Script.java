package com.contenthub.workspace.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "script")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Script {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(columnDefinition = "uuid", updatable = false, nullable = false)
	private UUID id;

	@Column(name = "card_id", nullable = false, unique = true, columnDefinition = "uuid")
	private UUID cardId;

	@Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
	private UUID workspaceId;

	@Column(name = "crdt_state", nullable = false, columnDefinition = "bytea")
	@Builder.Default
	private byte[] crdtState = new byte[0];

	@Column(name = "plain_text", nullable = false)
	@Builder.Default
	private String plainText = "";

	@Column(nullable = false)
	@Builder.Default
	private long version = 0L;

	@Column(name = "updated_at", nullable = false)
	@Builder.Default
	private Instant updatedAt = Instant.now();
}
