package com.contenthub.media.application.port.in;

import java.net.URL;
import java.util.UUID;

public interface RequestPresignedUploadUseCase {

	record PresignedUpload(UUID mediaId, URL presignedUrl, String s3Key) {
	}

	PresignedUpload requestUpload(UUID workspaceId, UUID cardId, String filename, String contentType, long sizeBytes);
}
