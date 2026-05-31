import { createSlice } from '@reduxjs/toolkit';
import type { RootState } from '../index';

interface WorkspaceState {
  currentWorkspaceId: string | null;
}

const workspaceSlice = createSlice({
  name: 'workspace',
  initialState: { currentWorkspaceId: null } as WorkspaceState,
  reducers: {
    setCurrentWorkspace(state, action: { payload: string }) {
      state.currentWorkspaceId = action.payload;
    },
  },
});

export const { setCurrentWorkspace } = workspaceSlice.actions;
export const selectCurrentWorkspaceId = (state: RootState) => state.workspace.currentWorkspaceId;
export default workspaceSlice.reducer;
