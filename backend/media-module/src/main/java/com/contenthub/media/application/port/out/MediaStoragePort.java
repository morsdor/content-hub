package com.contenthub.media.application.port.out;

import java.net.URL;

public interface MediaStoragePort {

	URL generatePresignedPutUrl(String bucket, String key, String contentType, long sizeBytes);

	String resolveMediaBucket();
}
