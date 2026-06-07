package com.contenthub.transcription.adapter.in.rest;

import com.contenthub.transcription.application.port.in.GetTranscriptUseCase;
import com.contenthub.transcription.domain.model.Transcript;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media/{mediaId}/transcript")
@RequiredArgsConstructor
public class TranscriptController {

	private final GetTranscriptUseCase getTranscriptUseCase;

	@GetMapping
	public ResponseEntity<TranscriptResponse> get(@PathVariable UUID mediaId) {
		return getTranscriptUseCase.getTranscript(mediaId).map(this::toResponse).map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	private TranscriptResponse toResponse(Transcript t) {
		List<TranscriptSegmentDto> segments = t.getSegments() == null ? List.of()
				: t.getSegments().stream()
						.map(s -> new TranscriptSegmentDto(s.getSpeaker(), s.getStartMs(), s.getEndMs(), s.getText()))
						.toList();
		return new TranscriptResponse(t.getMediaAssetId(), t.getStatus().name(), t.getLanguage(), segments);
	}
}
