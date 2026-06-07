package com.contenthub.transcription.application.port.in;

import com.contenthub.transcription.domain.model.Transcript;

import java.util.Optional;
import java.util.UUID;

public interface GetTranscriptUseCase {

	Optional<Transcript> getTranscript(UUID mediaId);
}
