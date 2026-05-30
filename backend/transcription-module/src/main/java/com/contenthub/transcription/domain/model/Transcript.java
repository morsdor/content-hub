package com.contenthub.transcription.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document(collection = "transcripts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transcript {

    @Id
    private String id;

    @Indexed
    private UUID mediaAssetId;

    private UUID workspaceId;
    private String provider;
    private String language;
    private long durationMs;

    @Builder.Default
    private String status = "processing";

    private List<TranscriptSegment> segments;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
