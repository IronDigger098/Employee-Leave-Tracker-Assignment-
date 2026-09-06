package com.misl.leavetracker.repository;

import com.misl.leavetracker.entity.Employee;
import com.misl.leavetracker.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link Employee}.
 *
 * Note what is NOT here: no implementation class, no SQL, no EntityManager.
 * Spring Data JPA creates a proxy implementing this interface at startup and
 * derives the query from each METHOD NAME:
 *
 *   findByEmail(String)              -> SELECT * FROM employees WHERE email = ?
 *   countByDeletedFalse()            -> SELECT COUNT(*) FROM employees WHERE deleted = false
 *   existsByEmailAndIdNot(String, Long)
 *                                    -> ... WHERE email = ? AND id <> ?
 *
 * Extending JpaRepository<Employee, Long> already gives us save(), findAll(),
 * findById(), deleteById(), count() and more for free.
 *
 * Laravel comparison: this replaces both the Eloquent query builder calls
 * (Employee::where('email', $e)->first()) and a hand-written repository class.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /** Used by login and by the JWT filter to load the authenticated user. */
    Optional<Employee> findByEmail(String email);

    /**
     * Used when a create is rejected as a duplicate, to explain WHY.
     *
     * An archived employee still occupies their email and code - the row is kept
     * so their leave history stays attached to a real person. Without loading the
     * offending row we could only say "already exists", which reads as a bug to an
     * admin looking at a list that does not contain them.
     */
    Optional<Employee> findByEmployeeCode(String employeeCode);

    /**
     * The working roster - everyone except archived employees.
     *
     * "DeletedFalse" is a derived-query keyword: Spring Data reads it as
     * WHERE deleted = false. Archived employees still exist and their leave
     * history is intact; they are simply not part of the active staff list.
     */
    List<Employee> findByDeletedFalseOrderByNameAsc();

    /** Archived employees only, for the "show archived" view. */
    List<Employee> findByDeletedTrueOrderByNameAsc();

    /** Headcount for the admin dashboard - archived staff are not employees. */
    long countByDeletedFalse();

    /**
     * How many administrators can actually log in right now.
     *
     * Used to stop the last one being archived, demoted or deactivated. Without
     * this the application can be locked out permanently: creating an ADMIN
     * requires being an ADMIN, so once the final one is gone nobody can make
     * another, and the only way back in is editing the database by hand.
     *
     * Both flags matter. `deleted` alone is not enough - an admin with
     * active = false is equally unable to sign in, so they cannot be the one
     * administrator the system is relying on.
     */
    long countByRoleAndActiveTrueAndDeletedFalse(Role role);

    /*
     * CREATE has no existsBy... pair: it uses findByEmail / findByEmployeeCode
     * above instead, because a duplicate has to be explained, not just detected.
     */

    /**
     * Uniqueness checks on UPDATE.
     * "AndIdNot" excludes the row being edited, so saving an employee without
     * changing their email does not report a false duplicate.
     */
    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByEmployeeCodeAndIdNot(String employeeCode, Long id);
}
