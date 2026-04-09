import { ApplicationConfig } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';
import { provideRouterStore } from '@ngrx/router-store';
import { ApolloLink, InMemoryCache, split } from '@apollo/client/core';
import { GraphQLWsLink } from '@apollo/client/link/subscriptions';
import { getMainDefinition } from '@apollo/client/utilities';
import { Apollo, APOLLO_OPTIONS } from 'apollo-angular';
import { HttpLink } from 'apollo-angular/http';
import { createClient } from 'graphql-ws';

import { appRoutes } from './app.routes';
import { jwtInterceptor } from './core/interceptors/jwt.interceptor';
import { authReducer } from './store/auth/auth.reducer';
import { jobsReducer } from './store/jobs/jobs.reducer';
import { candidatesReducer } from './store/candidates/candidates.reducer';
import { screeningReducer } from './store/screening/screening.store.reducer';
import { AuthEffects } from './store/auth/auth.effects';
import { JobsEffects } from './store/jobs/jobs.effects';
import { CandidatesEffects } from './store/candidates/candidates.effects';
import { ScreeningEffects } from './store/screening/screening.store.effects';
import { environment } from '../environments/environment';

const toAbsoluteWsUrl = (value: string): string => {
  if (value.startsWith('ws://') || value.startsWith('wss://')) {
    return value;
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const path = value.startsWith('/') ? value : `/${value}`;
  return `${protocol}//${window.location.host}${path}`;
};

const createWsLink = (): ApolloLink | null => {
  if (typeof window === 'undefined') {
    return null;
  }

  return new GraphQLWsLink(createClient({
    url: toAbsoluteWsUrl(environment.graphqlWsUrl),
    lazy: true,
    retryAttempts: 5,
    connectionParams: async () => {
      const token = localStorage.getItem('sha_token');
      return token
        ? {
            Authorization: `Bearer ${token}`,
            authorization: `Bearer ${token}`,
            authToken: `Bearer ${token}`,
          }
        : {};
    },
  }));
};

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(appRoutes, withComponentInputBinding()),
    provideAnimations(),
    provideHttpClient(withInterceptors([jwtInterceptor])),

    // NgRx
    provideStore({
      auth:       authReducer,
      jobs:       jobsReducer,
      candidates: candidatesReducer,
      screening:  screeningReducer,
    }),
    provideEffects([AuthEffects, JobsEffects, CandidatesEffects, ScreeningEffects]),
    provideStoreDevtools({ maxAge: 25, logOnly: environment.production }),
    provideRouterStore(),

    // Apollo GraphQL
    {
      provide: APOLLO_OPTIONS,
      useFactory: (httpLink: HttpLink) => {
        const http = httpLink.create({ uri: environment.graphqlUrl });
        const ws = createWsLink();
        const link = ws
          ? split(
              ({ query }) => {
                const definition = getMainDefinition(query);
                return definition.kind === 'OperationDefinition' && definition.operation === 'subscription';
              },
              ws,
              http
            )
          : http;

        return {
          cache: new InMemoryCache(),
          link,
          defaultOptions: {
            watchQuery: { fetchPolicy: 'cache-and-network' },
          },
        };
      },
      deps: [HttpLink],
    },
    Apollo,
  ],
};
