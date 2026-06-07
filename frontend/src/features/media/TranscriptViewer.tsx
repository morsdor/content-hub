import { useEffect, useRef } from 'react';
import Spinner from '@atlaskit/spinner';
import { token } from '@atlaskit/tokens';
import { useGetMediaStatusQuery, useGetTranscriptQuery } from '../../api/contentHubApi';

interface TranscriptViewerProps {
  mediaId: string;
  onReady?: () => void;
}

export function TranscriptViewer({ mediaId, onReady }: TranscriptViewerProps) {
  const onReadyCalled = useRef(false);

  // Poll media status every 2s until READY or FAILED
  const { data: mediaStatus } = useGetMediaStatusQuery(mediaId, {
    pollingInterval: 2000,
    skip: !mediaId,
  });

  const isReady = mediaStatus?.status === 'READY';
  const isFailed = mediaStatus?.status === 'FAILED';

  const { data: transcript, isLoading: transcriptLoading } = useGetTranscriptQuery(mediaId, {
    skip: !isReady,
  });

  useEffect(() => {
    if (isReady && onReady && !onReadyCalled.current) {
      onReadyCalled.current = true;
      onReady();
    }
  }, [isReady, onReady]);

  if (isFailed) {
    return (
      <p style={{ color: token('color.text.danger', '#AE2A19'), fontSize: '0.875rem' }}>
        Transcription failed.
      </p>
    );
  }

  if (!isReady) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: token('space.100', '8px') }}>
        <Spinner size="small" />
        <span style={{ color: token('color.text.subtlest', '#626F86'), fontSize: '0.875rem' }}>
          Waiting for transcription… (status: {mediaStatus?.status ?? 'checking'})
        </span>
      </div>
    );
  }

  if (transcriptLoading) {
    return <Spinner size="small" />;
  }

  if (!transcript || transcript.segments.length === 0) {
    return (
      <p style={{ color: token('color.text.subtlest', '#626F86'), fontSize: '0.875rem' }}>
        No transcript segments found.
      </p>
    );
  }

  return (
    <div
      style={{
        background: token('color.background.neutral', '#F7F8F9'),
        borderRadius: '4px',
        padding: token('space.200', '16px'),
        maxHeight: '320px',
        overflowY: 'auto',
      }}
    >
      {transcript.segments.map((seg, i) => (
        <div key={i} style={{ marginBottom: token('space.150', '12px') }}>
          <span
            style={{
              fontSize: '0.75rem',
              fontWeight: 600,
              color: token('color.text.accent.blue', '#0C66E4'),
              marginRight: token('space.100', '8px'),
            }}
          >
            {seg.speaker}
          </span>
          <span
            style={{
              fontSize: '0.75rem',
              color: token('color.text.subtlest', '#626F86'),
              marginRight: token('space.150', '12px'),
            }}
          >
            {formatMs(seg.startMs)}–{formatMs(seg.endMs)}
          </span>
          <span style={{ fontSize: '0.9375rem', color: token('color.text', '#172B4D') }}>
            {seg.text}
          </span>
        </div>
      ))}
    </div>
  );
}

function formatMs(ms: number): string {
  const s = Math.floor(ms / 1000);
  const m = Math.floor(s / 60);
  return `${m}:${String(s % 60).padStart(2, '0')}`;
}
