package com.misl.leavetracker.dto;

import com.misl.leavetracker.entity.LeaveStatus;
import com.misl.leavetracker.entity.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Outgoing representation of a leave request.
 *
 * The employee is flattened into three scalar fields (id, name, code) rather than
 * nested as an object. Two reasons:
 *   1. The admin leave table needs exactly the name and code to render a row -
 *      nesting the whole employee would ship a department, designation and role
 *      that nothing displays.
 *   2. Serialising the entity would follow employee -> leaveRequests -> employee
 *      and recurse forever. Flattening in the service sidesteps that entirely,
 *      which is why this project needs no @JsonIgnore anywhere.
 */
public class LeaveResponse {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private LeaveType leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private LeaveStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    public LeaveResponse() {
    }

    public LeaveResponse(Long id, Long employeeId, String employeeName, String employeeCode,
                         LeaveType leaveType, LocalDate startDate, LocalDate endDate,
                         String reason, LeaveStatus status,
                         LocalDateTime createdAt, LocalDateTime reviewedAt) {
        this.id = id;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.employeeCode = employeeCode;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
        this.reviewedAt = reviewedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

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

    public LeaveStatus getStatus() {
        return status;
    }

    public void setStatus(LeaveStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
