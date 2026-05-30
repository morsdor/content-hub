package com.contenthub.media.adapter.out.storage;

import com.contenthub.media.application.port.out.MediaStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URL;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class S3MediaStorageAdapter implements MediaStoragePort {

	private final S3Presigner s3Presigner;

	@Value("${contenthub.s3.media-bucket:contenthub-media-local}")
	private String mediaBucket;

	@Override
	public URL generatePresignedPutUrl(String bucket, String key, String contentType, long sizeBytes) {
		PutObjectRequest objectRequest = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType)
				.build();

		PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
				.signatureDuration(Duration.ofMinutes(15)).putObjectRequest(objectRequest).build();

		PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
		return presigned.url();
	}

	@Override
	public String resolveMediaBucket() {
		return mediaBucket;
	}
}
