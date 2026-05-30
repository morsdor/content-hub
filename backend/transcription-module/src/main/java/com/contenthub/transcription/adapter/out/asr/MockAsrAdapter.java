package com.contenthub.transcription.adapter.out.asr;

import com.contenthub.transcription.application.port.out.AsrPort;
import com.contenthub.transcription.domain.model.TranscriptSegment;
import com.contenthub.transcription.domain.model.TranscriptWord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class MockAsrAdapter implements AsrPort {

	@Override
	public List<TranscriptSegment> transcribe(String s3Bucket, String s3Key) {
		log.info("Mock ASR: transcribing s3://{}/{}", s3Bucket, s3Key);
		return List.of(TranscriptSegment.builder().speaker("S1").startMs(0).endMs(4200)
				.text("This is a mock transcript from the development ASR adapter.")
				.words(List.of(TranscriptWord.builder().word("This").startMs(0).endMs(180).confidence(0.99).build(),
						TranscriptWord.builder().word("is").startMs(200).endMs(340).confidence(0.99).build(),
						TranscriptWord.builder().word("a").startMs(360).endMs(420).confidence(0.99).build(),
						TranscriptWord.builder().word("mock").startMs(440).endMs(680).confidence(0.99).build(),
						TranscriptWord.builder().word("transcript").startMs(700).endMs(1100).confidence(0.98).build()))
				.build());
	}
}
