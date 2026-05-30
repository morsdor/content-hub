package com.contenthub.workspace.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkspaceRequest(@NotBlank String name, String plan) {
}
