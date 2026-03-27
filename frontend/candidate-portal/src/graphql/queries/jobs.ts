import { gql } from '@apollo/client';

export const JOBS_QUERY = gql`
  query Jobs($page: Int, $size: Int, $search: String) {
    jobs(page: $page, size: $size, search: $search) {
      totalElements
      totalPages
      page
      size
      content {
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
      }
    }
  }
`;

