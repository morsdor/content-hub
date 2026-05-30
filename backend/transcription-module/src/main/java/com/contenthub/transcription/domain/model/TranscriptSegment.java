package com.contenthub.transcription.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class TranscriptSegment {

	String speaker;
	int startMs;
	int endMs;
	String text;
	List<TranscriptWord> words;
}
