package com.contenthub.transcription.domain.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TranscriptWord {

	String word;
	int startMs;
	int endMs;
	double confidence;
}
