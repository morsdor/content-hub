import { useNavigate } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import Button from '@atlaskit/button/new';
import Spinner from '@atlaskit/spinner';
import EmptyState from '@atlaskit/empty-state';
import { setCurrentWorkspace } from '../../store/workspace/slice';
import { useGetWorkspacesQuery } from '../../api/contentHubApi';
import type { AppDispatch } from '../../store';

export function WorkspaceList() {
  const { data: workspaces = [], isLoading, error } = useGetWorkspacesQuery();
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: '2rem' }}>
        <Spinner />
      </div>
    );
  }

  if (error) {
    return (
      <EmptyState
        header="Could not load workspaces"
        description="Check that the backend is running and try again."
      />
    );
  }

  if (workspaces.length === 0) {
    return (
      <EmptyState
        header="No workspaces yet"
        description="Create your first workspace below to get started."
      />
    );
  }

  return (
    <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
      {workspaces.map((ws) => (
        <li key={ws.id} style={{ marginBottom: '0.5rem' }}>
          <Button
            appearance="subtle"
            onClick={() => {
              dispatch(setCurrentWorkspace(ws.id));
              navigate(`/workspaces/${ws.id}`);
            }}
          >
            {ws.name}
          </Button>
        </li>
      ))}
    </ul>
  );
}
