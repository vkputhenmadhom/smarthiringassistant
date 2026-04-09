import { gql } from 'apollo-angular';

// ── Auth ──────────────────────────────────────────────────────────────────────
export const LOGIN_MUTATION = gql`
  mutation Login($username: String!, $password: String!) {
    login(username: $username, password: $password) {
      token refreshToken expiresIn
      user { id username email role }
    }
  }
`;

export const REGISTER_MUTATION = gql`
  mutation Register($username: String!, $email: String!, $password: String!, $role: Role!) {
    register(username: $username, email: $email, password: $password, role: $role) {
      token refreshToken expiresIn
      user { id username email role }
    }
  }
`;

export const ME_QUERY = gql`
  query Me { me { id username email role } }
`;

export const REFRESH_TOKEN_MUTATION = gql`
  mutation RefreshToken($refreshToken: String!) {
    refreshToken(refreshToken: $refreshToken) {
      token
      refreshToken
      expiresIn
      user { id username email role }
    }
  }
`;

// ── Dashboard ─────────────────────────────────────────────────────────────────
export const DASHBOARD_METRICS_QUERY = gql`
  query DashboardMetrics {
    dashboardMetrics {
      totalJobs openJobs totalCandidates activeCandidates
      pendingScreenings completedScreenings averageMatchScore hireRate
      stagePassRates { stage passRate totalCount }
      topSkillsInDemand { skill count }
      recentActivity { type description timestamp }
    }
  }
`;

// ── Jobs ──────────────────────────────────────────────────────────────────────
export const JOBS_QUERY = gql`
  query Jobs($page: Int, $size: Int, $status: JobStatus, $search: String) {
    jobs(page: $page, size: $size, status: $status, search: $search) {
      totalElements totalPages page size
      content {
        id title department location type status skills
        salaryMin salaryMax salaryCurrency salaryConfidence
        applicantCount postedAt closingDate
      }
    }
  }
`;

export const JOB_QUERY = gql`
  query Job($id: ID!) {
    job(id: $id) {
      id title description department location type status skills
      salaryMin salaryMax salaryCurrency salaryConfidence
      applicantCount postedAt closingDate
    }
  }
`;

export const CREATE_JOB_MUTATION = gql`
  mutation CreateJob($input: CreateJobInput!) {
    createJob(input: $input) {
      id title status skills salaryMin salaryMax postedAt
    }
  }
`;

export const UPDATE_JOB_MUTATION = gql`
  mutation UpdateJob($id: ID!, $input: UpdateJobInput!) {
    updateJob(id: $id, input: $input) {
      id title status
    }
  }
`;

export const DELETE_JOB_MUTATION = gql`
  mutation DeleteJob($id: ID!) { deleteJob(id: $id) }
`;

export const PUBLISH_JOB_MUTATION = gql`
  mutation PublishJob($id: ID!) { publishJob(id: $id) { id status } }
`;

// ── Candidates ────────────────────────────────────────────────────────────────
export const CANDIDATES_QUERY = gql`
  query Candidates($page: Int, $size: Int, $status: ScreeningStatus, $search: String) {
    candidates(page: $page, size: $size, status: $status, search: $search) {
      totalElements totalPages page size
      content {
        id name email phone skills parseStatus screeningStatus matchScore createdAt
        experience { company title startDate endDate }
        education   { institution degree field graduationYear }
      }
    }
  }
`;

export const CANDIDATE_QUERY = gql`
  query Candidate($id: ID!) {
    candidate(id: $id) {
      id name email phone skills parseStatus screeningStatus matchScore
      experience { company title startDate endDate description technologies }
      education   { institution degree field graduationYear }
      resumeUrl createdAt updatedAt
    }
  }
`;

export const MATCHES_FOR_JOB_QUERY = gql`
  query MatchesForJob($jobId: ID!, $page: Int, $size: Int) {
    matchesForJob(jobId: $jobId, page: $page, size: $size) {
      totalElements
      content {
        id score status skillMatches skillGaps matchedAt
        candidate { id name email skills screeningStatus }
      }
    }
  }
`;

export const UPDATE_MATCH_STATUS_MUTATION = gql`
  mutation UpdateMatchStatus($matchId: ID!, $status: MatchStatus!) {
    updateMatchStatus(matchId: $matchId, status: $status) {
      id status
    }
  }
`;

// ── Screening ─────────────────────────────────────────────────────────────────
export const SCREENING_SESSION_QUERY = gql`
  query ScreeningSession($id: ID!) {
    screeningSession(id: $id) {
      id candidateId jobId currentStage status decision finalScore
      stageResults { stage passed score reasons evaluatedAt }
      failureReasons createdAt completedAt
    }
  }
`;

export const MY_SCREENING_SESSIONS_QUERY = gql`
  query MyScreeningSessions {
    myScreeningSessions {
      id
      candidateId
      jobId
      currentStage
      status
      decision
      finalScore
      stageResults { stage passed score reasons evaluatedAt }
      failureReasons
      createdAt
      completedAt
    }
  }
`;

export const START_SCREENING_MUTATION = gql`
  mutation StartScreening($candidateId: ID!, $jobId: ID!) {
    startScreening(candidateId: $candidateId, jobId: $jobId) {
      id currentStage status decision
    }
  }
`;

export const ADVANCE_SCREENING_MUTATION = gql`
  mutation AdvanceScreening($sessionId: ID!) {
    advanceScreening(sessionId: $sessionId) {
      id currentStage status decision finalScore
      stageResults { stage passed score }
    }
  }
`;

export const SCREENING_UPDATED_SUBSCRIPTION = gql`
  subscription ScreeningUpdated($sessionId: ID!) {
    screeningUpdated(sessionId: $sessionId) {
      id
      candidateId
      jobId
      currentStage
      status
      decision
      finalScore
      stageResults { stage passed score reasons evaluatedAt }
      failureReasons
      createdAt
      completedAt
    }
  }
`;

// ── Notifications ─────────────────────────────────────────────────────────────
export const MY_NOTIFICATIONS_QUERY = gql`
  query MyNotifications($unreadOnly: Boolean) {
    myNotifications(unreadOnly: $unreadOnly) {
      id type title message read createdAt
    }
  }
`;

export const NEW_NOTIFICATION_SUBSCRIPTION = gql`
  subscription NewNotification($userId: ID!) {
    newNotification(userId: $userId) {
      id
      userId
      type
      title
      message
      read
      createdAt
    }
  }
`;

export const MARK_NOTIFICATION_READ_MUTATION = gql`
  mutation MarkNotificationRead($id: ID!) {
    markNotificationRead(id: $id) {
      id
      read
    }
  }
`;

export const MARK_ALL_READ_MUTATION = gql`
  mutation MarkAllRead { markAllNotificationsRead }
`;
