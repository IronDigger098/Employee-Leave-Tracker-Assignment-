package com.misl.leavetracker.repository;

import com.misl.leavetracker.entity.LeaveRequest;
import com.misl.leavetracker.entity.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link LeaveRequest}.
 */
@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /**
     * Admin list: every leave request, newest first, WITH the employee already loaded.
     *
     * Why a hand-written @Query instead of the derived findAllByOrderByCreatedAtDesc():
     * LeaveRequest.employee is LAZY, so a plain findAll() returns 20 rows and then fires
     * one extra SELECT per row the moment we read employee.getName() while mapping to
     * the DTO - the classic N+1 problem (1 query for the list + N for the associations).
     *
     * JOIN FETCH tells Hibernate to load the employees in the SAME query, so 20 rows
     * cost 1 query instead of 21. With show-sql enabled you can confirm it: this
     * method produces a single SELECT with a join, not a burst of them.
     */
    @Query("SELECT l FROM LeaveRequest l JOIN FETCH l.employee ORDER BY l.createdAt DESC")
    List<LeaveRequest> findAllWithEmployee();

    /** Same idea for a single row, used by GET /api/leaves/{id}. */
    @Query("SELECT l FROM LeaveRequest l JOIN FETCH l.employee WHERE l.id = :id")
    Optional<LeaveRequest> findByIdWithEmployee(Long id);

    /**
     * One employee's own requests, newest first.
     *
     * Derived query: Spring Data reads "EmployeeId" as a walk into the association
     * (leaveRequest.employee.id), so this becomes WHERE employee_id = ?.
     * No JOIN FETCH needed here - every row belongs to the same employee, so Hibernate
     * loads that employee once and serves the rest from the persistence context.
     */
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    /** Dashboard counters (used in Phase 4). */
    long countByStatus(LeaveStatus status);

    long countByEmployeeId(Long employeeId);

    long countByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);
}
