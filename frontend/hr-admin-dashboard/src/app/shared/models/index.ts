export interface User {
  id: string;
  username: string;
  email: string;
  role: Role;
  createdAt?: string;
}

export type Role = 'CANDIDATE' | 'HR_ADMIN' | 'RECRUITER' | 'SUPER_ADMIN';

export interface AuthPayload {
  token: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export interface Job {
  id: string;
  title: string;
  description: string;
  department?: string;
  location?: string;
  type: JobType;
  status: JobStatus;
  skills: string[];
  salaryMin?: number;
  salaryMax?: number;
  salaryCurrency?: string;
  salaryConfidence?: number;
  postedAt?: string;
  closingDate?: string;
  applicantCount?: number;
}

export type JobType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERNSHIP' | 'REMOTE';
export type JobStatus = 'DRAFT' | 'OPEN' | 'CLOSED' | 'ARCHIVED';

export interface Candidate {
  id: string;
  userId: string;
  name: string;
  email: string;
  phone?: string;
  skills: string[];
  experience: Experience[];
  education: Education[];
  resumeUrl?: string;
  parseStatus: ParseStatus;
  screeningStatus?: ScreeningStatus;
  matchScore?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface Experience {
  company: string;
  title: string;
  startDate?: string;
  endDate?: string;
  description?: string;
  technologies?: string[];
}

export interface Education {
  institution: string;
  degree: string;
  field?: string;
  graduationYear?: number;
}

export type ParseStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type ScreeningStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'PASSED' | 'FAILED';
export type MatchStatus = 'PENDING' | 'MATCHED' | 'REJECTED' | 'SHORTLISTED' | 'HIRED';

export interface CandidateMatch {
  id: string;
  candidateId: string;
  jobId: string;
  score: number;
  status: MatchStatus;
  skillMatches: string[];
  skillGaps: string[];
  candidate?: Candidate;
  job?: Job;
  matchedAt?: string;
}

export interface ScreeningSession {
  id: string;
  candidateId: string;
  jobId: string;
  currentStage: string;
  status: string;
  decision: string;
  finalScore?: number;
  stageResults: StageResult[];
  failureReasons: string[];
  createdAt?: string;
  completedAt?: string;
}

export interface StageResult {
  stage: string;
  passed: boolean;
  score: number;
  response?: string;
  reasons: string[];
  evaluatedAt?: string;
}

export interface DashboardMetrics {
  totalJobs: number;
  openJobs: number;
  totalCandidates: number;
  activeCandidates: number;
  pendingScreenings: number;
  completedScreenings: number;
  averageMatchScore: number;
  hireRate: number;
  stagePassRates: StagePassRate[];
  topSkillsInDemand: SkillDemand[];
  recentActivity: ActivityItem[];
}

export interface StagePassRate { stage: string; passRate: number; totalCount: number; }
export interface SkillDemand   { skill: string; count: number; }
export interface ActivityItem  { type: string; description: string; timestamp: string; }

export interface Notification {
  id: string;
  userId: string;
  type: string;
  title: string;
  message: string;
  read: boolean;
  createdAt?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

