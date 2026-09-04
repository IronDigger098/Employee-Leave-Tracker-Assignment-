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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for leave requests.
 *
 * Two rules live here that no annotation can express:
 *   1. endDate must not be before startDate  (compares two fields)
 *   2. only a PENDING request may be edited, approved or rejected  (depends on
 *      the row's current state, not on the incoming payload)
 *
 * Both are enforced here rather than in the controller so they hold for every
 * caller - the REST API today, the data seeder, and anything added later.
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

    /** Admin view: every request, newest first. Uses JOIN FETCH to avoid N+1. */
    @Transactional(readOnly = true)
    public List<LeaveResponse> findAll() {
        return toResponseList(leaveRequestRepository.findAllWithEmployee());
    }

    /** Employee view: one person's own requests, newest first. */
    @Transactional(readOnly = true)
    public List<LeaveResponse> findByEmployee(Long employeeId) {
        return toResponseList(leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId));
    }

    @Transactional(readOnly = true)
    public LeaveResponse findById(Long id) {
        return toResponse(getLeaveOrThrow(id));
    }

    /**
     * Submit a new leave request.
     *
     * employeeId is a parameter, never part of the DTO - in Phase 4 the controller
     * will supply it from the authenticated user instead of from the request.
     * Status is forced to PENDING; the client has no say in it.
     */
    @Transactional
    public LeaveResponse create(LeaveRequestDto dto, Long employeeId) {
        validateDates(dto);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        LeaveRequest leaveRequest = new LeaveRequest(
                employee,
                dto.getLeaveType(),
                dto.getStartDate(),
                dto.getEndDate(),
                dto.getReason());
        // status = PENDING and createdAt are set by the constructor and @PrePersist.

        return toResponse(leaveRequestRepository.save(leaveRequest));
    }

    /**
     * Edit a request that has not been reviewed yet.
     *
     * Once an admin has approved or rejected it, the record is a decision and
     * editing it would change what was decided - so that is a 400, not a silent update.
     */
    @Transactional
    public LeaveResponse update(Long id, LeaveRequestDto dto) {
        LeaveRequest leaveRequest = getLeaveOrThrow(id);

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

    @Transactional
    public void delete(Long id) {
        leaveRequestRepository.delete(getLeaveOrThrow(id));
    }

    @Transactional
    public LeaveResponse approve(Long id) {
        return review(id, LeaveStatus.APPROVED);
    }

    @Transactional
    public LeaveResponse reject(Long id) {
        return review(id, LeaveStatus.REJECTED);
    }

    /**
     * The single state transition in this application: PENDING -> APPROVED / REJECTED.
     *
     * approve() and reject() differ only in the target status, so the guard, the
     * timestamp and the save live in one place. If the rule changes, it changes once.
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
     * A same-day leave (start == end) is valid; only end BEFORE start is rejected.
     */
    private void validateDates(LeaveRequestDto dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BadRequestException("End date must not be before start date");
        }
    }

    /**
     * Loads with JOIN FETCH so the employee is available when mapping to the DTO.
     * Without it, open-in-view = false would cause a LazyInitializationException
     * the moment Jackson or the mapper touched leaveRequest.getEmployee().
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
