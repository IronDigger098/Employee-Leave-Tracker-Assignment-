# Technical Explanation

How the Employee Leave Tracker works internally — the reasoning behind the
structure, not just a description of it.

---

## 1. Frontend / backend interaction

The two applications are completely separate programs. They share nothing but a
JSON contract over HTTP.

- The **frontend** is compiled to static files (HTML, JS, CSS) and served by nginx.
  It holds no business logic that matters — everything it enforces is re-enforced
  on the server.
- The **backend** is a stateless REST API. It serves no HTML and knows nothing about
  Angular.

That separation means each side can be tested and replaced independently: the API
can be exercised entirely with curl or Postman, and the UI could be rewritten in
React without touching a line of Java.

### The single-origin decision

Angular calls **relative** URLs — `/api/leaves`, never `http://localhost:8080/api/leaves`.
Something must map `/api` onto the backend:

| Environment | Who proxies | Configured in |
|---|---|---|
| Docker | nginx in the frontend container | `frontend/nginx.conf` |
| `ng serve` | the Angular dev server | `frontend/proxy.conf.json` |

Either way the browser makes every request to a single origin and never sees port
8080. Because the origin never changes, the browser's same-origin policy is never
triggered, and **the project contains no CORS configuration anywhere**.

The alternative — calling `http://localhost:8080` directly from the browser — would
have required a `CorsConfigurationSource` bean on the Spring side, an allowed-origins
list to maintain, and correct handling of preflight `OPTIONS` requests. Proxying
removes that entire category of problem.

---

## 2. Request flow, end to end

Following one real request: an admin approving leave request 5.

```
1.  Admin clicks "Approve" in admin-leaves.html
        │
2.  AdminLeaves.approve() → LeaveService.approve(5)     [Angular service]
        │   this.http.patch('/api/leaves/5/approve', {})
        │
3.  authInterceptor clones the request, adding
        │   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
        │
4.  Browser → nginx :4200
        │   location /api/ → proxy_pass http://backend:8080
        │
5.  Spring Security filter chain
        │   JwtAuthenticationFilter:
        │     - reads the Bearer header
        │     - verifies signature + expiry  (JwtService)
        │     - loads the employee by email  (CustomUserDetailsService)
        │     - puts an Authentication into the SecurityContext
        │
6.  authorizeHttpRequests → anyRequest().authenticated()   ✓
        │
7.  @PreAuthorize("hasRole('ADMIN')") on LeaveController.approve
        │   fails here → AccessDeniedException → 403
        │
8.  LeaveController.approve(5) → LeaveService.approve(5)
        │
9.  LeaveService.review(5, APPROVED)
        │     - loads the row (JOIN FETCH, so employee is available)
        │     - guards: status must be PENDING, else BadRequestException → 400
        │     - sets status = APPROVED, reviewedAt = now
        │     - repository.save(...)
        │
10. Hibernate flushes an UPDATE at transaction commit
        │
11. Entity → LeaveResponse DTO (employee flattened to 3 scalar fields)
        │
12. Jackson serialises to JSON → 200
        │
13. Angular swaps that one row in the signal:
        this.leaves.update(rows => rows.map(r => r.id === updated.id ? updated : r))
        │
14. The template re-renders that row automatically — badge turns green,
    "Reviewed" gains a timestamp, the buttons disappear.
```

Every failure along the way produces the same JSON error shape, so the frontend has
exactly one error-handling path rather than one per endpoint.

---

## 3. Controller → Service → Repository

Each layer has one job, and depends only on the layer beneath it.

### Controller — HTTP only

```java
@PostMapping
public ResponseEntity<LeaveResponse> create(
        @Valid @RequestBody LeaveRequestDto request,
        @AuthenticationPrincipal EmployeeUserDetails currentUser) {
    LeaveResponse created = leaveService.create(request, currentUser);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
}
```

Maps a URL to a method call and chooses a status code. It contains no rules, touches
no repository, and has no `try`/`catch` — exception handling is centralised.

- `@RequestBody` — Jackson deserialises the JSON into the DTO
- `@Valid` — runs Bean Validation **before** the method body; on failure the method
  is never entered
- `@AuthenticationPrincipal` — injects the user that `JwtAuthenticationFilter` placed
  in the SecurityContext

### Service — the rules

Everything that could be got wrong lives here, so it holds regardless of caller:

- **Cross-field validation** — `endDate` before `startDate` compares two fields, so
  no per-field annotation can express it
- **Not in the past** — `startDate` may not precede today. Worth noting *why* this is
  a service rule and not `@FutureOrPresent` on the DTO: the annotation exists and
  would work, but every other date rule here already lives in the service, and
  splitting them across two layers means a reader has to look in two places to learn
  what a valid request is. The client mirrors it with a real Angular validator, not
  the `min` attribute on the date input — Angular sets `novalidate` on forms it
  manages, so `min` only greys out days in the native picker and a typed value walks
  straight past it
- **State transitions** — "only a `PENDING` request may be reviewed" depends on what
  is currently in the database, which the incoming payload cannot know
- **Ownership** — "is this row yours?" requires the row to be loaded first
- **No overlapping dates** — a new request may not clash with one the employee
  already holds. The repository asks for rows where
  `existing.startDate <= newEnd AND existing.endDate >= newStart`, which is easiest
  to trust as the negation of "one range finishes before the other begins". Both
  comparisons are inclusive, so back-to-back leaves are allowed and same-day ones
  are not. This check runs **before** the entitlement check, and the ordering is
  load-bearing: the entitlement sums the length of each request, which only measures
  real days off if no two requests cover the same day. Without the overlap rule,
  21–29 Sep plus 25 Sep–1 Oct would be charged as 9 + 7 = 16 days for what is really
  11 days away from work
- **Annual entitlement** — 27 days per calendar year. This one depends on the
  employee's *other* rows: the service sums every `APPROVED` and `PENDING` request
  whose start date falls in that year and refuses anything that would push the total
  over. Two details matter. Pending days count, or a burst of unreviewed requests
  could slip past the limit and land the problem on the admin. And when *editing* a
  request, that request's own days are excluded from the running total — otherwise
  extending a 5-day leave by one day would be measured as asking for 6 days on top
  of the 5 already held. The limit is a configuration property
  (`app.leave.annual-entitlement-days`), not a constant, because an entitlement is a
  company policy rather than a fact about the code
- **Transactions** — `@Transactional` marks the unit of work; `readOnly = true` on
  queries lets Hibernate skip dirty-checking

### Repository — data access, without an implementation

```java
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);
}
```

There is **no implementation class anywhere in the project**. At startup Spring Data
parses the method names and generates a proxy:

- `findByEmail` → `WHERE email = ?`
- `existsByEmailAndIdNot` → `WHERE email = ? AND id <> ?`

That second one exists for a specific bug: when updating an employee, asking "does
this email already exist?" would find *the row being edited* and wrongly report a
duplicate. Excluding the current id fixes it.

### Why DTOs instead of returning entities

`EmployeeResponse` has no `password` field. Because every controller returns
`EmployeeResponse` and never `Employee`, there is **no code path that can leak a
password hash** — it is structurally impossible rather than merely remembered.

The same logic applies inbound. `LeaveRequestDto` deliberately omits `employeeId`
and `status`:

- if the client could send `employeeId`, anyone could file leave in a colleague's name
- if the client could send `status`, anyone could approve their own leave

**Leaving a field out of the DTO is the cheapest access control there is** — there is
no check to write and none to forget.

DTOs also stop infinite recursion: serialising the entity directly would follow
`employee → leaveRequests → employee → …` forever. Flattening in the service avoids
that, which is why the project needs no `@JsonIgnore` anywhere.

---

## 4. JWT authentication

### What a token actually is

```
header . payload . signature
```

- **header** — which algorithm signed it, `{"alg":"HS256"}`
- **payload** — the claims: `sub` (email), `employeeId`, `role`, `iat`, `exp`
- **signature** — HMAC-SHA256 over `header.payload` using the server's secret

The payload is **base64-encoded, not encrypted**. Anyone can paste a token into
jwt.io and read it — which is exactly why no password or other secret is ever placed
in a claim.

What the signature guarantees is that nobody **edited** the payload. Change one
character and the recomputed signature no longer matches, and producing a valid
replacement requires the secret key, which only the server holds.

That is what makes the API stateless: the server stores no sessions and can trust a
token purely because it verifies. Any instance can serve any request, because no
instance holds session state — which is what `SessionCreationPolicy.STATELESS` in
`SecurityConfig` declares.

### The trade-off worth naming

A JWT is valid until it expires and cannot be revoked, because there is no server-side
record of it. A database-backed session or an opaque token can be deleted instantly.
This project accepts that trade-off for a 24-hour token, and mitigates the worst of it
in the filter:

```java
UserDetails userDetails = userDetailsService.loadUserByUsername(email);
```

The filter **reloads the employee from the database on every request** rather than
trusting the `role` claim inside the token. That costs one query per request and buys
correctness: an employee deactivated or demoted a minute ago loses access on their
very next request, instead of when their token expires the next day.

### Where the pieces live

| Class | Responsibility |
|---|---|
| `JwtService` | Create and verify tokens; holds the signing key |
| `JwtAuthenticationFilter` | Per-request: header → verified token → `SecurityContext` |
| `CustomUserDetailsService` | Loads an `Employee` and wraps it as `UserDetails` |
| `EmployeeUserDetails` | The bridge between our entity and Spring Security's contract |
| `JwtAuthenticationEntryPoint` | Produces the JSON 401 for unauthenticated requests |
| `SecurityConfig` | Assembles the filter chain and the public/protected rules |

**Why `EmployeeUserDetails` is a separate class** rather than making `Employee`
implement `UserDetails`: `Employee` is a persistence concern (JPA annotations,
columns) and `UserDetails` is a security concern (authorities, account flags).
Merging them would put `getAuthorities()` and `isAccountNonLocked()` on an `@Entity`,
each needing `@Transient`. Two small classes are cleaner than one confused one.

**Why `JwtAuthenticationEntryPoint` is needed at all:** `@RestControllerAdvice` only
sees exceptions thrown from inside a controller. An unauthenticated request is
rejected by the *filter chain*, before any controller is chosen — so without this bean
Spring Security would return an empty 401 body and the Angular error handler would
have nothing to read.

### BCrypt

```java
passwordEncoder.matches(rawPassword, storedHash)   // correct
passwordEncoder.encode(rawPassword).equals(storedHash)   // always false
```

BCrypt generates a **random salt per password** and embeds it in the 60-character
hash. Two employees with the same password get different hashes, and encoding the
same input twice never produces the same output. Verification must therefore use
`matches()`, which extracts the salt from the stored hash before comparing.

---

## 5. Role-based authorization

Authorization is enforced at two levels, deliberately, because the two questions have
different shapes.

### Role — static, on the controller

```java
@RestController
@RequestMapping("/api/employees")
@PreAuthorize("hasRole('ADMIN')")     // applies to every method in the class
public class EmployeeController { ... }
```

`@PreAuthorize` runs **before** the method body. It can answer "is this caller an
admin?" without touching the database, and putting it on the class means an endpoint
added later is protected by default rather than accidentally left open.

Spring Security has no separate notion of a "role" — a role is an authority whose
name begins with `ROLE_`. That prefix is added in `EmployeeUserDetails.getAuthorities()`,
which is why `hasRole('ADMIN')` and `hasAuthority('ROLE_ADMIN')` mean the same thing.

> `@EnableMethodSecurity` on `SecurityConfig` is what makes these annotations work.
> Without it every `@PreAuthorize` in the project is **silently ignored** — the code
> compiles, the app starts, and no role is ever checked.

### Ownership — dynamic, in the service

```java
private void checkOwnership(LeaveRequest leaveRequest, EmployeeUserDetails currentUser) {
    if (currentUser.isAdmin()) return;
    if (!leaveRequest.getEmployee().getId().equals(currentUser.getId())) {
        throw new AccessDeniedException("You can only access your own leave requests");
    }
}
```

`@PreAuthorize` cannot express this: deciding whether leave request 7 belongs to you
requires **loading row 7 first**. So the check lives in the service, next to the data
it protects.

It throws Spring Security's own `AccessDeniedException` rather than a custom type, so
one handler in `GlobalExceptionHandler` converts both sources of denial into `403`.

### 401 versus 403

- **401 Unauthorized** — "I do not know who you are." No token, or an invalid one.
- **403 Forbidden** — "I know who you are, and you may not do this."

There are two separate 401 producers, because there are two stages at which identity
can fail: `JwtAuthenticationEntryPoint` (filter chain, bad or missing token) and
`InvalidCredentialsException` (inside `AuthService`, wrong password at login).

### Defence in depth

The strongest privacy guarantee is not a check at all. `GET /api/leaves/my` takes the
employee id **from the token**, so the request contains no id to tamper with — there
is no URL to edit and no body field to change.

The Angular route guards are a **usability** layer on top of all this. Anyone can edit
localStorage and reach `/admin/employees`; what stops them is that every API call on
that page returns 403 and no data ever appears. The guard's job is to keep ordinary
users out of pages that would only show them errors.

---

## 6. JPA and Hibernate

**JPA** is the specification (the `jakarta.persistence` annotations). **Hibernate** is
the implementation that actually generates SQL. **Spring Data JPA** sits above both and
generates repository implementations from method names.

### Entity ⇄ table

```java
@Entity
@Table(name = "employees",
       uniqueConstraints = @UniqueConstraint(name = "uk_employees_email", columnNames = "email"))
public class Employee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    ...
}
```

`ddl-auto=update` makes Hibernate emit the DDL at startup — visible in the logs as
`create table employees ...` and
`alter table leave_requests add constraint fk_leave_requests_employee ...`.

`GenerationType.IDENTITY` delegates id generation to the database column
(Postgres `BIGSERIAL`); Hibernate reads the generated value back after the insert.

### `EnumType.STRING`, not the default

```java
@Enumerated(EnumType.STRING)
private LeaveStatus status;
```

JPA's default is `ORDINAL`, which stores the enum's *index* — `0`, `1`, `2`. Insert a
new constant in the middle of the enum one day and every existing row silently changes
meaning. `STRING` stores `'APPROVED'` and is immune to reordering.

### Lazy loading, `open-in-view`, and N+1

`LeaveRequest.employee` is `fetch = LAZY`, so loading a leave request does not
automatically load its employee.

`application.yml` sets `spring.jpa.open-in-view: false`. Spring Boot defaults this to
**true**, which keeps the Hibernate session open until the HTTP response is written —
hiding `LazyInitializationException` bugs and holding a database connection for the
whole request. With it off, every lazy association must be resolved inside a
`@Transactional` service method, which is where it belongs.

That combination creates the classic **N+1 problem**: `findAll()` returns 20 rows, and
reading `employee.getName()` while mapping each one fires 20 extra `SELECT`s — 21
queries for one screen. The fix:

```java
@Query("SELECT l FROM LeaveRequest l JOIN FETCH l.employee ORDER BY l.createdAt DESC")
List<LeaveRequest> findAllWithEmployee();
```

`JOIN FETCH` loads the employees in the *same* query. With `show-sql: true` you can
watch it happen — the admin leave screen produces a single statement:

```sql
select ... from leave_requests lr1_0
join employees e1_0 on e1_0.id = lr1_0.employee_id
order by lr1_0.created_at desc
```

The employee's own list needs no `JOIN FETCH`: every row belongs to the same person,
so Hibernate loads that employee once and serves the rest from the persistence context.

### Dirty checking and transactions

Inside a `@Transactional` method, an entity loaded from a repository is *managed*.
Changing a field marks it dirty, and Hibernate flushes an `UPDATE` at commit — no
`save()` call is strictly required. The services call `save()` anyway, because it makes
the intent obvious to a reader who does not know that rule.

### No cascade — and why that is the point

```java
@OneToMany(mappedBy = "employee")
private List<LeaveRequest> leaveRequests = new ArrayList<>();
```

`mappedBy` marks this as the **inverse** side — `LeaveRequest.employee` owns the
foreign key, so this side creates no extra column.

There is deliberately **no `cascade` and no `orphanRemoval`**. An earlier version had
`cascade = CascadeType.ALL, orphanRemoval = true`, which is the reflex answer to
"deleting an employee fails on a foreign-key constraint". It is the wrong answer here.

A leave request is not merely data *about* a person. It is the record of a decision the
company made: who asked for what, who approved it, and when. HR has to be able to
answer "how much leave did this person take in 2026?" long after they have left, for
payroll and for audit. Cascading the delete would erase exactly the evidence that
matters, and it would do it silently.

So `DELETE /api/employees/{id}` performs a **soft delete** instead:

```java
employee.setDeleted(true);
employee.setActive(false);   // archived implies no access
employeeRepository.save(employee);
```

Removing the cascade also makes the safe behaviour the *default*. If someone later
calls `employeeRepository.delete(...)` directly — bypassing the service — PostgreSQL
now rejects it with a foreign-key violation rather than quietly destroying history. The
database becomes the last line of defence, not an accomplice.

**Consequences that follow from the flag**

| Concern | How it is handled |
|---|---|
| Staff list | `findByDeletedFalseOrderByNameAsc()` — archived rows are hidden by default |
| Reaching an archived record | `GET /api/employees?includeArchived=true` |
| Login | `active` is forced to `false`, so the existing active check blocks them |
| Editing | `update()` throws `400` — an archived record is history, not a live row |
| Headcount | `DashboardService` uses `countByDeletedFalse()`, not `count()` |
| Leave history | Untouched. It is still returned by every `/api/leaves` endpoint |

**Adding the column to a live table.** `ddl-auto: update` can only add a `NOT NULL`
column to a table that already has rows if the column has a default, hence:

```java
@Column(columnDefinition = "boolean not null default false")
private boolean deleted = false;
```

Without the `default false`, Hibernate would try `ALTER TABLE ... ADD COLUMN deleted
boolean not null` and PostgreSQL would refuse it on any existing employee row.

**Why the verb is still `DELETE`.** `DELETE` is the REST verb for "remove this from the
collection", which is precisely what the caller means and what the assignment
specifies. Whether removal is implemented as a row deletion or a flag is the server's
business. Renaming the endpoint to `/archive` would leak an implementation detail into
the public contract.

**Laravel comparison.** This is what `SoftDeletes` gives you for free: a `deleted_at`
column, a global scope that hides trashed rows, and `withTrashed()` to see them. Spring
Data has no equivalent built in, so the three pieces are written by hand — the flag, the
`findByDeletedFalse...` queries, and the `includeArchived` parameter. Hibernate's
`@SQLDelete` + `@Where` can approximate it, but a hidden global filter is a trap the
first time you genuinely need the archived rows, so an explicit boolean is clearer here.

`EmployeeServiceTest` pins the behaviour down:

```java
verify(employeeRepository, never()).delete(any(Employee.class));
verify(employeeRepository, never()).deleteById(any(Long.class));
```

If anyone ever swaps the soft delete back for a real one, that test fails immediately
rather than the loss being discovered months later when someone asks for a leave report.

---

## 7. Docker architecture

```
                    Host machine
        ┌──────────────────────────────────────────┐
        │  localhost:4200 ──► frontend container    │
        │  localhost:8080 ──► backend container     │
        └────────────┬─────────────────────────────┘
                     │  docker network (internal DNS)
   ┌─────────────────┼──────────────────┬──────────────────┐
   │                 │                  │                  │
┌──▼────────┐   ┌────▼──────┐     ┌─────▼─────┐            │
│ frontend  │──►│  backend  │────►│    db     │◄─ postgres-data
│  nginx    │   │  Spring   │     │ Postgres  │    (named volume)
│  :80      │   │  :8080    │     │  :5432    │
└───────────┘   └───────────┘     └───────────┘
```

### Multi-stage builds

Both images separate *build tooling* from *runtime*.

**Backend** — stage 1 is Maven + JDK and produces a jar; stage 2 starts from a plain
JRE and copies in only that jar. No compiler, no source, no `~/.m2` cache in the
shipped image, and it runs as a non-root user.

**Frontend** — stage 1 is Node running `ng build`; stage 2 is nginx with only
`dist/frontend/browser`. The final image contains no Node runtime and no
`node_modules`. Shipping `ng serve` instead would mean running a development server —
a file watcher with unminified output — in production.

### Layer caching

```dockerfile
COPY pom.xml .
RUN mvn -B dependency:go-offline    # cached while pom.xml is unchanged
COPY src ./src
RUN mvn -B clean package -DskipTests
```

Docker caches each instruction and reuses it while its inputs are unchanged.
Dependencies change rarely; source changes constantly. Copying everything at once
would re-download the whole dependency tree on every one-line edit. The frontend does
the same with `package.json` + `npm ci`.

`.dockerignore` matters for correctness as well as speed: copying the host's
`node_modules` into the image would overwrite the container's correctly-built tree
with packages compiled for a different platform.

### Service names are hostnames

```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/leave_tracker
```
```nginx
proxy_pass http://backend:8080;
```

`db` and `backend` are the **service names** from `docker-compose.yml`, resolved by
Docker's embedded DNS to the right container. Using `localhost` here is the single
most common mistake when containerising a multi-service application — inside the
backend container, `localhost` is the backend itself, where no database is listening.

### Ordering: started ≠ ready

```yaml
depends_on:
  db:
    condition: service_healthy
```

Plain `depends_on` waits only for the container to **start**. Postgres needs several
more seconds to initialise and accept connections, so without the healthcheck the
backend wins the race, fails to connect, and exits. `pg_isready` in the healthcheck is
what makes "ready" observable.

### Configuration and persistence

Every setting is an environment variable with a development default:

```yaml
POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-postgres}
JWT_SECRET: ${JWT_SECRET:-dev-only-secret-change-me-in-production-0123456789abcdef}
```

`${VAR:-default}` means the project runs immediately with no `.env` file, while any
value can be overridden without editing code. `.env` is git-ignored; only
`.env.example` with placeholders is committed.

The `postgres-data` named volume keeps the database outside the container's writable
layer, so data survives `docker compose down`. `docker compose down -v` removes it and
the seeder repopulates on the next start.

### The nginx SPA fallback

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

`/admin/employees` exists only in Angular's router, not as a file on disk. Without
this line, clicking a link works (no server request is made) but refreshing the page
returns 404 — the classic "works until you press F5" SPA bug. `try_files` serves the
real file if it exists and otherwise hands back `index.html`, letting Angular read the
URL and route accordingly.
