package com.misl.leavetracker.repository;

import com.misl.leavetracker.entity.LeaveRequest;
import com.misl.leavetracker.entity.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
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

    /**
     * Every request by one employee, in one calendar year, that counts against
     * their annual entitlement.
     *
     * Derived query again, and the name says the whole WHERE clause:
     *   EmployeeId       -> employee_id = ?
     *   StatusIn         -> status IN (?, ?)
     *   StartDateBetween -> start_date BETWEEN ? AND ?
     *
     * PENDING is included alongside APPROVED on purpose. If only approved days
     * counted, an employee could submit ten overlapping requests while none had
     * been reviewed and blow straight past the limit - the check would only bite
     * later, and the problem would land on the admin instead.
     */
    List<LeaveRequest> findByEmployeeIdAndStatusInAndStartDateBetween(
            Long employeeId, Collection<LeaveStatus> statuses, LocalDate from, LocalDate to);

    /**
     * Requests by this employee whose dates clash with the range [start, end].
     *
     * The overlap test is the standard one, and it is easier to trust by writing
     * down when two ranges DO NOT overlap: either the existing one finishes before
     * the new one starts, or it begins after the new one ends. Negate that and you
     * get the condition below:
     *
     *     existing.startDate <= newEnd  AND  existing.endDate >= newStart
     *
     * Both comparisons are inclusive, so ranges that merely touch - one ending on
     * the same day the next begins - still count as a clash. That is correct here:
     * an employee cannot be on two different leaves on the same day.
     *
     * Written as an explicit @Query rather than a derived method name. The derived
     * equivalent would be
     * findByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual,
     * which is technically fine and practically unreadable.
     */
    @Query("""
            SELECT l FROM LeaveRequest l
            WHERE l.employee.id = :employeeId
              AND l.status IN :statuses
              AND l.startDate <= :end
              AND l.endDate >= :start
            """)
    List<LeaveRequest> findOverlapping(@Param("employeeId") Long employeeId,
                                       @Param("statuses") Collection<LeaveStatus> statuses,
                                       @Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    /** Dashboard counters (used in Phase 4). */
    long countByStatus(LeaveStatus status);

    long countByEmployeeId(Long employeeId);

    long countByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);
}
