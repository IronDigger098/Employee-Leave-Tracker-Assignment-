import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthUser, LoginRequest, LoginResponse } from '../models/auth.model';

/**
 * Owns everything about "who is signed in".
 *
 * `providedIn: 'root'` registers this as an application-wide singleton, created
 * lazily the first time something injects it. Every component, guard and
 * interceptor that injects AuthService gets the SAME instance - which is what
 * makes the session state below shared rather than per-component.
 *
 * Laravel comparison: closest to a singleton bound in the service container.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  /**
   * inject() instead of a constructor parameter. Both work; inject() is the
   * modern form and keeps the class free of a constructor that exists only to
   * receive dependencies.
   */
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private static readonly TOKEN_KEY = 'elt.token';
  private static readonly USER_KEY = 'elt.user';

  /**
   * A signal holding the current user, or null when signed out.
   *
   * Signals are Angular's reactive state primitive: read it in a template and
   * that part of the view re-renders automatically whenever the value changes.
   * The navbar uses this to swap between "Login" and "Logout" with no manual
   * subscription and nothing to unsubscribe.
   *
   * It is seeded from localStorage so a page refresh does not sign the user out.
   */
  private readonly userSignal = signal<AuthUser | null>(this.readStoredUser());

  /**
   * Exposed read-only. Components can read currentUser() but cannot call .set()
   * on it - only this service may change the session.
   */
  readonly currentUser = this.userSignal.asReadonly();

  /** Derived signals: recomputed automatically when currentUser changes. */
  readonly isLoggedIn = computed(() => this.userSignal() !== null);
  readonly isAdmin = computed(() => this.userSignal()?.role === 'ADMIN');

  /**
   * POST /api/auth/login
   *
   * Returns an Observable, so nothing happens until a component subscribes -
   * Observables are lazy, unlike Promises which start immediately. `tap` runs the
   * session-storing side effect as the response passes through, without changing
   * what the caller receives.
   *
   * Note the URL is relative. In Docker, nginx serves the app and proxies /api to
   * the backend, so the browser only ever talks to one origin - which is why this
   * project needs no CORS configuration at all. During `ng serve`,
   * proxy.conf.json does the same job.
   */
  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('/api/auth/login', credentials)
      .pipe(tap((response) => this.storeSession(response)));
  }

  /** Clears the session and returns to the login page. */
  logout(): void {
    this.clearSession();
    this.router.navigate(['/login']);
  }

  /**
   * Clears the session WITHOUT navigating.
   *
   * Used by the HTTP interceptor when the server answers 401: the token is dead,
   * so the stored session must go, but the interceptor decides separately where
   * to send the user.
   */
  clearSession(): void {
    localStorage.removeItem(AuthService.TOKEN_KEY);
    localStorage.removeItem(AuthService.USER_KEY);
    this.userSignal.set(null);
  }

  /** Read by the HTTP interceptor to build the Authorization header. */
  getToken(): string | null {
    return localStorage.getItem(AuthService.TOKEN_KEY);
  }

  /** Where a user belongs after logging in, based on their role. */
  homeRoute(): string {
    return this.isAdmin() ? '/admin/dashboard' : '/employee/dashboard';
  }

  private storeSession(response: LoginResponse): void {
    const user: AuthUser = {
      employeeId: response.employeeId,
      name: response.name,
      email: response.email,
      role: response.role,
    };
    localStorage.setItem(AuthService.TOKEN_KEY, response.token);
    localStorage.setItem(AuthService.USER_KEY, JSON.stringify(user));
    this.userSignal.set(user);
  }

  /**
   * Restores the session on a page reload.
   *
   * Wrapped in try/catch because localStorage can hold anything - a half-written
   * value, or something a user typed into devtools. Corrupt JSON should mean
   * "signed out", never a crash on startup.
   */
  private readStoredUser(): AuthUser | null {
    const raw = localStorage.getItem(AuthService.USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AuthUser;
    } catch {
      // Cleared directly rather than via clearSession(): this method runs while
      // userSignal is still being initialised, so calling a method that touches
      // the signal would blow up on an undefined field.
      localStorage.removeItem(AuthService.TOKEN_KEY);
      localStorage.removeItem(AuthService.USER_KEY);
      return null;
    }
  }
}
