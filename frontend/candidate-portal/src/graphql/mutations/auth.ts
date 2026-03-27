import { gql } from '@apollo/client';

export const LOGIN_MUTATION = gql`
  mutation Login($username: String!, $password: String!) {
    login(username: $username, password: $password) {
      token
      refreshToken
      expiresIn
      user { id username email role }
    }
  }
`;

export const REGISTER_MUTATION = gql`
  mutation Register($username: String!, $email: String!, $password: String!, $role: Role!) {
    register(username: $username, email: $email, password: $password, role: $role) {
      token
      refreshToken
      expiresIn
      user { id username email role }
    }
  }
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

