import { gql } from '@apollo/client';

export const DASHBOARD_METRICS_QUERY = gql`
  query DashboardMetrics {
    dashboardMetrics {
      totalJobs
      openJobs
      totalCandidates
      activeCandidates
      pendingScreenings
      completedScreenings
      averageMatchScore
      hireRate
      topSkillsInDemand { skill count }
      recentActivity { type description timestamp }
    }
  }
`;

