/**
 * Types for leave requests. Mirrors LeaveRequestDto.java / LeaveResponse.java.
 */

/** Must match the LeaveType enum in the backend. */
export type LeaveType = 'CASUAL' | 'SICK' | 'ANNUAL';

/** Must match the LeaveStatus enum in the backend. */
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/**
 * A leave request as returned by the API.
 *
 * Dates arrive as ISO strings, not JavaScript Date objects - JSON has no date
 * type, so `startDate` is the string "2026-09-10". Typing it as `string` is
 * honest about that. Angular's DatePipe formats these strings directly in the
 * template, so there is no need to convert them.
 */
export interface LeaveRequest {
  id: number;
  employeeId: number;
  employeeName: string;
  employeeCode: string;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  reason: string;
  status: LeaveStatus;
  createdAt: string;
  reviewedAt: string | null;
}

/**
 * What we send when submitting or editing a request.
 *
 * Deliberately missing: employeeId and status. The server takes the employee from
 * the JWT and always sets status to PENDING, so sending either would be pointless -
 * and the type makes that impossible to forget.
 */
export interface LeaveRequestPayload {
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  reason: string;
}

/**
 * The options for the leave-type dropdown, kept next to the type it belongs to.
 *
 * A matching LEAVE_STATUSES array is deliberately absent: nothing in the UI lets
 * a user pick a status. The admin filter's options are fixed in the template and
 * the status itself is only ever changed through the approve/reject endpoints.
 */
export const LEAVE_TYPES: LeaveType[] = ['CASUAL', 'SICK', 'ANNUAL'];
