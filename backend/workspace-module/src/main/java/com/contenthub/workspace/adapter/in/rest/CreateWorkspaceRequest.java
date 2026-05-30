package com.contenthub.workspace.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateWorkspaceRequest(
        @NotBlank String name,
        String plan,
        @NotNull UUID createdBy
) {}
