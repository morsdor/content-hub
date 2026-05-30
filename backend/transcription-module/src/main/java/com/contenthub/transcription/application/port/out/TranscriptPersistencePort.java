package com.contenthub.transcription.application.port.out;

import com.contenthub.transcription.domain.model.Transcript;

import java.util.UUID;

public interface TranscriptPersistencePort {

	Transcript save(Transcript transcript);

	boolean existsByMediaAssetId(UUID mediaAssetId);
}
