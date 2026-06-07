package com.contenthub.transcription.adapter.in.rest;

import java.util.List;
import java.util.UUID;

public record TranscriptResponse(UUID mediaAssetId, String status, String language,
		List<TranscriptSegmentDto> segments) {
}
