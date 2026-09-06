package com.misl.leavetracker.service;

import com.misl.leavetracker.dto.LeaveRequestDto;
import com.misl.leavetracker.dto.LeaveResponse;
import com.misl.leavetracker.entity.Employee;
import com.misl.leavetracker.entity.LeaveRequest;
import com.misl.leavetracker.entity.LeaveStatus;
import com.misl.leavetracker.exception.BadRequestException;
import com.misl.leavetracker.exception.ResourceNotFoundException;
import com.misl.leavetracker.repository.EmployeeRepository;
import com.misl.leavetracker.repository.LeaveRequestRepository;
import com.misl.leavetracker.security.EmployeeUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Business logic for leave requests.
 *
 * Five kinds of rule live here, none of which an annotation can express:
 *   1. endDate must not be before startDate            (spans two fields)
 *   2. only a PENDING request may be edited or reviewed (depends on stored state)
 *   3. an employee may only touch their OWN requests    (depends on who is asking)
 *   4. a request may not overlap another the employee holds (depends on other rows)
 *   5. the annual entitlement may not be exceeded       (depends on other rows)
 *
 * Rule 3 is the one that matters most for the assessment. @PreAuthorize on the
 * controller can express "must be an ADMIN", but it cannot express "must be the
 * owner of row 7" - that requires loading row 7 first. So role checks sit on the
 * controller and ownership checks sit here, right next to the data they protect.
 */
@Service
public class LeaveService {

    /**
     * A request in either of these states occupies part of the employee's
     * entitlement. REJECTED does not - a refused request costs nothing.
     */
    private static final Set<LeaveStatus> STATUSES_THAT_CONSUME_ENTITLEMENT =
            EnumSet.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final int annualEntitlementDays;

    /**
     * The company's timezone, used to decide what "today" is.
     *
     * NOT the container's. A Docker container with no TZ set runs on UTC, so a
     * bare LocalDate.now() in Dhaka (UTC+6) still reports YESTERDAY between
     * midnight and 6am - which let an employee book leave for a day that had
     * already passed. "Today" for a leave policy is a business fact about where
     * the company is, so it is configured rather than inherited from the host.
     */
    private final ZoneId zone;

    public LeaveService(LeaveRequestRepository leaveRequestRepository,
                        EmployeeRepository employeeRepository,
                        @Value("${app.leave.annual-entitlement-days}") int annualEntitlementDays,
                        @Value("${app.timezone}") String timezone) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.annualEntitlementDays = annualEntitlementDays;
        // Throws at startup on a bad value, which is where a misconfiguration belongs.
        this.zone = ZoneId.of(timezone);
    }

    /** Exposed so the dashboard can show the entitlement alongside days used. */
    public int getAnnualEntitlementDays() {
        return annualEntitlementDays;
    }

    /** Today in the company's timezone - never the container's. */
    public LocalDate today() {
        return LocalDate.now(zone);
    }

    /** Admin view: every request, newest first. JOIN FETCH avoids the N+1 problem. */
    @Transactional(readOnly = true)
    public List<LeaveResponse> findAll() {
        return toResponseList(leaveRequestRepository.findAllWithEmployee());
    }

    /**
     * The caller's own requests.
     *
     * There is no id parameter a client could tamper with - the id comes from the
     * authenticated principal, so "my leaves" cannot be turned into "his leaves"
     * by editing a URL.
     */
    @Transactional(readOnly = true)
    public List<LeaveResponse> findMyLeaves(EmployeeUserDetails currentUser) {
        return toResponseList(
                leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(currentUser.getId()));
    }

    /** One request. Admins see any; an employee sees only their own. */
    @Transactional(readOnly = true)
    public LeaveResponse findById(Long id, EmployeeUserDetails currentUser) {
        LeaveRequest leaveRequest = getLeaveOrThrow(id);
        checkOwnership(leaveRequest, currentUser);
        return toResponse(leaveRequest);
    }

    /**
     * Submit a new request. Always starts PENDING, always belongs to the caller.
     *
     * Compare this with Phase 3, where employeeId arrived as a query parameter.
     * Now it comes from the token, so an employee cannot file leave in a
     * colleague's name.
     */
    @Transactional
    public LeaveResponse create(LeaveRequestDto dto, EmployeeUserDetails currentUser) {
        validateDates(dto);
        checkNoOverlap(currentUser.getId(), dto, null);
        checkAnnualEntitlement(currentUser.getId(), dto, null);

        Employee employee = employeeRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", currentUser.getId()));

        LeaveRequest leaveRequest = new LeaveRequest(
                employee,
                dto.getLeaveType(),
                dto.getStartDate(),
                dto.getEndDate(),
                dto.getReason());

        return toResponse(leaveRequestRepository.save(leaveRequest));
    }

    /**
     * Edit a request that has not been reviewed.
     *
     * Two guards, in this order: ownership first (are you allowed to see this row
     * at all?), then state (is it still editable?). Checking state first would let
     * a stranger learn from the error message whether someone else's request had
     * been approved.
     */
    @Transactional
    public LeaveResponse update(Long id, LeaveRequestDto dto, EmployeeUserDetails currentUser) {
        LeaveRequest leaveRequest = getLeaveOrThrow(id);
        checkOwnership(leaveRequest, currentUser);

        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new BadRequestException("Only a PENDING leave request can be edited. "
                    + "This request is already " + leaveRequest.getStatus());
        }
        validateDates(dto);
        checkNoOverlap(leaveRequest.getEmployee().getId(), dto, leaveRequest.getId());
        checkAnnualEntitlement(leaveRequest.getEmployee().getId(), dto, leaveRequest.getId());

        leaveRequest.setLeaveType(dto.getLeaveType());
        leaveRequest.setStartDate(dto.getStartDate());
        leaveRequest.setEndDate(dto.getEndDate());
        leaveRequest.setReason(dto.getReason());

        return toResponse(leaveRequestRepository.save(leaveRequest));
    }

    /**
     * Withdraw a request.
     *
     * An admin may delete any request. An employee may withdraw only their own,
     * and only while it is still PENDING - deleting an approved leave would erase
     * a decision from the record.
     */
    @Transactional
    public void delete(Long id, EmployeeUserDetails currentUser) {
        LeaveRequest leaveRequest = getLeaveOrThrow(id);
        checkOwnership(leaveRequest, currentUser);

        if (!currentUser.isAdmin() && leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new BadRequestException("Only a PENDING leave request can be withdrawn. "
                    + "This request is already " + leaveRequest.getStatus());
        }

        leaveRequestRepository.delete(leaveRequest);
    }

    /** ADMIN only - enforced by @PreAuthorize on the controller. */
    @Transactional
    public LeaveResponse approve(Long id) {
        return review(id, LeaveStatus.APPROVED);
    }

    /** ADMIN only - enforced by @PreAuthorize on the controller. */
    @Transactional
    public LeaveResponse reject(Long id) {
        return review(id, LeaveStatus.REJECTED);
    }

    /**
     * The single state transition in this application:
     * PENDING -> APPROVED, or PENDING -> REJECTED. Nothing else is legal.
     */
    private LeaveResponse review(Long id, LeaveStatus newStatus) {
        LeaveRequest leaveRequest = getLeaveOrThrow(id);

        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new BadRequestException("Only a PENDING leave request can be reviewed. "
                    + "This request is already " + leaveRequest.getStatus());
        }

        leaveRequest.setStatus(newStatus);
        leaveRequest.setReviewedAt(LocalDateTime.now());

        return toResponse(leaveRequestRepository.save(leaveRequest));
    }

    /**
     * The privacy rule: an ADMIN may act on any request, an EMPLOYEE only on rows
     * whose employee_id equals their own.
     *
     * Throwing Spring Security's AccessDeniedException (rather than our own
     * exception type) keeps this consistent with what @PreAuthorize throws, so a
     * single handler in GlobalExceptionHandler turns both into 403.
     */
    private void checkOwnership(LeaveRequest leaveRequest, EmployeeUserDetails currentUser) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (!leaveRequest.getEmployee().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only access your own leave requests");
        }
    }

    /**
     * Date sanity, in two parts.
     *
     * A single-day leave (start == end) is valid; only end BEFORE start is rejected.
     *
     * Leave also may not begin in the past. Without this an employee could book
     * days that have already gone by - which consumes entitlement, produces
     * approvals for dates nobody can act on, and is an obvious way to quietly
     * absorb an unexplained absence after the fact.
     *
     * Today itself is allowed: applying on the morning of the day you need off is
     * reasonable, and only `isBefore` is rejected.
     *
     * Note this forbids backdating for EVERY leave type, including SICK. A real HR
     * system usually does allow retroactive sick leave, since illness is not
     * planned - that would be a per-type rule (allow SICK to start in the past,
     * perhaps within a bounded window, and forbid it for CASUAL and ANNUAL). It is
     * left as one simple rule here rather than a policy engine.
     */
    private void validateDates(LeaveRequestDto dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BadRequestException("End date must not be before start date");
        }

        LocalDate today = today();
        if (dto.getStartDate().isBefore(today)) {
            throw new BadRequestException(
                    "Leave cannot start in the past. The earliest allowed start date is " + today + ".");
        }
    }

    /**
     * Total days already committed by this employee in the given calendar year.
     *
     * A request is attributed to the year of its START date. A request that
     * straddles new year therefore counts wholly against the year it began in.
     * Splitting it across two years would be more precise, but it complicates
     * every calculation for a rare case; this rule is simple and predictable,
     * which matters more for a policy people have to reason about.
     */
    @Transactional(readOnly = true)
    public long usedLeaveDays(Long employeeId, int year) {
        return sumLeaveDays(employeeId, year, null);
    }

    private long sumLeaveDays(Long employeeId, int year, Long excludeLeaveId) {
        List<LeaveRequest> counted = leaveRequestRepository
                .findByEmployeeIdAndStatusInAndStartDateBetween(
                        employeeId,
                        STATUSES_THAT_CONSUME_ENTITLEMENT,
                        LocalDate.of(year, 1, 1),
                        LocalDate.of(year, 12, 31));

        long total = 0;
        for (LeaveRequest existing : counted) {
            /*
             * When EDITING a request, that request's own days must not be counted
             * against the employee - otherwise extending a 5-day leave by one day
             * would be measured as if they were asking for 6 days on top of the 5
             * they already hold.
             */
            if (excludeLeaveId != null && excludeLeaveId.equals(existing.getId())) {
                continue;
            }
            total += lengthInDays(existing.getStartDate(), existing.getEndDate());
        }
        return total;
    }

    /**
     * Refuses a request whose dates clash with one the employee already holds.
     *
     * Without this an employee could book 21-29 Sep and then 25 Sep-1 Oct, which is
     * nonsense twice over: they cannot be on two separate leaves on 25-29 Sep, and
     * the entitlement would be charged 9 + 7 = 16 days for what is really 11 days
     * away from work.
     *
     * That second point is why this check has to run BEFORE the entitlement check.
     * The entitlement sums the length of each request, which is only a correct
     * measure of days-off if no two requests cover the same day. Forbidding overlap
     * is what makes that arithmetic sound.
     *
     * REJECTED requests are ignored - a refused leave blocks nothing.
     *
     * @param excludeLeaveId the request being edited, or null when creating
     */
    private void checkNoOverlap(Long employeeId, LeaveRequestDto dto, Long excludeLeaveId) {
        List<LeaveRequest> clashes = leaveRequestRepository.findOverlapping(
                employeeId,
                STATUSES_THAT_CONSUME_ENTITLEMENT,
                dto.getStartDate(),
                dto.getEndDate());

        for (LeaveRequest clash : clashes) {
            // Editing a request always "overlaps itself" - that is not a conflict.
            if (excludeLeaveId != null && excludeLeaveId.equals(clash.getId())) {
                continue;
            }
            throw new BadRequestException(String.format(
                    "These dates overlap an existing %s request from %s to %s.",
                    clash.getStatus(), clash.getStartDate(), clash.getEndDate()));
        }
    }

    /**
     * Enforces the annual entitlement.
     *
     * Like the date-range and state rules, this cannot be an annotation: it depends
     * on rows already in the database, not on the incoming payload.
     *
     * @param excludeLeaveId the request being edited, or null when creating
     */
    private void checkAnnualEntitlement(Long employeeId, LeaveRequestDto dto, Long excludeLeaveId) {
        int year = dto.getStartDate().getYear();

        long alreadyUsed = sumLeaveDays(employeeId, year, excludeLeaveId);
        long requested = lengthInDays(dto.getStartDate(), dto.getEndDate());
        long remaining = annualEntitlementDays - alreadyUsed;

        if (requested > remaining) {
            throw new BadRequestException(String.format(
                    "This request is %d day(s), but you have only %d of your %d annual "
                            + "leave days remaining for %d.",
                    requested, Math.max(remaining, 0), annualEntitlementDays, year));
        }
    }

    /**
     * Length of a leave in whole days, counting both ends.
     *
     * The +1 is the point: 10 Sep to 12 Sep is three days off work, not two.
     * ChronoUnit.DAYS.between is exclusive of the end date.
     */
    static long lengthInDays(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /**
     * JOIN FETCH so the employee is loaded in the same query. Without it,
     * open-in-view = false would throw LazyInitializationException as soon as
     * checkOwnership() or the mapper touched leaveRequest.getEmployee().
     */
    private LeaveRequest getLeaveOrThrow(Long id) {
        return leaveRequestRepository.findByIdWithEmployee(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request", id));
    }

    private List<LeaveResponse> toResponseList(List<LeaveRequest> leaveRequests) {
        List<LeaveResponse> result = new ArrayList<>();
        for (LeaveRequest leaveRequest : leaveRequests) {
            result.add(toResponse(leaveRequest));
        }
        return result;
    }

    /** Entity -> DTO, flattening the employee into three scalar fields. */
    public static LeaveResponse toResponse(LeaveRequest leaveRequest) {
        Employee employee = leaveRequest.getEmployee();
        return new LeaveResponse(
                leaveRequest.getId(),
                employee.getId(),
                employee.getName(),
                employee.getEmployeeCode(),
                leaveRequest.getLeaveType(),
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                leaveRequest.getReason(),
                leaveRequest.getStatus(),
                leaveRequest.getCreatedAt(),
                leaveRequest.getReviewedAt());
    }
}
