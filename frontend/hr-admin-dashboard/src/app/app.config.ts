import { ApplicationConfig } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';
import { provideRouterStore } from '@ngrx/router-store';
import { InMemoryCache } from '@apollo/client/core';
import { Apollo, APOLLO_OPTIONS } from 'apollo-angular';
import { HttpLink } from 'apollo-angular/http';

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
      useFactory: (httpLink: HttpLink) => ({
        cache: new InMemoryCache(),
        link: httpLink.create({ uri: environment.graphqlUrl }),
        defaultOptions: {
          watchQuery: { fetchPolicy: 'cache-and-network' },
        },
      }),
      deps: [HttpLink],
    },
    Apollo,
  ],
};

