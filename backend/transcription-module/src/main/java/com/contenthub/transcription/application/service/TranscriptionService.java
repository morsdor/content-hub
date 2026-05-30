package com.contenthub.transcription.application.service;

import com.contenthub.transcription.adapter.out.persistence.TranscriptRepository;
import com.contenthub.transcription.application.port.in.TranscribeMediaUseCase;
import com.contenthub.transcription.application.port.out.AsrPort;
import com.contenthub.transcription.domain.model.Transcript;
import com.contenthub.transcription.domain.model.TranscriptSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranscriptionService implements TranscribeMediaUseCase {

    private final AsrPort asrPort;
    private final TranscriptRepository transcriptRepository;

    @Override
    public void transcribe(UUID mediaId, UUID workspaceId, String s3Key, String s3Bucket, String traceId) {
        if (transcriptRepository.findByMediaAssetId(mediaId).isPresent()) {
            log.info("Transcript already exists for mediaId={}, skipping (idempotent)", mediaId);
            return;
        }

        log.info("Starting transcription for mediaId={} traceId={}", mediaId, traceId);
        List<TranscriptSegment> segments = asrPort.transcribe(s3Bucket, s3Key);

        Transcript transcript = Transcript.builder()
                .mediaAssetId(mediaId)
                .workspaceId(workspaceId)
                .provider("mock")
                .language("en")
                .status("completed")
                .segments(segments)
                .build();

        transcriptRepository.save(transcript);
        log.info("Transcription completed for mediaId={}", mediaId);
    }
}
