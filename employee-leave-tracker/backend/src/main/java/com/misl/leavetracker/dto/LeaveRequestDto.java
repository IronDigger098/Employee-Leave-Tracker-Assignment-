package com.misl.leavetracker.dto;

import com.misl.leavetracker.entity.LeaveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Incoming payload for submitting or editing a leave request.
 *
 * Note what is absent: employeeId and status.
 *   - employeeId comes from the authenticated user, never from the client. If the
 *     client could send it, any employee could file leave in someone else's name.
 *   - status is always PENDING on creation and can only change through the
 *     /approve and /reject endpoints. Letting a client set it would let an
 *     employee approve their own leave.
 *
 * Leaving a field out of the DTO is the cheapest possible access control.
 *
 * Dates arrive as ISO strings ("2026-09-10"). Jackson's JavaTimeModule, which
 * Spring Boot registers automatically, converts them to LocalDate.
 */
public class LeaveRequestDto {

    @NotNull(message = "Leave type is required")
    private LeaveType leaveType;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;

    /*
     * "End date must not be before start date" is NOT an annotation here.
     * Bean Validation annotations are per-field; this rule compares two fields,
     * so it lives in LeaveService.validateDates(). A custom class-level
     * constraint could express it, but that is a lot of machinery for one rule.
     */

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
