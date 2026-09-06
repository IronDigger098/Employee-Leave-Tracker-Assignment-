package com.misl.leavetracker.service;

import com.misl.leavetracker.dto.EmployeeRequest;
import com.misl.leavetracker.dto.EmployeeResponse;
import com.misl.leavetracker.entity.Employee;
import com.misl.leavetracker.entity.Role;
import com.misl.leavetracker.exception.BadRequestException;
import com.misl.leavetracker.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmployeeService}, focused on the archive behaviour.
 *
 * The rule being protected here is easy to break by accident and expensive when
 * it breaks: deleting an employee must NOT destroy their leave history. These
 * tests fail loudly if anyone ever swaps the soft delete back for a real one.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeService archiving")
class EmployeeServiceTest {

    private static final Long EMPLOYEE_ID = 2L;
    private static final Long ADMIN_ID = 1L;
    /** Whoever is signed in while performing the archive - an admin other than the target. */
    private static final Long CALLER_ID = 99L;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private EmployeeService employeeService;

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeService(employeeRepository, passwordEncoder);
    }

    private Employee rahim() {
        Employee employee = new Employee(
                "EMP001", "Rahim Uddin", "rahim@misl.com", "hashed",
                "Engineering", "Software Engineer", Role.EMPLOYEE, true);
        employee.setId(EMPLOYEE_ID);
        return employee;
    }

    private Employee ayeshaTheAdmin() {
        Employee employee = new Employee(
                "ADM001", "Ayesha Rahman", "admin@misl.com", "hashed",
                "Human Resources", "HR Manager", Role.ADMIN, true);
        employee.setId(ADMIN_ID);
        return employee;
    }

    @Test
    @DisplayName("delete() archives the employee instead of removing the row")
    void deleteArchivesRatherThanRemoving() {
        Employee rahim = rahim();
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(rahim));

        employeeService.delete(EMPLOYEE_ID, CALLER_ID);

        /*
         * The assertion that matters. If this ever fails because someone
         * reintroduced a hard delete, every leave request belonging to this
         * employee would be destroyed along with them.
         */
        verify(employeeRepository, never()).delete(any(Employee.class));
        verify(employeeRepository, never()).deleteById(any(Long.class));

        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(saved.capture());

        assertThat(saved.getValue().isDeleted()).isTrue();
        // Archived implies no access - otherwise a removed employee could still log in.
        assertThat(saved.getValue().isActive()).isFalse();
    }

    @Test
    @DisplayName("delete() refuses an employee that is already archived")
    void deleteRefusesAlreadyArchivedEmployee() {
        Employee archived = rahim();
        archived.setDeleted(true);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> employeeService.delete(EMPLOYEE_ID, CALLER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been archived");

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    @DisplayName("delete() refuses to archive the last active admin")
    void deleteRefusesTheLastAdmin() {
        Employee admin = ayeshaTheAdmin();
        when(employeeRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        // Ayesha is the only administrator who can still sign in.
        when(employeeRepository.countByRoleAndActiveTrueAndDeletedFalse(Role.ADMIN)).thenReturn(1L);

        /*
         * Without this guard the application is permanently locked out: creating an
         * ADMIN requires being an ADMIN, so once the last one is archived there is
         * no route back in short of editing the database by hand.
         */
        assertThatThrownBy(() -> employeeService.delete(ADMIN_ID, CALLER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only active administrator");

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    @DisplayName("delete() allows archiving an admin while another admin remains")
    void deleteAllowsAdminWhenAnotherRemains() {
        Employee admin = ayeshaTheAdmin();
        when(employeeRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(employeeRepository.countByRoleAndActiveTrueAndDeletedFalse(Role.ADMIN)).thenReturn(2L);

        employeeService.delete(ADMIN_ID, CALLER_ID);

        // The rule protects the LAST admin, not admins in general.
        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(saved.capture());
        assertThat(saved.getValue().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("delete() refuses to let an admin archive their own account")
    void deleteRefusesSelfArchive() {
        Employee admin = ayeshaTheAdmin();
        when(employeeRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));

        // Same id in both arguments: the signed-in admin clicked Archive on themselves.
        assertThatThrownBy(() -> employeeService.delete(ADMIN_ID, ADMIN_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot archive your own account");

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    @DisplayName("update() refuses to demote the last active admin to EMPLOYEE")
    void updateRefusesDemotingTheLastAdmin() {
        Employee admin = ayeshaTheAdmin();
        when(employeeRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(employeeRepository.existsByEmailAndIdNot("admin@misl.com", ADMIN_ID)).thenReturn(false);
        when(employeeRepository.existsByEmployeeCodeAndIdNot("ADM001", ADMIN_ID)).thenReturn(false);
        when(employeeRepository.countByRoleAndActiveTrueAndDeletedFalse(Role.ADMIN)).thenReturn(1L);

        /*
         * Archiving is not the only way to lose the last admin. Demoting them to
         * EMPLOYEE, or setting active = false, locks the system just as thoroughly -
         * and an edit form makes either a single careless click.
         */
        EmployeeRequest demotion = new EmployeeRequest();
        demotion.setEmployeeCode("ADM001");
        demotion.setName("Ayesha Rahman");
        demotion.setEmail("admin@misl.com");
        demotion.setDepartment("Human Resources");
        demotion.setDesignation("HR Manager");
        demotion.setRole(Role.EMPLOYEE);
        demotion.setActive(true);

        assertThatThrownBy(() -> employeeService.update(ADMIN_ID, demotion))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only active administrator");

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    @DisplayName("findAll() hides archived employees by default")
    void findAllHidesArchivedByDefault() {
        when(employeeRepository.findByDeletedFalseOrderByNameAsc()).thenReturn(List.of(rahim()));

        List<EmployeeResponse> result = employeeService.findAll(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isDeleted()).isFalse();
        // The archived query is not even run when it is not wanted.
        verify(employeeRepository, never()).findByDeletedTrueOrderByNameAsc();
    }

    @Test
    @DisplayName("findAll(includeArchived) returns archived employees too")
    void findAllCanIncludeArchived() {
        Employee archived = rahim();
        archived.setId(3L);
        archived.setDeleted(true);

        when(employeeRepository.findByDeletedFalseOrderByNameAsc()).thenReturn(List.of(rahim()));
        when(employeeRepository.findByDeletedTrueOrderByNameAsc()).thenReturn(List.of(archived));

        List<EmployeeResponse> result = employeeService.findAll(true);

        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(EmployeeResponse::isDeleted);
    }
}
