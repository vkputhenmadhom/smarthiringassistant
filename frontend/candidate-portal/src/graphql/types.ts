export interface AuthUser {
  id: string;
  username: string;
  email: string;
  role: string;
}

export interface AuthPayload {
  token: string;
  refreshToken: string;
  expiresIn: number;
  user: AuthUser;
}

export interface GraphQLPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size?: number;
}

export interface Job {
  id: string;
  title: string;
  department?: string;
  location?: string;
  type: string;
  status: string;
  skills: string[];
  salaryMin?: number;
  salaryMax?: number;
  salaryCurrency?: string;
  salaryConfidence?: number;
  applicantCount?: number;
}

export interface StagePassRate {
  stage: string;
  passRate: number;
  totalCount: number;
}

export interface SkillDemand {
  skill: string;
  count: number;
}

export interface ActivityItem {
  type: string;
  description: string;
  timestamp: string;
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
  topSkillsInDemand: SkillDemand[];
  recentActivity: ActivityItem[];
}

export interface StageResult {
  stage: string;
  passed: boolean;
  score: number;
  reasons: string[];
  evaluatedAt?: string;
}

export interface ScreeningSession {
  id: string;
  candidateId: string;
  jobId: string;
  currentStage: string;
  status: string;
  decision: string;
  finalScore?: number | null;
  stageResults: StageResult[];
  failureReasons?: string[];
  createdAt?: string;
  completedAt?: string;
}

export interface ResumeUploadPayload {
  id: string;
  parseStatus: string;
}

