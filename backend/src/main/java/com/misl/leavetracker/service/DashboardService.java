package com.misl.leavetracker.service;

import com.misl.leavetracker.dto.DashboardResponse;
import com.misl.leavetracker.entity.LeaveStatus;
import com.misl.leavetracker.repository.EmployeeRepository;
import com.misl.leavetracker.repository.LeaveRequestRepository;
import com.misl.leavetracker.security.EmployeeUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

/**
 * Summary counters for the two dashboard screens.
 *
 * Every number comes from a COUNT query rather than loading the rows and calling
 * .size() in Java. The database counts rows far better than we can, and the count
 * travels over the wire as one integer instead of a full result set.
 */
@Service
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    /**
     * Injected rather than recomputing the balance here.
     *
     * "How many days has this employee used" is one rule with one definition -
     * which statuses count, how a day is measured, how the year is decided. It
     * lives in LeaveService, and this class asks for the answer instead of
     * reimplementing it. Two copies of that logic would eventually disagree, and
     * the dashboard would show a number the validator did not believe.
     */
    private final LeaveService leaveService;

    public DashboardService(EmployeeRepository employeeRepository,
                            LeaveRequestRepository leaveRequestRepository,
                            LeaveService leaveService) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveService = leaveService;
    }

    /** Company-wide figures for the admin dashboard. */
    @Transactional(readOnly = true)
    public DashboardResponse getAdminDashboard() {
        long pending = leaveRequestRepository.countByStatus(LeaveStatus.PENDING);
        long approved = leaveRequestRepository.countByStatus(LeaveStatus.APPROVED);
        long rejected = leaveRequestRepository.countByStatus(LeaveStatus.REJECTED);

        return new DashboardResponse(
                employeeRepository.count(),
                pending + approved + rejected,
                pending,
                approved,
                rejected);
    }

    /**
     * The caller's own figures.
     *
     * Scoped by the id from the token, not from a parameter - so this endpoint
     * cannot be pointed at another employee's numbers.
     */
    @Transactional(readOnly = true)
    public DashboardResponse getEmployeeDashboard(EmployeeUserDetails currentUser) {
        Long employeeId = currentUser.getId();

        /*
         * The balance is always for the CURRENT calendar year, matching the year
         * a new request would be validated against.
         */
        int entitlement = leaveService.getAnnualEntitlementDays();
        long used = leaveService.usedLeaveDays(employeeId, Year.now().getValue());

        return new DashboardResponse(
                0,
                leaveRequestRepository.countByEmployeeId(employeeId),
                leaveRequestRepository.countByEmployeeIdAndStatus(employeeId, LeaveStatus.PENDING),
                leaveRequestRepository.countByEmployeeIdAndStatus(employeeId, LeaveStatus.APPROVED),
                leaveRequestRepository.countByEmployeeIdAndStatus(employeeId, LeaveStatus.REJECTED),
                entitlement,
                used,
                // Never show a negative balance: an admin could in principle approve
                // beyond the limit, and "-3 days remaining" reads as a bug.
                Math.max(entitlement - used, 0));
    }
}
