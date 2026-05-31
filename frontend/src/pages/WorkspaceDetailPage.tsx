import { useParams, useNavigate } from 'react-router-dom';
import Button from '@atlaskit/button/new';
import Heading from '@atlaskit/heading';
import { useGetWorkspacesQuery } from '../api/contentHubApi';
import { MediaUpload } from '../features/media';
import { AnalyticsDashboard } from '../features/analytics';
import { useWebSocket } from '../hooks/useWebSocket';

export function WorkspaceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: workspaces = [] } = useGetWorkspacesQuery();
  const workspace = workspaces.find((ws) => ws.id === id);

  useWebSocket(id ?? '');

  return (
    <div style={{ maxWidth: '960px', margin: '0 auto', padding: '2rem' }}>
      <div style={{ marginBottom: '1.5rem' }}>
        <Button appearance="subtle" onClick={() => navigate('/workspaces')}>
          ← Workspaces
        </Button>
      </div>

      <Heading size="xlarge">{workspace?.name ?? id}</Heading>

      <p style={{ color: 'var(--ds-text-subtle)', fontSize: '0.875rem', marginTop: '0.5rem' }}>
        Phase 0 skeleton — Kanban board and script editor coming in Phase 1.
      </p>

      <div style={{ marginTop: '2rem', display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
        <MediaUpload />
        <AnalyticsDashboard />
      </div>
    </div>
  );
}
