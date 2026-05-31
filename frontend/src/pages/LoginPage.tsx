import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '@atlaskit/button/new';
import Heading from '@atlaskit/heading';
import { useAuth } from '../auth/useAuth';

export function LoginPage() {
  const { isAuthenticated, isLoading, login } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) navigate('/workspaces', { replace: true });
  }, [isAuthenticated, navigate]);

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', paddingTop: '6rem' }}>
        <span>Loading...</span>
      </div>
    );
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        paddingTop: '6rem',
        gap: '1rem',
      }}
    >
      <Heading size="xlarge">ContentHub</Heading>
      <p style={{ color: 'var(--ds-text-subtle)', margin: 0 }}>
        Browser-based Descript-style content production platform.
      </p>
      <div style={{ marginTop: '1rem' }}>
        <Button appearance="primary" onClick={login}>
          Sign in
        </Button>
      </div>
    </div>
  );
}
