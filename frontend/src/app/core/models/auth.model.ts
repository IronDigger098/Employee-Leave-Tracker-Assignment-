/**
 * Types for authentication.
 *
 * These interfaces mirror the backend DTOs exactly - LoginRequest matches
 * LoginRequest.java, LoginResponse matches LoginResponse.java. Keeping the shapes
 * identical means TypeScript catches a mismatch at compile time instead of
 * leaving it to show up as `undefined` on screen.
 *
 * `interface` rather than `class` on purpose: these describe the SHAPE of JSON
 * that arrives over HTTP. They vanish at compile time and generate no JavaScript.
 * A class would suggest the objects have behaviour and could be `new`-ed, which
 * would be misleading - HttpClient never constructs your class, it just hands you
 * the parsed JSON and trusts the type you declared.
 */

/** Must match the Role enum in the backend. */
export type Role = 'ADMIN' | 'EMPLOYEE';

/** POST /api/auth/login request body. */
export interface LoginRequest {
  email: string;
  password: string;
}

/** POST /api/auth/login 200 response. */
export interface LoginResponse {
  token: string;
  employeeId: number;
  name: string;
  email: string;
  role: Role;
}

/**
 * What we keep in memory (and localStorage) about the signed-in user.
 *
 * The token is stored separately, so this object can be read freely by
 * components that need the name or role for display without touching the credential.
 */
export interface AuthUser {
  employeeId: number;
  name: string;
  email: string;
  role: Role;
}

/**
 * The error shape every failing endpoint returns - matches ApiError.java.
 * One shape for all errors means one piece of error-handling code in the UI.
 */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  /** Present only on validation failures: { fieldName: message }. */
  fieldErrors?: Record<string, string>;
}
