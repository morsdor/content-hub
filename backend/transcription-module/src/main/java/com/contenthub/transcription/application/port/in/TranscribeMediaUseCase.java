package com.contenthub.transcription.application.port.in;

import java.util.UUID;

public interface TranscribeMediaUseCase {

	void transcribe(UUID mediaId, UUID workspaceId, String s3Key, String s3Bucket, String traceId);
}
