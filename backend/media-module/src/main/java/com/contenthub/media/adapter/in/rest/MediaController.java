package com.contenthub.media.adapter.in.rest;

import com.contenthub.media.application.port.in.RequestPresignedUploadUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final RequestPresignedUploadUseCase requestPresignedUploadUseCase;

    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadUrlResponse requestUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        var result = requestPresignedUploadUseCase.requestUpload(
                request.workspaceId(),
                request.cardId(),
                request.filename(),
                request.contentType(),
                request.sizeBytes());
        return new UploadUrlResponse(result.mediaId(), result.presignedUrl().toString(), result.s3Key());
    }
}
