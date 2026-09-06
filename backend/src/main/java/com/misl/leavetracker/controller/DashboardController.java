package com.misl.leavetracker.controller;

import com.misl.leavetracker.dto.DashboardResponse;
import com.misl.leavetracker.security.EmployeeUserDetails;
import com.misl.leavetracker.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Counters for the two dashboard screens.
 *
 * Without these, each dashboard would have to download every leave request and
 * count them in the browser - which is both slower and a privacy problem, since
 * an employee would receive rows they are not allowed to see just to compute a total.
 */
@Tag(name = "Dashboard", description = "Summary counters for the two dashboard screens.")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** GET /api/dashboard/admin - company-wide totals. ADMIN only. */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DashboardResponse> getAdminDashboard() {
        return ResponseEntity.ok(dashboardService.getAdminDashboard());
    }

    /** GET /api/dashboard/employee - the caller's own totals. Any authenticated user. */
    @GetMapping("/employee")
    public ResponseEntity<DashboardResponse> getEmployeeDashboard(
            @AuthenticationPrincipal EmployeeUserDetails currentUser) {
        return ResponseEntity.ok(dashboardService.getEmployeeDashboard(currentUser));
    }
}
