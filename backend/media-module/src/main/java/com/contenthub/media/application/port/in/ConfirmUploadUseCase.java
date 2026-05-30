package com.contenthub.media.application.port.in;

import java.util.UUID;

public interface ConfirmUploadUseCase {

    void confirmUpload(UUID mediaId, UUID callerUserId, String traceId);
}
