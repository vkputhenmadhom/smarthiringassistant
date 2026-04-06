import { gql } from '@apollo/client';

export const DASHBOARD_METRICS_QUERY = gql`
  query MyCandidateDashboard {
    myCandidateDashboard {
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

