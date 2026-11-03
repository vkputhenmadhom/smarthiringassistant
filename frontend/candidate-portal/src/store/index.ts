import { configureStore } from '@reduxjs/toolkit';
import authReducer from './authSlice';
import jobsReducer from './jobsSlice';
import resumeReducer from './resumeSlice';

export const store = configureStore({
  reducer: {
    auth:   authReducer,
    jobs:   jobsReducer,
    resume: resumeReducer,
  },
});

export type RootState   = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
