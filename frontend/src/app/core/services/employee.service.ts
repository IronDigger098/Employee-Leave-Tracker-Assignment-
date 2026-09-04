import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DashboardStats, Employee, EmployeeRequest } from '../models/employee.model';

/**
 * All HTTP calls for employees and dashboard counters.
 *
 * Components never call HttpClient directly. Keeping every URL in one place means
 * a renamed endpoint is a one-line change here rather than a hunt through
 * templates, and it keeps components focused on presenting data.
 *
 * No Authorization header is set anywhere in this file - authInterceptor attaches
 * it to every outgoing request, so services stay unaware that auth exists.
 */
@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/employees';

  /** GET /api/employees - ADMIN only; a non-admin receives 403. */
  findAll(): Observable<Employee[]> {
    return this.http.get<Employee[]>(this.baseUrl);
  }

  findById(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${this.baseUrl}/${id}`);
  }

  create(payload: EmployeeRequest): Observable<Employee> {
    return this.http.post<Employee>(this.baseUrl, payload);
  }

  update(id: number, payload: EmployeeRequest): Observable<Employee> {
    return this.http.put<Employee>(`${this.baseUrl}/${id}`, payload);
  }

  /** DELETE returns 204 No Content, hence Observable<void>. */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /** GET /api/dashboard/admin - company-wide counters. */
  adminStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>('/api/dashboard/admin');
  }

  /** GET /api/dashboard/employee - the signed-in user's own counters. */
  employeeStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>('/api/dashboard/employee');
  }
}
