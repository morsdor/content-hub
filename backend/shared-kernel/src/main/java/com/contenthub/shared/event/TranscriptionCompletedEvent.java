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
public class TranscriptionCompletedEvent {

	public static final String TOPIC = "transcription.completed";

	private UUID mediaId;
	private String transcriptId;
	private UUID workspaceId;
	private String traceId;
}
