import { useAuth as useOidcAuth } from 'react-oidc-context';
import { useSelector } from 'react-redux';
import { selectAuth } from '../store/auth/slice';

export function useAuth() {
  const oidc = useOidcAuth();
  const { token, user } = useSelector(selectAuth);

  return {
    user,
    token,
    isAuthenticated: oidc.isAuthenticated,
    isLoading: oidc.isLoading,
    login: () => void oidc.signinRedirect(),
    logout: () => void oidc.removeUser(),
  };
}
