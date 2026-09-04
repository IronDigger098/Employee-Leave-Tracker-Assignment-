package com.misl.leavetracker.controller;

import com.misl.leavetracker.dto.LeaveRequestDto;
import com.misl.leavetracker.dto.LeaveResponse;
import com.misl.leavetracker.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP entry point for leave requests.
 *
 * ============================================================================
 *  PHASE 3 NOTE - the two `@RequestParam Long employeeId` parameters below are
 *  TEMPORARY. They exist only so these endpoints can be tested before
 *  authentication exists. In Phase 4 each is replaced by the id of the
 *  authenticated user taken from the SecurityContext, and the query parameter
 *  disappears from the API. Nothing else in this class changes.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    /** GET /api/leaves -> 200, all requests newest first. Becomes ADMIN-only in Phase 4. */
    @GetMapping
    public ResponseEntity<List<LeaveResponse>> getAll() {
        return ResponseEntity.ok(leaveService.findAll());
    }

    /**
     * GET /api/leaves/my -> 200, the caller's own requests.
     *
     * Declared before /{id} for readability, though Spring does not need the order:
     * when two patterns match, the more specific literal segment ("my") always wins
     * over a variable segment ("{id}").
     *
     * PHASE 3: employeeId comes from a query parameter. PHASE 4: from the JWT.
     */
    @GetMapping("/my")
    public ResponseEntity<List<LeaveResponse>> getMy(@RequestParam Long employeeId) {
        return ResponseEntity.ok(leaveService.findByEmployee(employeeId));
    }

    /** GET /api/leaves/{id} -> 200, or 404. */
    @GetMapping("/{id}")
    public ResponseEntity<LeaveResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.findById(id));
    }

    /**
     * POST /api/leaves -> 201 Created, status PENDING.
     *
     * PHASE 3: employeeId comes from a query parameter. PHASE 4: from the JWT.
     */
    @PostMapping
    public ResponseEntity<LeaveResponse> create(@Valid @RequestBody LeaveRequestDto request,
                                                @RequestParam Long employeeId) {
        LeaveResponse created = leaveService.create(request, employeeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** PUT /api/leaves/{id} -> 200. Rejected with 400 if the request is not PENDING. */
    @PutMapping("/{id}")
    public ResponseEntity<LeaveResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody LeaveRequestDto request) {
        return ResponseEntity.ok(leaveService.update(id, request));
    }

    /** DELETE /api/leaves/{id} -> 204 No Content. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        leaveService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/leaves/{id}/approve -> 200.
     *
     * PATCH rather than PUT because this changes ONE field (status) rather than
     * replacing the whole resource, and it carries no body at all - the URL says
     * everything. Becomes ADMIN-only in Phase 4.
     */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<LeaveResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.approve(id));
    }

    /** PATCH /api/leaves/{id}/reject -> 200. Becomes ADMIN-only in Phase 4. */
    @PatchMapping("/{id}/reject")
    public ResponseEntity<LeaveResponse> reject(@PathVariable Long id) {
        return ResponseEntity.ok(leaveService.reject(id));
    }
}
