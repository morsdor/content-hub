package com.contenthub.workspace.adapter.in.rest;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceListItem(UUID id, String name, Instant createdAt) {
}
