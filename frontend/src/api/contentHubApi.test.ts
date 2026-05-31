import { contentHubApi } from './contentHubApi';

describe('contentHubApi', () => {
  it('has the expected reducer path', () => {
    expect(contentHubApi.reducerPath).toBe('contentHubApi');
  });

  it('exposes getWorkspaces and createWorkspace endpoints', () => {
    expect(contentHubApi.endpoints.getWorkspaces).toBeDefined();
    expect(contentHubApi.endpoints.createWorkspace).toBeDefined();
  });
});
