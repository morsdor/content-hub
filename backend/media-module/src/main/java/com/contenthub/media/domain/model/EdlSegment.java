package com.contenthub.media.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "edl_segment", uniqueConstraints = @UniqueConstraint(name = "idx_edl_seq", columnNames = {"edl_id",
		"seq"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EdlSegment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(columnDefinition = "uuid", updatable = false, nullable = false)
	private UUID id;

	@Column(name = "edl_id", nullable = false, columnDefinition = "uuid")
	private UUID edlId;

	@Column(name = "media_asset_id", nullable = false, columnDefinition = "uuid")
	private UUID mediaAssetId;

	@Column(nullable = false)
	private int seq;

	@Column(name = "source_start_ms", nullable = false)
	private int sourceStartMs;

	@Column(name = "source_end_ms", nullable = false)
	private int sourceEndMs;
}
