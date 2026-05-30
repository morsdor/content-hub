package com.contenthub.media.domain.model;

import com.contenthub.media.adapter.out.persistence.MediaStatusConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "media_asset")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID workspaceId;

    @Column(name = "card_id", columnDefinition = "uuid", updatable = false)
    private UUID cardId;

    @Column(name = "s3_key", nullable = false, updatable = false)
    private String s3Key;

    @Column(name = "s3_bucket", nullable = false, updatable = false)
    private String s3Bucket;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Setter
    @Column(name = "duration_ms")
    private Integer durationMs;

    @Setter
    @Convert(converter = MediaStatusConverter.class)
    @Column(nullable = false)
    @Builder.Default
    private MediaStatus status = MediaStatus.UPLOADING;

    @Setter
    @Column(name = "transcript_id")
    private String transcriptId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Setter
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
