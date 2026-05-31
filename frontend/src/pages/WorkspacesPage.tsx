import { useNavigate } from 'react-router-dom';
import Button from '@atlaskit/button/new';
import Heading from '@atlaskit/heading';
import { WorkspaceList } from '../features/workspace/WorkspaceList';
import { WorkspaceCreate } from '../features/workspace/WorkspaceCreate';
import { useAuth } from '../auth/useAuth';

export function WorkspacesPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div style={{ maxWidth: '640px', margin: '0 auto', padding: '2rem' }}>
      <header
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '2rem',
        }}
      >
        <Heading size="xlarge">ContentHub</Heading>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          {user && (
            <span style={{ color: 'var(--ds-text-subtle)', fontSize: '0.875rem' }}>
              {user.email}
            </span>
          )}
          <Button appearance="subtle" onClick={handleLogout}>
            Sign out
          </Button>
        </div>
      </header>

      <Heading size="large">Workspaces</Heading>
      <div style={{ marginTop: '1rem' }}>
        <WorkspaceList />
        <WorkspaceCreate />
      </div>
    </div>
  );
}
