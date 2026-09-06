import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models/auth.model';
import { AuthService } from '../services/auth.service';

/**
 * Route guards - they decide whether a navigation is allowed to proceed.
 *
 * IMPORTANT: these guards are a USABILITY feature, not a security feature. All of
 * this code runs in the browser, where the user can edit it. Anyone can put a fake
 * role in localStorage and reach /admin/employees.
 *
 * What stops them is the SERVER: every endpoint that page calls is protected by
 * @PreAuthorize, so a faked admin lands on a screen where every request returns
 * 403 and no data ever appears. The guard's real job is to stop an ordinary user
 * from wandering into a page that would only show them errors.
 *
 * A guard returns true (allow), false (block), or a UrlTree (redirect).
 * Returning a UrlTree is better than calling router.navigate() and returning
 * false: it is a single atomic "go there instead" rather than a cancelled
 * navigation followed by a new one.
 */

/** Blocks anyone who is not signed in, sending them to the login page. */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }
  return router.createUrlTree(['/login']);
};

/**
 * Blocks anyone whose role is not the one required.
 *
 * A factory rather than a plain guard, so the route can say which role it wants:
 *
 *     canActivate: [authGuard, roleGuard('ADMIN')]
 *
 * Someone signed in with the wrong role is bounced to their own dashboard
 * rather than to /login - they are authenticated, just in the wrong place, and
 * throwing them back to a login screen would be confusing.
 */
export const roleGuard = (required: Role): CanActivateFn => {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    const user = authService.currentUser();
    if (!user) {
      return router.createUrlTree(['/login']);
    }
    if (user.role === required) {
      return true;
    }
    return router.createUrlTree([authService.homeRoute()]);
  };
};

/**
 * The reverse: keeps an already-signed-in user off the login page, sending them
 * to their dashboard instead. Without it, pressing Back after logging in shows
 * the login form again, which feels broken.
 */
export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    return true;
  }
  return router.createUrlTree([authService.homeRoute()]);
};
