import { gql } from '@apollo/client';

export const MY_NOTIFICATIONS_QUERY = gql`
  query MyNotifications($unreadOnly: Boolean) {
    myNotifications(unreadOnly: $unreadOnly) {
      id
      userId
      type
      title
      message
      read
      createdAt
    }
  }
`;

export const NEW_NOTIFICATION_SUBSCRIPTION = gql`
  subscription NewNotification($userId: ID!) {
    newNotification(userId: $userId) {
      id
      userId
      type
      title
      message
      read
      createdAt
    }
  }
`;

