import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import type { RootState } from '../store';

// ── Workspace types ────────────────────────────────────────────────────────

export interface Workspace {
  id: string;
  name: string;
  createdAt?: string;
}

export interface CreateWorkspaceRequest {
  name: string;
  plan?: string;
}

export interface CreateWorkspaceResponse {
  id: string;
}

// ── API slice ──────────────────────────────────────────────────────────────

export const contentHubApi = createApi({
  reducerPath: 'contentHubApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/api',
    prepareHeaders: (headers, { getState }) => {
      const token = (getState() as RootState).auth.token;
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
  tagTypes: ['Workspace'],
  endpoints: (builder) => ({
    getWorkspaces: builder.query<Workspace[], void>({
      query: () => '/v1/workspaces',
      providesTags: ['Workspace'],
    }),
    createWorkspace: builder.mutation<CreateWorkspaceResponse, CreateWorkspaceRequest>({
      query: (body) => ({ url: '/v1/workspaces', method: 'POST', body }),
      invalidatesTags: ['Workspace'],
    }),
  }),
});

export const { useGetWorkspacesQuery, useCreateWorkspaceMutation } = contentHubApi;
