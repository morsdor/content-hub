package com.contenthub.transcription.adapter.out.persistence;

import com.contenthub.transcription.domain.model.Transcript;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface TranscriptRepository extends MongoRepository<Transcript, String> {

    Optional<Transcript> findByMediaAssetId(UUID mediaAssetId);
}
