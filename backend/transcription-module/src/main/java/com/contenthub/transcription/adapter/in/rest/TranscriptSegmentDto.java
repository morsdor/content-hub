package com.contenthub.transcription.adapter.in.rest;

public record TranscriptSegmentDto(String speaker, int startMs, int endMs, String text) {
}
