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
