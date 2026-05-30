package com.contenthub.transcription.application.port.out;

import com.contenthub.transcription.domain.model.TranscriptSegment;

import java.util.List;

public interface AsrPort {

	List<TranscriptSegment> transcribe(String s3Bucket, String s3Key);
}
