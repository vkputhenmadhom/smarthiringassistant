import { gql } from '@apollo/client';

const JOB_FIELDS = `
  id
  title
  department
  location
  type
  status
  skills
  salaryMin
  salaryMax
  salaryCurrency
  salaryConfidence
  applicantCount
  source
  companyName
  externalUrl
  postedAt
  seniority
  minExperienceYears
  maxExperienceYears
`;

/** source: a feed key from JOB_FEEDS_QUERY or an exact source tag; omit for every job */
export const JOBS_QUERY = gql`
  query Jobs($page: Int, $size: Int, $search: String, $source: String) {
    jobs(page: $page, size: $size, search: $search, source: $source) {
      totalElements
      totalPages
      page
      size
      content { ${JOB_FIELDS} }
    }
  }
`;


/** The job feeds to show as tabs, in order (configured in job-analyzer's job-feeds.yml) */
export const JOB_FEEDS_QUERY = gql`
  query JobFeeds {
    jobFeeds { key label emptyMessage }
  }
`;

/** The signed-in candidate's matches from their resume, best first, with the job; score is 0–1 */
export const MY_MATCHES_QUERY = gql`
  query MyMatches($candidateId: ID!, $size: Int) {
    matchesForCandidate(candidateId: $candidateId, page: 0, size: $size) {
      totalElements
      content {
        id
        score
        skillMatches
        skillGaps
        job { ${JOB_FIELDS} }
      }
    }
  }
`;
