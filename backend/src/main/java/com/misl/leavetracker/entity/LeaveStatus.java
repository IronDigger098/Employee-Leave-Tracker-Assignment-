package com.misl.leavetracker.entity;

/**
 * Lifecycle of a leave request.
 *
 * Allowed transitions (enforced in LeaveService):
 *   PENDING -> APPROVED
 *   PENDING -> REJECTED
 *
 * Nothing else is legal. Once a request is APPROVED or REJECTED it is final,
 * so approving an already-approved leave returns 400 Bad Request rather than
 * silently succeeding.
 */
public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED
}
