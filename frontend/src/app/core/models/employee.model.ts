import { Role } from './auth.model';

/**
 * Types for employees. Mirrors EmployeeRequest.java / EmployeeResponse.java.
 */

/**
 * What the server sends back. Note there is no `password` field - the backend's
 * EmployeeResponse does not contain one, so the frontend has no way to display a
 * hash even by accident.
 */
export interface Employee {
  id: number;
  employeeCode: string;
  name: string;
  email: string;
  department: string;
  designation: string;
  role: Role;
  active: boolean;
}

/**
 * What we send when creating or updating.
 *
 * `password?` is optional for the same reason it is optional on the server: it is
 * required when creating an employee, but leaving it blank on an update means
 * "keep the existing password". The `?` makes that rule visible in the type.
 */
export interface EmployeeRequest {
  employeeCode: string;
  name: string;
  email: string;
  password?: string;
  department: string;
  designation: string;
  role: Role;
  active: boolean;
}

/** GET /api/dashboard/admin and /api/dashboard/employee - matches DashboardResponse.java. */
export interface DashboardStats {
  totalEmployees: number;
  totalLeaves: number;
  pendingLeaves: number;
  approvedLeaves: number;
  rejectedLeaves: number;
}
