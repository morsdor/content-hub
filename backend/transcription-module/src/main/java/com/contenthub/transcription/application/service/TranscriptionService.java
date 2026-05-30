package com.contenthub.transcription.application.service;

import com.contenthub.transcription.application.port.in.TranscribeMediaUseCase;
import com.contenthub.transcription.application.port.out.AsrPort;
import com.contenthub.transcription.application.port.out.TranscriptPersistencePort;
import com.contenthub.transcription.domain.model.Transcript;
import com.contenthub.transcription.domain.model.TranscriptSegment;
import com.contenthub.transcription.domain.model.TranscriptStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranscriptionService implements TranscribeMediaUseCase {

    private final AsrPort asrPort;
    private final TranscriptPersistencePort transcriptPersistencePort;

    @Override
    public void transcribe(UUID mediaId, UUID workspaceId, String s3Key, String s3Bucket, String traceId) {
        log.info("Starting transcription for mediaId={} traceId={}", mediaId, traceId);
        List<TranscriptSegment> segments = asrPort.transcribe(s3Bucket, s3Key);

        Transcript transcript = Transcript.builder()
                .mediaAssetId(mediaId)
                .workspaceId(workspaceId)
                .provider("mock")
                .language("en")
                .status(TranscriptStatus.COMPLETED)
                .segments(segments)
                .build();

        try {
            transcriptPersistencePort.save(transcript);
            log.info("Transcription completed for mediaId={}", mediaId);
        } catch (DuplicateKeyException e) {
            // unique index on mediaAssetId — duplicate delivery, safe to ignore
            log.info("Transcript already exists for mediaId={}, skipping (idempotent)", mediaId);
        }
    }
}
