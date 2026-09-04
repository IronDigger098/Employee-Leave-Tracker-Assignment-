import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Attaches the JWT to every outgoing request, and reacts to 401 responses.
 *
 * This is the frontend mirror of JwtAuthenticationFilter on the server: one place
 * that deals with the token, so no service and no component ever mentions it.
 * Without this, every single API call would need to build its own headers.
 *
 * It is a FUNCTION, not a class. Angular's older HTTP_INTERCEPTORS took classes;
 * since v15 an interceptor is a plain function registered with
 * withInterceptors([...]), and inject() works inside it. Less ceremony, and it
 * tree-shakes.
 *
 * Laravel comparison: middleware, but on the outgoing side - closest to a
 * Guzzle/Http client middleware that stamps a header on every request.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.getToken();

  /*
   * HttpRequest is immutable - you cannot assign to req.headers. clone() returns a
   * modified copy, which is what we pass along. That immutability is deliberate:
   * it means a retried request is always identical to the original.
   */
  const request = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      /*
       * 401 means the token is missing, expired or invalid. Clear the dead
       * session and send the user to the login page.
       *
       * The isLoginRequest guard matters: a failed login ALSO returns 401, and
       * without this check a wrong password would trigger a navigation to
       * /login that wipes the error message before the user can read it.
       */
      const isLoginRequest = req.url.includes('/api/auth/login');

      if (error.status === 401 && !isLoginRequest) {
        authService.clearSession();
        router.navigate(['/login']);
      }

      /*
       * Re-throw so the component still sees the error and can show a message.
       * Swallowing it here would leave a page spinning forever with no
       * explanation. 403s are passed straight through - the user is signed in
       * correctly, they simply are not allowed to do that.
       */
      return throwError(() => error);
    }),
  );
};
