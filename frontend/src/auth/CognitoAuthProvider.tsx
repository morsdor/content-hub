import { useEffect } from 'react';
import { AuthProvider, useAuth as useOidcAuth } from 'react-oidc-context';
import { useDispatch } from 'react-redux';
import { setCredentials, clearCredentials } from '../store/auth/slice';
import type { AppDispatch } from '../store';

const oidcConfig = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8090/contenthub',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'contenthub-spa',
  redirect_uri: import.meta.env.VITE_OIDC_REDIRECT_URI ?? 'http://localhost:5173/auth/callback',
  scope: import.meta.env.VITE_OIDC_SCOPE ?? 'openid profile email',
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, '/workspaces');
  },
};

function AuthSync() {
  const oidc = useOidcAuth();
  const dispatch = useDispatch<AppDispatch>();

  useEffect(() => {
    if (oidc.isAuthenticated && oidc.user) {
      dispatch(
        setCredentials({
          token: oidc.user.access_token,
          user: {
            sub: oidc.user.profile.sub,
            email: (oidc.user.profile.email as string) ?? '',
            name: (oidc.user.profile.name as string) ?? '',
          },
        }),
      );
    } else if (!oidc.isAuthenticated && !oidc.isLoading) {
      dispatch(clearCredentials());
    }
  }, [oidc.isAuthenticated, oidc.isLoading, oidc.user, dispatch]);

  return null;
}

export function CognitoAuthProvider({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider {...oidcConfig}>
      <AuthSync />
      {children}
    </AuthProvider>
  );
}
