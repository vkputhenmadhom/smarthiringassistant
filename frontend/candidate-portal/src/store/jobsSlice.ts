import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { apolloClient } from '../apolloClient';
import { GraphQLPage, Job, JOBS_QUERY } from '../graphql';

interface JobsState {
  items: Job[]; loading: boolean; error: string | null;
  totalElements: number; totalPages: number; page: number;
  selectedJobId: string | null;
}

const initialState: JobsState = {
  items: [], loading: false, error: null,
  totalElements: 0, totalPages: 0, page: 0, selectedJobId: null,
};

export const fetchJobs = createAsyncThunk('jobs/fetch', async (params: { page?: number; size?: number; search?: string } = {}) => {
  const res = await apolloClient.query<{ jobs: GraphQLPage<Job> }>({
    query: JOBS_QUERY,
    variables: params,
    fetchPolicy: 'network-only',
  });
  return res.data.jobs;
});

const jobsSlice = createSlice({
  name: 'jobs',
  initialState,
  reducers: {
    selectJob: (s, a) => { s.selectedJobId = a.payload; },
  },
  extraReducers: builder => {
    builder
      .addCase(fetchJobs.pending,   s => { s.loading = true; s.error = null; })
      .addCase(fetchJobs.fulfilled, (s, a) => {
        s.loading = false; s.items = a.payload.content;
        s.totalElements = a.payload.totalElements; s.totalPages = a.payload.totalPages; s.page = a.payload.page;
      })
      .addCase(fetchJobs.rejected, (s, a) => { s.loading = false; s.error = a.error.message ?? 'Failed to load jobs'; });
  },
});

export const { selectJob } = jobsSlice.actions;
export default jobsSlice.reducer;
