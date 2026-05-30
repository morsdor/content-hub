package com.contenthub.media.application.port.out;

import com.contenthub.media.domain.model.MediaAsset;

import java.util.Optional;
import java.util.UUID;

public interface MediaPersistencePort {

	MediaAsset save(MediaAsset asset);

	Optional<MediaAsset> findById(UUID id);
}
