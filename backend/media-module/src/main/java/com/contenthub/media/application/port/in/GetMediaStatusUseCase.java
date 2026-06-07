package com.contenthub.media.application.port.in;

import com.contenthub.media.domain.model.MediaAsset;

import java.util.Optional;
import java.util.UUID;

public interface GetMediaStatusUseCase {

	Optional<MediaAsset> getMediaStatus(UUID mediaId);
}
