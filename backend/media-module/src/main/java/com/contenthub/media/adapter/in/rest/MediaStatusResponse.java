package com.contenthub.media.adapter.in.rest;

import java.util.UUID;

public record MediaStatusResponse(UUID id, UUID workspaceId, String status, String s3Key, String transcriptId) {
}
