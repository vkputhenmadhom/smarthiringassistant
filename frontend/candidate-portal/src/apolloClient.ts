import { ApolloClient, InMemoryCache, createHttpLink, from, split } from '@apollo/client';
import { setContext } from '@apollo/client/link/context';
import { onError } from '@apollo/client/link/error';
import { fromPromise } from '@apollo/client';
import { GraphQLWsLink } from '@apollo/client/link/subscriptions';
import { getMainDefinition } from '@apollo/client/utilities';
import { createClient } from 'graphql-ws';

let refreshInFlight: Promise<string | null> | null = null;

const refreshTokenSilently = async (): Promise<string | null> => {
  if (refreshInFlight) {
    return refreshInFlight;
  }

  const refreshToken = localStorage.getItem('sha_refresh_token');
  if (!refreshToken) {
    return null;
  }

  refreshInFlight = (async () => {
    try {
      const res = await fetch(graphqlHttpUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          query: `mutation RefreshToken($refreshToken: String!) {
            refreshToken(refreshToken: $refreshToken) {
              token
              refreshToken
              expiresIn
              user { id username email role }
            }
          }`,
          variables: { refreshToken },
        }),
      });

      const json = await res.json();
      const payload = json?.data?.refreshToken;
      if (!payload?.token) {
        return null;
      }

      const expiresAt = Date.now() + (payload.expiresIn * 1000);
      localStorage.setItem('sha_token', payload.token);
      localStorage.setItem('sha_refresh_token', payload.refreshToken);
      localStorage.setItem('sha_expires_at', String(expiresAt));
      localStorage.setItem('sha_user', JSON.stringify(payload.user));
      return payload.token as string;
    } catch {
      return null;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
};

const getValidToken = async (): Promise<string | null> => {
  let token = localStorage.getItem('sha_token');
  const expiresAt = Number(localStorage.getItem('sha_expires_at') ?? '') || 0;

  if (token && expiresAt && expiresAt <= Date.now() + 30_000) {
    token = await refreshTokenSilently();
  }

  return token;
};

const toAbsoluteWsUrl = (value: string): string => {
  if (value.startsWith('ws://') || value.startsWith('wss://')) {
    return value;
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const path = value.startsWith('/') ? value : `/${value}`;
  return `${protocol}//${window.location.host}${path}`;
};

const graphqlHttpUrl = (import.meta.env.VITE_GRAPHQL_HTTP_URL as string | undefined) ?? '/graphql';
const graphqlWsUrl = (import.meta.env.VITE_GRAPHQL_WS_URL as string | undefined) ?? '/graphql-ws';

const httpLink = createHttpLink({ uri: graphqlHttpUrl });

const authLink = setContext(async (operation, { headers }) => {
  const token = operation.operationName !== 'RefreshToken'
    ? await getValidToken()
    : localStorage.getItem('sha_token');

  return {
    headers: {
      ...headers,
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
  };
});

const errorLink = onError(({ graphQLErrors, networkError, operation, forward }) => {
  const unauthenticatedGraphql =
    !!graphQLErrors?.some(err => err.extensions?.code === 'UNAUTHENTICATED');
  const unauthenticatedNetwork = (networkError as any)?.statusCode === 401;

  if (!unauthenticatedGraphql && !unauthenticatedNetwork) {
    return;
  }

  if (operation.operationName === 'RefreshToken' || operation.getContext().alreadyRetried) {
    return;
  }

  operation.setContext({ alreadyRetried: true });

  return fromPromise(refreshTokenSilently()).flatMap(newToken => {
    if (!newToken) {
      localStorage.removeItem('sha_token');
      localStorage.removeItem('sha_refresh_token');
      localStorage.removeItem('sha_expires_at');
      localStorage.removeItem('sha_user');
      return forward(operation);
    }

    operation.setContext(({ headers = {} }) => ({
      headers: {
        ...headers,
        authorization: `Bearer ${newToken}`,
      },
    }));

    return forward(operation);
  });
});

const httpChain = from([errorLink, authLink, httpLink]);
const wsLink = typeof window !== 'undefined'
  ? new GraphQLWsLink(createClient({
      url: toAbsoluteWsUrl(graphqlWsUrl),
      lazy: true,
      retryAttempts: 5,
      connectionParams: async () => {
        const token = await getValidToken();
        return token
          ? {
              Authorization: `Bearer ${token}`,
              authorization: `Bearer ${token}`,
              authToken: `Bearer ${token}`,
            }
          : {};
      },
    }))
  : null;

export const apolloClient = new ApolloClient({
  link: wsLink
    ? split(
        ({ query }) => {
          const definition = getMainDefinition(query);
          return definition.kind === 'OperationDefinition' && definition.operation === 'subscription';
        },
        wsLink,
        httpChain
      )
    : httpChain,
  cache: new InMemoryCache(),
  defaultOptions: {
    watchQuery: { fetchPolicy: 'cache-and-network' },
  },
});
