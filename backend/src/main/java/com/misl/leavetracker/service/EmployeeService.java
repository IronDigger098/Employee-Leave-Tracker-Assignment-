package com.misl.leavetracker.service;

import com.misl.leavetracker.dto.EmployeeRequest;
import com.misl.leavetracker.dto.EmployeeResponse;
import com.misl.leavetracker.entity.Employee;
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
     * readOnly = true lets Hibernate skip dirty-checking (it does not need to
     * detect changes it will never flush), and tells the driver this is a
     * read-only transaction.
     */
    @Transactional(readOnly = true)
    public List<EmployeeResponse> findAll() {
        List<EmployeeResponse> result = new ArrayList<>();
        for (Employee employee : employeeRepository.findAll()) {
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
        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("An employee with email "
                    + request.getEmail() + " already exists");
        }
        if (employeeRepository.existsByEmployeeCode(request.getEmployeeCode())) {
            throw new DuplicateResourceException("An employee with code "
                    + request.getEmployeeCode() + " already exists");
        }

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

        // "AndIdNot" excludes this row, so re-saving without changing the email is fine.
        if (employeeRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new DuplicateResourceException("Another employee already uses email "
                    + request.getEmail());
        }
        if (employeeRepository.existsByEmployeeCodeAndIdNot(request.getEmployeeCode(), id)) {
            throw new DuplicateResourceException("Another employee already uses code "
                    + request.getEmployeeCode());
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

    @Transactional
    public void delete(Long id) {
        Employee employee = getEmployeeOrThrow(id);
        // cascade = ALL + orphanRemoval on Employee.leaveRequests deletes their
        // leave requests too, instead of hitting a foreign key violation.
        employeeRepository.delete(employee);
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
                employee.isActive());
    }
}
