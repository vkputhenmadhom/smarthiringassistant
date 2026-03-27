import { gql } from '@apollo/client';

export const SCREENING_SESSION_QUERY = gql`
  query ScreeningSession($id: ID!) {
    screeningSession(id: $id) {
      id
      candidateId
      jobId
      currentStage
      status
      decision
      finalScore
      stageResults {
        stage
        passed
        score
        reasons
        evaluatedAt
      }
      failureReasons
      createdAt
      completedAt
    }
  }
`;

