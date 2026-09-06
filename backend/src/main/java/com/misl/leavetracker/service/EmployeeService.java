package com.misl.leavetracker.service;

import com.misl.leavetracker.dto.EmployeeRequest;
import com.misl.leavetracker.dto.EmployeeResponse;
import com.misl.leavetracker.entity.Employee;
import com.misl.leavetracker.entity.Role;
import com.misl.leavetracker.exception.BadRequestException;
import com.misl.leavetracker.exception.DuplicateResourceException;
import com.misl.leavetracker.exception.ResourceNotFoundException;
import com.misl.leavetracker.repository.EmployeeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for employees.
 *
 * The controller does no thinking; it maps HTTP to a method call. All rules -
 * uniqueness, password hashing, "blank password means keep the old one" - live here.
 * That way the same rules apply no matter who calls the service.
 *
 * Laravel comparison: an app/Services class, with the repository injected the
 * same way Laravel's container would inject it.
 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructor injection - no @Autowired needed, Spring uses the single
     * constructor automatically.
     *
     * Preferred over field injection because the fields can be `final` (so the
     * object is immutable and impossible to construct half-initialised), and
     * because it makes dependencies obvious: if this constructor gets long, the
     * class is doing too much.
     */
    public EmployeeService(EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * The staff list.
     *
     * readOnly = true lets Hibernate skip dirty-checking (it does not need to
     * detect changes it will never flush), and tells the driver this is a
     * read-only transaction.
     *
     * Archived employees are excluded by default - they are no longer part of the
     * roster, and an admin managing staff does not want them cluttering the table.
     * They are still reachable with includeArchived = true, because "keep the
     * history" is worthless if nobody can look at it.
     */
    @Transactional(readOnly = true)
    public List<EmployeeResponse> findAll(boolean includeArchived) {
        List<Employee> employees = new ArrayList<>(employeeRepository.findByDeletedFalseOrderByNameAsc());
        if (includeArchived) {
            employees.addAll(employeeRepository.findByDeletedTrueOrderByNameAsc());
        }

        List<EmployeeResponse> result = new ArrayList<>();
        for (Employee employee : employees) {
            result.add(toResponse(employee));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long id) {
        return toResponse(getEmployeeOrThrow(id));
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        // Password is optional on update but mandatory here - see EmployeeRequest.
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BadRequestException("Password is required when creating an employee");
        }
        /*
         * Loading the clashing row rather than calling existsBy... so the message can
         * say WHY. An archived employee keeps their email and code - the row survives
         * so their leave history stays attached to a real person - but they are hidden
         * from the staff list. A bare "already exists" would send the admin hunting
         * for a record they cannot see.
         */
        employeeRepository.findByEmail(request.getEmail()).ifPresent(clash -> {
            throw new DuplicateResourceException(
                    duplicateMessage("email", request.getEmail(), clash));
        });
        employeeRepository.findByEmployeeCode(request.getEmployeeCode()).ifPresent(clash -> {
            throw new DuplicateResourceException(
                    duplicateMessage("code", request.getEmployeeCode(), clash));
        });

        Employee employee = new Employee();
        employee.setEmployeeCode(request.getEmployeeCode());
        employee.setName(request.getName());
        employee.setEmail(request.getEmail());
        // The ONLY place a raw password is turned into a stored value.
        employee.setPassword(passwordEncoder.encode(request.getPassword()));
        employee.setDepartment(request.getDepartment());
        employee.setDesignation(request.getDesignation());
        employee.setRole(request.getRole());
        employee.setActive(request.getActive() == null || request.getActive());

        return toResponse(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = getEmployeeOrThrow(id);

        if (employee.isDeleted()) {
            throw new BadRequestException(
                    "This employee has been archived and can no longer be edited.");
        }

        // "AndIdNot" excludes this row, so re-saving without changing the email is fine.
        if (employeeRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new DuplicateResourceException("Another employee already uses email "
                    + request.getEmail());
        }
        if (employeeRepository.existsByEmployeeCodeAndIdNot(request.getEmployeeCode(), id)) {
            throw new DuplicateResourceException("Another employee already uses code "
                    + request.getEmployeeCode());
        }

        /*
         * The other two doors to a locked-out system. Archiving the last admin is
         * the obvious one, but demoting them to EMPLOYEE or setting active = false
         * has exactly the same effect, and an edit form makes either a one-click
         * accident. Same guard, same message.
         */
        boolean losingAdminAccess = request.getRole() != Role.ADMIN
                || (request.getActive() != null && !request.getActive());
        if (losingAdminAccess) {
            assertNotLastActiveAdmin(employee, "Changing their role or deactivating them");
        }

        employee.setEmployeeCode(request.getEmployeeCode());
        employee.setName(request.getName());
        employee.setEmail(request.getEmail());
        employee.setDepartment(request.getDepartment());
        employee.setDesignation(request.getDesignation());
        employee.setRole(request.getRole());
        if (request.getActive() != null) {
            employee.setActive(request.getActive());
        }
        // Blank password means "leave the existing hash alone".
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            employee.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        /*
         * No explicit save() call is strictly required here: `employee` is a managed
         * entity inside an open transaction, so Hibernate's dirty checking will flush
         * the UPDATE at commit. We call save() anyway because it makes the intent
         * obvious to a reader who does not know that rule.
         */
        return toResponse(employeeRepository.save(employee));
    }

    /**
     * Archives an employee. Their leave history is NOT deleted.
     *
     * This is a SOFT DELETE: the row stays, `deleted` is set to true, and `active`
     * is set to false so they can no longer log in. From the admin's point of view
     * the employee disappears from the staff list; from HR's point of view every
     * leave request they ever filed is still there, still attached to their name.
     *
     * Why not a real delete. A leave request is not just data about a person, it is
     * a record of a decision the company made - who asked for what, who approved
     * it, and when. Removing the employee would remove that history with them, and
     * an HR system has to be able to answer "how much leave did this person take in
     * 2026?" long after they have left. Payroll and audit both depend on it.
     *
     * The endpoint stays DELETE /api/employees/{id} because that is the REST verb
     * for "remove this from the collection", which is what the caller means and
     * what the assignment specifies. How removal is implemented is our business,
     * not the client's.
     */
    @Transactional
    public void delete(Long id, Long currentUserId) {
        Employee employee = getEmployeeOrThrow(id);

        if (employee.isDeleted()) {
            throw new BadRequestException("This employee has already been archived.");
        }

        /*
         * Guard 1: no archiving yourself.
         *
         * Archiving sets active = false, and JwtAuthenticationFilter re-checks that
         * flag on every single request - so an admin who archived themselves would
         * be signed out by their very next click, holding a token the server now
         * refuses. Blocking it here turns an irreversible mistake into a message.
         */
        if (employee.getId().equals(currentUserId)) {
            throw new BadRequestException(
                    "You cannot archive your own account. Ask another administrator to do it.");
        }

        // Guard 2: never leave the system without a usable administrator.
        assertNotLastActiveAdmin(employee, "Archiving them");

        employee.setDeleted(true);
        // Archived implies no access - otherwise a removed employee could still log in.
        employee.setActive(false);

        employeeRepository.save(employee);
    }

    /**
     * Explains a duplicate email or employee code, naming the archived case.
     */
    private static String duplicateMessage(String field, String value, Employee clash) {
        if (clash.isDeleted()) {
            return "The " + field + " " + value + " belongs to " + clash.getName()
                    + ", an archived employee. Archived records keep their email and code so"
                    + " their leave history stays intact — please use a different " + field + ".";
        }
        return "An employee with " + field + " " + value + " already exists";
    }

    /**
     * Refuses any change that would remove the final working administrator.
     *
     * This is a lockout guard, not a business rule. Creating an ADMIN requires
     * being an ADMIN (@PreAuthorize("hasRole('ADMIN')") on EmployeeController), so
     * the moment the last one loses access there is no path back in through the
     * application at all - recovery means hand-editing the database or wiping the
     * volume and losing every record. A one-query check is cheap insurance against
     * a mistake with no undo.
     *
     * There are three doors into that state and all of them come through here:
     * archiving the admin, deactivating them, and demoting them to EMPLOYEE.
     */
    private void assertNotLastActiveAdmin(Employee employee, String action) {
        boolean currentlyAWorkingAdmin =
                employee.getRole() == Role.ADMIN && employee.isActive() && !employee.isDeleted();

        if (!currentlyAWorkingAdmin) {
            // Removing someone who was never a usable admin cannot cause a lockout.
            return;
        }

        if (employeeRepository.countByRoleAndActiveTrueAndDeletedFalse(Role.ADMIN) <= 1) {
            throw new BadRequestException(
                    "This is the only active administrator. " + action
                            + " would leave nobody able to manage employees or review leave "
                            + "requests, and no way to create a replacement. "
                            + "Create another ADMIN first.");
        }
    }

    /**
     * Loads an employee or throws 404. Kept package-visible so LeaveService can
     * reuse it in the next phase.
     */
    @Transactional(readOnly = true)
    public Employee getEmployeeOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    /**
     * Entity -> DTO mapping, written by hand.
     *
     * Ten obvious lines beat a MapStruct/ModelMapper dependency here: nothing is
     * generated, nothing is reflective, and the password field is visibly absent.
     */
    public static EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getName(),
                employee.getEmail(),
                employee.getDepartment(),
                employee.getDesignation(),
                employee.getRole(),
                employee.isActive(),
                employee.isDeleted());
    }
}
