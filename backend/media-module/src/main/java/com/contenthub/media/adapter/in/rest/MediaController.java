package com.contenthub.media.adapter.in.rest;

import com.contenthub.media.application.port.in.ConfirmUploadUseCase;
import com.contenthub.media.application.port.in.RequestPresignedUploadUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

	private final RequestPresignedUploadUseCase requestPresignedUploadUseCase;
	private final ConfirmUploadUseCase confirmUploadUseCase;

	@PostMapping("/upload-url")
	@ResponseStatus(HttpStatus.CREATED)
	public UploadUrlResponse requestUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
		var result = requestPresignedUploadUseCase.requestUpload(request.workspaceId(), request.cardId(),
				request.filename(), request.contentType(), request.sizeBytes());
		return new UploadUrlResponse(result.mediaId(), result.presignedUrl().toString(), result.s3Key());
	}

	// Called by the browser after the S3 presigned PUT succeeds.
	// Transitions the asset UPLOADING → UPLOADED and writes the video.uploaded
	// outbox entry.
	@PatchMapping("/{mediaId}/confirm-upload")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void confirmUpload(@PathVariable UUID mediaId, Principal principal) {
		UUID callerUserId = UUID.fromString(principal.getName());
		// Trace ID: proper OTel propagation wired in Phase 1; UUID placeholder for
		// Phase 0
		confirmUploadUseCase.confirmUpload(mediaId, callerUserId, UUID.randomUUID().toString());
	}
}
