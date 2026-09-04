import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { LeaveRequest, LeaveRequestPayload } from '../models/leave.model';

/**
 * All HTTP calls for leave requests.
 */
@Injectable({ providedIn: 'root' })
export class LeaveService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/leaves';

  /** GET /api/leaves - every request in the system. ADMIN only. */
  findAll(): Observable<LeaveRequest[]> {
    return this.http.get<LeaveRequest[]>(this.baseUrl);
  }

  /**
   * GET /api/leaves/my - the signed-in user's own requests.
   *
   * No id is sent. The server reads it from the JWT, so this call cannot be
   * pointed at anyone else's data by editing the request.
   */
  findMine(): Observable<LeaveRequest[]> {
    return this.http.get<LeaveRequest[]>(`${this.baseUrl}/my`);
  }

  findById(id: number): Observable<LeaveRequest> {
    return this.http.get<LeaveRequest>(`${this.baseUrl}/${id}`);
  }

  /** POST /api/leaves - always created as PENDING, always owned by the caller. */
  create(payload: LeaveRequestPayload): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(this.baseUrl, payload);
  }

  /** PUT /api/leaves/{id} - own request, and only while it is still PENDING. */
  update(id: number, payload: LeaveRequestPayload): Observable<LeaveRequest> {
    return this.http.put<LeaveRequest>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /**
   * PATCH /api/leaves/{id}/approve - ADMIN only.
   *
   * The second argument to patch() is the request body. These endpoints take no
   * body at all - the URL carries the whole instruction - but HttpClient.patch()
   * requires the parameter, so we pass {}.
   */
  approve(id: number): Observable<LeaveRequest> {
    return this.http.patch<LeaveRequest>(`${this.baseUrl}/${id}/approve`, {});
  }

  /** PATCH /api/leaves/{id}/reject - ADMIN only. */
  reject(id: number): Observable<LeaveRequest> {
    return this.http.patch<LeaveRequest>(`${this.baseUrl}/${id}/reject`, {});
  }
}
