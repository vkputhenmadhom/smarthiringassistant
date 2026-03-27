import { gql } from '@apollo/client';

export const UPLOAD_RESUME_MUTATION = gql`
  mutation UploadResume($fileName: String!, $contentBase64: String!, $contentType: String) {
    uploadResume(fileName: $fileName, contentBase64: $contentBase64, contentType: $contentType) {
      id
      parseStatus
    }
  }
`;

