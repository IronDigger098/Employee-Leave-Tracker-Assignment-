package com.misl.leavetracker.dto;

/**
 * Counters for a dashboard screen.
 *
 * One class serves both dashboards because the shape is identical - four numbers.
 * The difference is only in scope, and the endpoint says which:
 *   GET /api/dashboard/admin     -> counts across all employees
 *   GET /api/dashboard/employee  -> counts for the caller only
 *
 * totalEmployees is 0 on the employee dashboard, where it has no meaning.
 * Two near-identical DTOs would have been more code saying the same thing.
 */
public class DashboardResponse {

    private long totalEmployees;
    private long totalLeaves;
    private long pendingLeaves;
    private long approvedLeaves;
    private long rejectedLeaves;

    public DashboardResponse(long totalEmployees, long totalLeaves, long pendingLeaves,
                             long approvedLeaves, long rejectedLeaves) {
        this.totalEmployees = totalEmployees;
        this.totalLeaves = totalLeaves;
        this.pendingLeaves = pendingLeaves;
        this.approvedLeaves = approvedLeaves;
        this.rejectedLeaves = rejectedLeaves;
    }

    public long getTotalEmployees() {
        return totalEmployees;
    }

    public long getTotalLeaves() {
        return totalLeaves;
    }

    public long getPendingLeaves() {
        return pendingLeaves;
    }

    public long getApprovedLeaves() {
        return approvedLeaves;
    }

    public long getRejectedLeaves() {
        return rejectedLeaves;
    }
}
