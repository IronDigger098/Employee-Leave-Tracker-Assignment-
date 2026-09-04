package com.misl.leavetracker.controller;

import com.misl.leavetracker.dto.EmployeeRequest;
import com.misl.leavetracker.dto.EmployeeResponse;
import com.misl.leavetracker.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP entry point for employee management.
 *
 * This class is deliberately thin. Its whole job is:
 *   URL + method  ->  service call  ->  HTTP status code.
 * No business rules, no repository access, no try/catch (the
 * GlobalExceptionHandler owns that).
 *
 * @RestController = @Controller + @ResponseBody, meaning every return value is
 * serialised to JSON by Jackson instead of being resolved as a view name.
 *
 * The class-level @PreAuthorize applies to EVERY method below - managing staff
 * records is an HR function. Putting it on the class rather than repeating it on
 * five methods also means an endpoint added here later is protected by default
 * instead of being accidentally left open.
 *
 * A non-admin calling any of these gets 403; a caller with no token at all gets
 * 401 from the filter chain, before this class is ever reached.
 */
@RestController
@RequestMapping("/api/employees")
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /** GET /api/employees -> 200 with the full list. */
    @GetMapping
    public ResponseEntity<List<EmployeeResponse>> getAll() {
        return ResponseEntity.ok(employeeService.findAll());
    }

    /**
     * GET /api/employees/{id} -> 200, or 404 if absent.
     * @PathVariable binds the {id} segment of the URL to the method parameter.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.findById(id));
    }

    /**
     * POST /api/employees -> 201 Created.
     *
     * @RequestBody tells Jackson to deserialise the JSON body into EmployeeRequest.
     * @Valid runs Bean Validation on it FIRST; if it fails, the method is never
     * entered and GlobalExceptionHandler returns 400 with per-field messages.
     * Both annotations are needed - @Valid alone would not bind the body, and
     * @RequestBody alone would not validate it.
     */
    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        EmployeeResponse created = employeeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** PUT /api/employees/{id} -> 200 with the updated resource. */
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.ok(employeeService.update(id, request));
    }

    /**
     * DELETE /api/employees/{id} -> 204 No Content.
     * 204 rather than 200 because there is no body to return.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
