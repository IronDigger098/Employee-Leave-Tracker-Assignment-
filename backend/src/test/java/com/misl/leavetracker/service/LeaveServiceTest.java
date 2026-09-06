package com.misl.leavetracker.service;

import com.misl.leavetracker.dto.LeaveRequestDto;
import com.misl.leavetracker.dto.LeaveResponse;
import com.misl.leavetracker.entity.Employee;
import com.misl.leavetracker.entity.LeaveRequest;
import com.misl.leavetracker.entity.LeaveStatus;
import com.misl.leavetracker.entity.LeaveType;
import com.misl.leavetracker.entity.Role;
import com.misl.leavetracker.exception.BadRequestException;
import com.misl.leavetracker.exception.ResourceNotFoundException;
import com.misl.leavetracker.repository.EmployeeRepository;
import com.misl.leavetracker.repository.LeaveRequestRepository;
import com.misl.leavetracker.security.EmployeeUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the business rules in {@link LeaveService}.
 *
 * These target exactly the four kinds of rule that no annotation can express,
 * which is precisely why they are the rules worth testing:
 *
 *   1. cross-field validation  - endDate must not precede startDate
 *   2. state transitions       - only a PENDING request may be edited or reviewed
 *   3. ownership               - an employee may only touch their own requests
 *   4. annual entitlement      - depends on the employee's other rows for the year
 *
 * Deliberately NOT @SpringBootTest. These are plain unit tests: the repositories
 * are Mockito mocks, so no Spring context starts and no database is needed. They
 * run in milliseconds and pass on a machine with no PostgreSQL installed - which
 * matters, because a test that needs infrastructure is a test people stop running.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveService business rules")
class LeaveServiceTest {

    /*
     * Declared as Long rather than written inline as `1L`. The ids these are
     * passed to are all declared Long, and using a typed constant means the
     * compiler never has to box a primitive at the call site - as well as giving
     * each id a name instead of a bare number scattered through the file.
     */
    private static final Long LEAVE_ID = 1L;
    private static final Long UNKNOWN_LEAVE_ID = 99L;
    private static final Long ADMIN_ID = 1L;
    private static final Long RAHIM_ID = 2L;
    private static final Long KARIM_ID = 3L;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    /** The entitlement the service under test is configured with. */
    private static final int ENTITLEMENT_DAYS = 27;

    private LeaveService leaveService;

    /**
     * Built by hand rather than with @InjectMocks.
     *
     * The service's third constructor argument is a plain int read from
     * configuration. @InjectMocks has no mock to supply for a primitive and would
     * pass 0, giving every test an entitlement of zero days - so every request
     * would be refused and the failures would look like a bug in the rule rather
     * than a bug in the test setup.
     */
    @BeforeEach
    void setUp() {
        leaveService = new LeaveService(leaveRequestRepository, employeeRepository, ENTITLEMENT_DAYS);
    }

    // ---------------------------------------------------------------- helpers

    private Employee employee(Long id, String name, Role role) {
        Employee employee = new Employee(
                "EMP" + id, name, name.toLowerCase() + "@misl.com",
                "hashed", "Engineering", "Engineer", role, true);
        employee.setId(id);
        return employee;
    }

    private LeaveRequest pendingLeaveOwnedBy(Employee owner) {
        LeaveRequest leaveRequest = new LeaveRequest(
                owner, LeaveType.CASUAL,
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12),
                "Family function");
        leaveRequest.setId(LEAVE_ID);
        return leaveRequest;
    }

    private LeaveRequestDto dto(LocalDate start, LocalDate end) {
        LeaveRequestDto dto = new LeaveRequestDto();
        dto.setLeaveType(LeaveType.CASUAL);
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setReason("Family function");
        return dto;
    }

    /**
     * Makes save() behave like the real one: returns the entity it was handed.
     *
     * any(LeaveRequest.class), not a bare any(). CrudRepository.save is declared
     * as <S extends T> S save(S entity), so an untyped any() infers to Object,
     * which does not satisfy that bound and fails to compile.
     */
    private void stubSaveReturnsArgument() {
        when(leaveRequestRepository.save(any(LeaveRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * The entitlement check asks the repository what this employee already has
     * booked this year. These helpers control that answer.
     *
     * Only stubbed in tests that actually reach the check - Mockito runs in strict
     * mode, and an unused stub fails the test rather than being ignored.
     */
    private void stubExistingLeave(LeaveRequest... existing) {
        when(leaveRequestRepository.findByEmployeeIdAndStatusInAndStartDateBetween(
                any(Long.class), any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(existing));
    }

    /** A previously booked leave of the given length, owned by the given employee. */
    private LeaveRequest bookedLeave(Employee owner, Long id, LocalDate start, int days) {
        LeaveRequest leaveRequest = new LeaveRequest(
                owner, LeaveType.ANNUAL, start, start.plusDays(days - 1L), "Earlier leave");
        leaveRequest.setId(id);
        leaveRequest.setStatus(LeaveStatus.APPROVED);
        return leaveRequest;
    }

    // ------------------------------------------------- 1. date range validation

    @Test
    @DisplayName("create() rejects an end date earlier than the start date")
    void createRejectsBackwardsDateRange() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);
        LeaveRequestDto backwards = dto(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 15));
        EmployeeUserDetails currentUser = new EmployeeUserDetails(rahim);

        assertThatThrownBy(() -> leaveService.create(backwards, currentUser))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("End date must not be before start date");

        // The dates are checked before anything is loaded or written, so neither
        // repository should have been touched at all.
        verify(employeeRepository, never()).findById(any(Long.class));
        verify(leaveRequestRepository, never()).save(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("create() accepts a single-day leave where start equals end")
    void createAcceptsSingleDayLeave() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);
        LocalDate sameDay = LocalDate.of(2026, 9, 10);

        stubExistingLeave();
        when(employeeRepository.findById(RAHIM_ID)).thenReturn(Optional.of(rahim));
        stubSaveReturnsArgument();

        LeaveResponse response =
                leaveService.create(dto(sameDay, sameDay), new EmployeeUserDetails(rahim));

        assertThat(response.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(response.getStartDate()).isEqualTo(response.getEndDate());
    }

    @Test
    @DisplayName("create() always sets status PENDING and the caller as owner")
    void createAlwaysStartsPendingAndOwnedByCaller() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);

        stubExistingLeave();
        when(employeeRepository.findById(RAHIM_ID)).thenReturn(Optional.of(rahim));
        stubSaveReturnsArgument();

        LeaveResponse response = leaveService.create(
                dto(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12)),
                new EmployeeUserDetails(rahim));

        assertThat(response.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(response.getEmployeeId()).isEqualTo(RAHIM_ID);
        assertThat(response.getReviewedAt()).isNull();
    }

    // ---------------------------------------------------- 2. state transitions

    @Test
    @DisplayName("approve() moves a PENDING request to APPROVED and stamps reviewedAt")
    void approveMovesPendingToApproved() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);

        when(leaveRequestRepository.findByIdWithEmployee(LEAVE_ID))
                .thenReturn(Optional.of(pendingLeaveOwnedBy(rahim)));
        stubSaveReturnsArgument();

        LeaveResponse response = leaveService.approve(LEAVE_ID);

        assertThat(response.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(response.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("approve() refuses a request that has already been reviewed")
    void approveRefusesAlreadyReviewedRequest() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);
        LeaveRequest alreadyApproved = pendingLeaveOwnedBy(rahim);
        alreadyApproved.setStatus(LeaveStatus.APPROVED);

        when(leaveRequestRepository.findByIdWithEmployee(LEAVE_ID))
                .thenReturn(Optional.of(alreadyApproved));

        assertThatThrownBy(() -> leaveService.approve(LEAVE_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only a PENDING leave request can be reviewed");

        verify(leaveRequestRepository, never()).save(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("reject() refuses a request that has already been reviewed")
    void rejectRefusesAlreadyReviewedRequest() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);
        LeaveRequest alreadyRejected = pendingLeaveOwnedBy(rahim);
        alreadyRejected.setStatus(LeaveStatus.REJECTED);

        when(leaveRequestRepository.findByIdWithEmployee(LEAVE_ID))
                .thenReturn(Optional.of(alreadyRejected));

        assertThatThrownBy(() -> leaveService.reject(LEAVE_ID))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("update() refuses to edit a request that has already been reviewed")
    void updateRefusesReviewedRequest() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);
        LeaveRequest approved = pendingLeaveOwnedBy(rahim);
        approved.setStatus(LeaveStatus.APPROVED);

        when(leaveRequestRepository.findByIdWithEmployee(LEAVE_ID))
                .thenReturn(Optional.of(approved));

        LeaveRequestDto edit = dto(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11));
        EmployeeUserDetails currentUser = new EmployeeUserDetails(rahim);

        assertThatThrownBy(() -> leaveService.update(LEAVE_ID, edit, currentUser))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only a PENDING leave request can be edited");
    }

    // ------------------------------------------- 4. annual leave entitlement

    @Test
    @DisplayName("a request within the remaining entitlement is accepted")
    void createAcceptsRequestWithinEntitlement() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);

        // 20 days already booked, 7 remaining, asking for 5.
        stubExistingLeave(bookedLeave(rahim, 10L, LocalDate.of(2026, 3, 1), 20));
        when(employeeRepository.findById(RAHIM_ID)).thenReturn(Optional.of(rahim));
        stubSaveReturnsArgument();

        LeaveResponse response = leaveService.create(
                dto(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14)),
                new EmployeeUserDetails(rahim));

        assertThat(response.getStatus()).isEqualTo(LeaveStatus.PENDING);
    }

    @Test
    @DisplayName("a request that would exceed the annual entitlement is refused")
    void createRefusesRequestBeyondEntitlement() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);

        // 25 days already booked, 2 remaining, asking for 5.
        stubExistingLeave(bookedLeave(rahim, 10L, LocalDate.of(2026, 3, 1), 25));
        EmployeeUserDetails currentUser = new EmployeeUserDetails(rahim);
        LeaveRequestDto tooLong = dto(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14));

        assertThatThrownBy(() -> leaveService.create(tooLong, currentUser))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("This request is 5 day(s)")
                .hasMessageContaining("only 2 of your 27 annual leave days remaining");

        verify(leaveRequestRepository, never()).save(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("a request using the entitlement exactly to the last day is accepted")
    void createAcceptsRequestUsingTheLastRemainingDay() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);

        // 25 booked + 2 requested = exactly 27. The limit is inclusive.
        stubExistingLeave(bookedLeave(rahim, 10L, LocalDate.of(2026, 3, 1), 25));
        when(employeeRepository.findById(RAHIM_ID)).thenReturn(Optional.of(rahim));
        stubSaveReturnsArgument();

        LeaveResponse response = leaveService.create(
                dto(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11)),
                new EmployeeUserDetails(rahim));

        assertThat(response.getStatus()).isEqualTo(LeaveStatus.PENDING);
    }

    @Test
    @DisplayName("editing a request does not count that request against its own entitlement")
    void updateExcludesTheRequestBeingEditedFromTheTotal() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);
        LeaveRequest existing = pendingLeaveOwnedBy(rahim);

        /*
         * The only leave on file IS the one being edited (25 days, id = LEAVE_ID).
         * Extending it to 26 days must succeed: without the exclusion the service
         * would read 25 already used and refuse anything over 2.
         */
        LeaveRequest sameRequestAsStored = bookedLeave(rahim, LEAVE_ID, LocalDate.of(2026, 9, 1), 25);

        when(leaveRequestRepository.findByIdWithEmployee(LEAVE_ID)).thenReturn(Optional.of(existing));
        stubExistingLeave(sameRequestAsStored);
        stubSaveReturnsArgument();

        LeaveResponse response = leaveService.update(
                LEAVE_ID,
                dto(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 26)),
                new EmployeeUserDetails(rahim));

        assertThat(response.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    // ------------------------------------------------------------ 3. ownership

    @Test
    @DisplayName("findById() lets an employee read their own request")
    void ownerCanReadOwnRequest() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);

        when(leaveRequestRepository.findByIdWithEmployee(LEAVE_ID))
                .thenReturn(Optional.of(pendingLeaveOwnedBy(rahim)));

        LeaveResponse response = leaveService.findById(LEAVE_ID, new EmployeeUserDetails(rahim));

        assertThat(response.getEmployeeId()).isEqualTo(RAHIM_ID);
    }

    @Test
    @DisplayName("findById() denies an employee access to somebody else's request")
    void employeeCannotReadAnotherEmployeesRequest() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);
        Employee karim = employee(KARIM_ID, "Karim", Role.EMPLOYEE);

        when(leaveRequestRepository.findByIdWithEmployee(LEAVE_ID))
                .thenReturn(Optional.of(pendingLeaveOwnedBy(rahim)));

        // Karim asking for Rahim's request.
        EmployeeUserDetails currentUser = new EmployeeUserDetails(karim);

        assertThatThrownBy(() -> leaveService.findById(LEAVE_ID, currentUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("your own leave requests");
    }

    @Test
    @DisplayName("findById() lets an admin read anybody's request")
    void adminCanReadAnyRequest() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);
        Employee admin = employee(ADMIN_ID, "Ayesha", Role.ADMIN);

        when(leaveRequestRepository.findByIdWithEmployee(LEAVE_ID))
                .thenReturn(Optional.of(pendingLeaveOwnedBy(rahim)));

        LeaveResponse response = leaveService.findById(LEAVE_ID, new EmployeeUserDetails(admin));

        assertThat(response.getEmployeeId()).isEqualTo(RAHIM_ID);
    }

    @Test
    @DisplayName("delete() denies an employee deleting somebody else's request")
    void employeeCannotDeleteAnotherEmployeesRequest() {
        Employee rahim = employee(RAHIM_ID, "Rahim", Role.EMPLOYEE);
        Employee karim = employee(KARIM_ID, "Karim", Role.EMPLOYEE);

        when(leaveRequestRepository.findByIdWithEmployee(LEAVE_ID))
                .thenReturn(Optional.of(pendingLeaveOwnedBy(rahim)));

        EmployeeUserDetails currentUser = new EmployeeUserDetails(karim);

        assertThatThrownBy(() -> leaveService.delete(LEAVE_ID, currentUser))
                .isInstanceOf(AccessDeniedException.class);

        verify(leaveRequestRepository, never()).delete(any(LeaveRequest.class));
    }

    // ------------------------------------------------------------- not found

    @Test
    @DisplayName("findById() throws ResourceNotFoundException for an unknown id")
    void unknownIdThrowsNotFound() {
        Employee admin = employee(ADMIN_ID, "Ayesha", Role.ADMIN);

        when(leaveRequestRepository.findByIdWithEmployee(UNKNOWN_LEAVE_ID))
                .thenReturn(Optional.empty());

        EmployeeUserDetails currentUser = new EmployeeUserDetails(admin);

        assertThatThrownBy(() -> leaveService.findById(UNKNOWN_LEAVE_ID, currentUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Leave request not found with id 99");
    }
}
