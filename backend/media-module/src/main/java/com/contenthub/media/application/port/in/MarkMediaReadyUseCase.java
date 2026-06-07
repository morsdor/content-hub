package com.contenthub.media.application.port.in;

import java.util.UUID;

public interface MarkMediaReadyUseCase {

	void markReady(UUID mediaId, String transcriptId);
}
