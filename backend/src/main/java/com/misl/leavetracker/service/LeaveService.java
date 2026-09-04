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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for leave requests.
 *
 * Three kinds of rule live here, none of which an annotation can express:
 *   1. endDate must not be before startDate            (spans two fields)
 *   2. only a PENDING request may be edited or reviewed (depends on stored state)
 *   3. an employee may only touch their OWN requests    (depends on who is asking)
 *
 * Rule 3 is the one that matters most for the assessment. @PreAuthorize on the
 * controller can express "must be an ADMIN", but it cannot express "must be the
 * owner of row 7" - that requires loading row 7 first. So role checks sit on the
 * controller and ownership checks sit here, right next to the data they protect.
 */
@Service
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;

    public LeaveService(LeaveRequestRepository leaveRequestRepository,
                        EmployeeRepository employeeRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
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

    /** A single-day leave (start == end) is valid; only end BEFORE start is rejected. */
    private void validateDates(LeaveRequestDto dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BadRequestException("End date must not be before start date");
        }
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
