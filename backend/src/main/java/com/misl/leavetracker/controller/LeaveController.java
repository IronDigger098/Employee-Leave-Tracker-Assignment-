package com.misl.leavetracker.controller;

import com.misl.leavetracker.dto.LeaveRequestDto;
import com.misl.leavetracker.dto.LeaveResponse;
import com.misl.leavetracker.security.EmployeeUserDetails;
import com.misl.leavetracker.service.LeaveService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP entry point for leave requests.
 *
 * The Phase 3 "?employeeId=" query parameters are gone. Identity now comes from
 * {@code @AuthenticationPrincipal}, which hands us the EmployeeUserDetails that
 * JwtAuthenticationFilter placed in the SecurityContext. The client can no longer
 * claim to be someone else, because it never supplies the id at all.
 *
 * Authorization is split deliberately:
 *   - ROLE checks live here as @PreAuthorize. They are static and readable at a
 *     glance: "this endpoint is for admins".
 *   - OWNERSHIP checks live in LeaveService, because deciding whether row 7
 *     belongs to you requires loading row 7 first.
 */
@Tag(name = "Leave Requests", description = "Submitting, viewing and reviewing leave requests.")
@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    /**
     * GET /api/leaves - every request in the system. ADMIN only.
     *
     * @PreAuthorize runs BEFORE the method body. A non-admin never reaches the
     * service; Spring throws AccessDeniedException, which GlobalExceptionHandler
     * turns into 403.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LeaveResponse>> getAll() {
        return ResponseEntity.ok(leaveService.findAll());
    }

    /** GET /api/leaves/my - the caller's own requests. Any authenticated user. */
    @GetMapping("/my")
    public ResponseEntity<List<LeaveResponse>> getMy(
            @AuthenticationPrincipal EmployeeUserDetails currentUser) {
        return ResponseEntity.ok(leaveService.findMyLeaves(currentUser));
    }

    /**
     * GET /api/leaves/{id} - 200 for an admin or the owner, 403 for anyone else.
     *
     * No @PreAuthorize here: the answer depends on the row, so the check has to
     * happen in the service after loading it.
     */
    @GetMapping("/{id}")
    public ResponseEntity<LeaveResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal EmployeeUserDetails currentUser) {
        return ResponseEntity.ok(leaveService.findById(id, currentUser));
    }

    /**
     * POST /api/leaves -> 201, status PENDING, owned by the caller.
     *
     * Left open to any authenticated user rather than restricted to EMPLOYEE: an
     * admin is also a member of staff who takes leave, and forbidding that would
     * be an odd rule to have to justify.
     */
    @PostMapping
    public ResponseEntity<LeaveResponse> create(
            @Valid @RequestBody LeaveRequestDto request,
            @AuthenticationPrincipal EmployeeUserDetails currentUser) {
        LeaveResponse created = leaveService.create(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** PUT /api/leaves/{id} - owner only, and only while PENDING. */
    @PutMapping("/{id}")
    public ResponseEntity<LeaveResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody LeaveRequestDto request,
            @AuthenticationPrincipal EmployeeUserDetails currentUser) {
        return ResponseEntity.ok(leaveService.update(id, request, currentUser));
    }

    /** DELETE /api/leaves/{id} -> 204. Admin: any. Employee: own, while PENDING. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal EmployeeUserDetails currentUser) {
        leaveService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/leaves/{id}/approve - ADMIN only.
     *
     * PATCH rather than PUT because this changes one field rather than replacing
     * the resource, and it carries no request body at all.
     */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LeaveResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.approve(id));
    }

    /** PATCH /api/leaves/{id}/reject - ADMIN only. */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LeaveResponse> reject(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.reject(id));
    }
}
