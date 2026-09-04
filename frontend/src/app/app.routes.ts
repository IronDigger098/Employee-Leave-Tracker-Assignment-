import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/guards/auth.guard';

/**
 * The route table.
 *
 * Two things to notice:
 *
 * 1. `loadComponent` instead of a direct import. This is LAZY LOADING: the
 *    browser downloads a page's JavaScript only when the user actually navigates
 *    to it. The initial bundle stays small, and an employee never downloads the
 *    admin screens at all.
 *
 * 2. Guards run in array order, and every guard must pass. So
 *    [authGuard, roleGuard('ADMIN')] reads exactly as intended: signed in first,
 *    then an admin.
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'login',
  },
  {
    path: 'login',
    // guestGuard bounces an already-signed-in user to their dashboard.
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },

  // ---------- ADMIN ----------
  {
    path: 'admin/dashboard',
    canActivate: [authGuard, roleGuard('ADMIN')],
    loadComponent: () =>
      import('./pages/admin/admin-dashboard/admin-dashboard').then((m) => m.AdminDashboard),
  },
  {
    path: 'admin/employees',
    canActivate: [authGuard, roleGuard('ADMIN')],
    loadComponent: () =>
      import('./pages/admin/admin-employees/admin-employees').then((m) => m.AdminEmployees),
  },
  {
    path: 'admin/leaves',
    canActivate: [authGuard, roleGuard('ADMIN')],
    loadComponent: () => import('./pages/admin/admin-leaves/admin-leaves').then((m) => m.AdminLeaves),
  },

  // ---------- EMPLOYEE ----------
  {
    path: 'employee/dashboard',
    canActivate: [authGuard, roleGuard('EMPLOYEE')],
    loadComponent: () =>
      import('./pages/employee/employee-dashboard/employee-dashboard').then(
        (m) => m.EmployeeDashboard,
      ),
  },
  {
    /*
     * Declared BEFORE 'employee/leaves' for readability. Angular's router matches
     * the more specific path regardless of order here, since neither pattern
     * contains a wildcard - but keeping the specific one first is a habit worth
     * having, because with a ':id' parameter the order would genuinely matter.
     */
    path: 'employee/leaves/new',
    canActivate: [authGuard, roleGuard('EMPLOYEE')],
    loadComponent: () =>
      import('./pages/employee/leave-form/leave-form').then((m) => m.LeaveForm),
  },
  {
    path: 'employee/leaves',
    canActivate: [authGuard, roleGuard('EMPLOYEE')],
    loadComponent: () =>
      import('./pages/employee/employee-leaves/employee-leaves').then((m) => m.EmployeeLeaves),
  },

  /*
   * Wildcard - must be LAST. Angular matches routes top to bottom and stops at
   * the first hit, so a '**' placed earlier would swallow every route below it.
   */
  {
    path: '**',
    redirectTo: 'login',
  },
];
