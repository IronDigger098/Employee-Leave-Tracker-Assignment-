package com.misl.leavetracker.service;

import com.misl.leavetracker.dto.DashboardResponse;
import com.misl.leavetracker.entity.LeaveStatus;
import com.misl.leavetracker.repository.EmployeeRepository;
import com.misl.leavetracker.repository.LeaveRequestRepository;
import com.misl.leavetracker.security.EmployeeUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public DashboardService(EmployeeRepository employeeRepository,
                            LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
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

        return new DashboardResponse(
                0,
                leaveRequestRepository.countByEmployeeId(employeeId),
                leaveRequestRepository.countByEmployeeIdAndStatus(employeeId, LeaveStatus.PENDING),
                leaveRequestRepository.countByEmployeeIdAndStatus(employeeId, LeaveStatus.APPROVED),
                leaveRequestRepository.countByEmployeeIdAndStatus(employeeId, LeaveStatus.REJECTED));
    }
}
