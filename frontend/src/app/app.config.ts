import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';

import { authInterceptor } from './core/interceptors/auth.interceptor';
import { routes } from './app.routes';

/**
 * The application's root providers.
 *
 * This is the standalone-components replacement for the old AppModule. Angular
 * dropped NgModules as the default in v17; there is no @NgModule anywhere in this
 * project, and every component declares its own imports.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),

    // Makes the routes below available and enables <router-outlet>.
    provideRouter(routes),

    /*
     * Registers HttpClient application-wide AND wires our interceptor into it.
     *
     * This one line is what makes every HTTP call in the app carry the JWT.
     * Forgetting withInterceptors() is a common bug: HttpClient still works, the
     * app still compiles, and every protected request quietly returns 401.
     */
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
