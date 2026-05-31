import Spinner from '@atlaskit/spinner';

// Shown while react-oidc-context processes the authorization code exchange.
// onSigninCallback in CognitoAuthProvider replaces the URL and moves to /workspaces.
export function AuthCallback() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', paddingTop: '6rem' }}>
      <Spinner size="large" />
    </div>
  );
}
