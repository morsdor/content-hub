package com.contenthub.shared.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoUploadedEvent {

	public static final String TOPIC = "video.uploaded";
	public static final String EVENT_TYPE = "VideoUploaded";

	private UUID mediaId;
	private UUID workspaceId;
	private UUID cardId;
	private String s3Key;
	private String s3Bucket;
	private String traceId;
}
