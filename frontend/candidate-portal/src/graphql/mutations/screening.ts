import { gql } from '@apollo/client';

export const START_SCREENING_MUTATION = gql`
  mutation StartScreening($candidateId: ID!, $jobId: ID!) {
    startScreening(candidateId: $candidateId, jobId: $jobId) {
      id
      candidateId
      jobId
      currentStage
      status
      decision
    }
  }
`;

export const SUBMIT_SCREENING_RESPONSE_MUTATION = gql`
  mutation SubmitScreeningResponse($sessionId: ID!, $stage: String!, $response: String!) {
    submitScreeningResponse(sessionId: $sessionId, stage: $stage, response: $response) {
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

