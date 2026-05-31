import { configureStore } from '@reduxjs/toolkit';
import authReducer from './auth/slice';
import workspaceReducer from './workspace/slice';
import { contentHubApi } from '../api/contentHubApi';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    workspace: workspaceReducer,
    [contentHubApi.reducerPath]: contentHubApi.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(contentHubApi.middleware),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
