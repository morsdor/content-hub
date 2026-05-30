package com.contenthub.media.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record UploadUrlRequest(
        @NotNull UUID workspaceId,
        UUID cardId,
        @NotBlank String filename,
        @NotBlank String contentType,
        @Positive long sizeBytes
) {}
