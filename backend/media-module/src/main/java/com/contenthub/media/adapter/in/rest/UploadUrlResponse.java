package com.contenthub.media.adapter.in.rest;

import java.util.UUID;

public record UploadUrlResponse(
        UUID mediaId,
        String presignedUrl,
        String s3Key
) {}
