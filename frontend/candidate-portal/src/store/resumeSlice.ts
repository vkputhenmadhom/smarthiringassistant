import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { RootState } from './index';
import { ResumeUploadPayload } from '../graphql';

interface ResumeState { uploading: boolean; error: string | null; parseStatus: string | null; resumeId: string | null; }

interface ResumeParseApiResponse {
  resumeId?: string;
  status?: string;
  message?: string;
}

const initialState: ResumeState = { uploading: false, error: null, parseStatus: null, resumeId: null };

export const uploadResume = createAsyncThunk<ResumeUploadPayload, File, { state: RootState; rejectValue: string }>(
  'resume/upload',
  async (file, { getState, rejectWithValue }) => {
    try {
      const token = getState().auth.token ?? localStorage.getItem('sha_token');
      if (!token) {
        return rejectWithValue('Please sign in again before uploading your resume.');
      }

      const formData = new FormData();
      formData.append('file', file, file.name);

      const response = await fetch('/api/resumes/parse', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
        },
        body: formData,
      });

      const body = (await response.json().catch(() => ({}))) as ResumeParseApiResponse;

      if (!response.ok) {
        return rejectWithValue(body.message ?? `Upload failed (${response.status})`);
      }

      return {
        id: body.resumeId ?? 'unknown',
        parseStatus: body.status ?? 'PROCESSING',
      };
    } catch (error) {
      return rejectWithValue(error instanceof Error ? error.message : 'Upload failed');
    }
  }
);

const resumeSlice = createSlice({
  name: 'resume',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder
      .addCase(uploadResume.pending,   s => { s.uploading = true; s.error = null; })
      .addCase(uploadResume.fulfilled, (s, a) => { s.uploading = false; s.resumeId = a.payload.id; s.parseStatus = a.payload.parseStatus; })
      .addCase(uploadResume.rejected,  (s, a) => {
        s.uploading = false;
        s.error = (a.payload as string) ?? a.error.message ?? 'Upload failed';
      });
  },
});

export default resumeSlice.reducer;
