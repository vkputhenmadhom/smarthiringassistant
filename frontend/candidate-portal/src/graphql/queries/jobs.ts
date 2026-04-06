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

export const JOBS_QUERY = gql`
  query Jobs($page: Int, $size: Int, $search: String) {
    jobs(page: $page, size: $size, search: $search) {
      totalElements
      totalPages
      page
      size
      content { ${JOB_FIELDS} }
    }
  }
`;

/** India government & PSU jobs — filtered server-side by source=INDIA */
export const GOVT_JOBS_QUERY = gql`
  query GovtJobs($page: Int, $size: Int, $search: String) {
    jobs(page: $page, size: $size, search: $search, source: "INDIA") {
      totalElements
      totalPages
      page
      size
      content { ${JOB_FIELDS} }
    }
  }
`;
