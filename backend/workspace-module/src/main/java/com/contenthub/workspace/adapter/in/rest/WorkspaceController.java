package com.contenthub.workspace.adapter.in.rest;

import com.contenthub.workspace.application.port.in.CreateWorkspaceUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

	private final CreateWorkspaceUseCase createWorkspaceUseCase;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreateWorkspaceResponse create(@Valid @RequestBody CreateWorkspaceRequest request, Principal principal) {
		// sub claim from the JWT — mock-oauth2 issues UUID subs in local dev
		UUID createdBy = UUID.fromString(principal.getName());
		var id = createWorkspaceUseCase.createWorkspace(request.name(),
				request.plan() != null ? request.plan() : "solo", createdBy);
		return new CreateWorkspaceResponse(id);
	}
}
