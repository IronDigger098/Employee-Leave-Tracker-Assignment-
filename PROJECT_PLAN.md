# Employee Leave Tracker — Project Plan (Phase 0)

MISL Fresher Assessment • Planning document, no code yet.

---

## 0. Pinned versions

| Layer | Choice | Why |
|---|---|---|
| Java | **21 (LTS)** | Spring Boot 3.5 supports 17–25; 21 is the current mainstream LTS. |
| Spring Boot | **3.5.16** | Last patch of the 3.5 line. Enormous tutorial/doc ecosystem — every StackOverflow answer and guide you find will match this code. Spring Boot 4.x is newer but ships Spring Security 7 with API changes and far fewer learning resources. For an assessment you must *explain*, 3.5 is the lower-risk pick. |
| Spring Security | 6.5.x (managed by Boot BOM) | |
| Hibernate / JPA | 6.6.x (managed by Boot BOM) | |
| jjwt (JWT library) | **0.12.6** | Current stable API (`Jwts.builder().signWith(key)`), the version almost all modern tutorials use. |
| PostgreSQL | **16-alpine** | Stable, small image. |
| Angular | **20.3.x** | LTS-tagged, standalone components, `@if`/`@for` control flow. |
| Node (build only) | 22-alpine | |
| Maven | 3.9.x via wrapper (`mvnw`) | Wrapper means the grader needs no local Maven. |
| Nginx | 1.27-alpine | Serves the built Angular bundle and reverse-proxies `/api`. |

---

## 1. Final architecture

### 1.1 Runtime topology (3 containers)

```
                    Browser (http://localhost:4200)
                              |
                              | HTTP
                              v
        +---------------------------------------------+
        |  frontend  (nginx:alpine)          port 80   |
        |    /            -> Angular static bundle     |
        |    /api/*       -> reverse proxy ----------+ |
        +--------------------------------------------|-+
                                                     |
                                     Docker network  |  http://backend:8080
                                                     v
        +---------------------------------------------+
        |  backend  (eclipse-temurin:21-jre)  port 8080|
        |    Controller -> Service -> Repository       |
        +---------------------------------------------+
                              |
                              | JDBC  jdbc:postgresql://db:5432/leave_tracker
                              v
        +---------------------------------------------+
        |  db  (postgres:16-alpine)          port 5432 |
        +---------------------------------------------+
```

**Why nginx reverse-proxies `/api`:** the browser only ever talks to one origin
(`localhost:4200`), so there is **no CORS configuration anywhere in the project**.
One less moving part to explain and one less thing to break in the demo.

### 1.2 Layered request flow (the thing you must be able to recite)

```
Angular component (Reactive Form)
   -> Angular service (HttpClient)
   -> HTTP interceptor attaches: Authorization: Bearer <jwt>
   -> nginx  /api/*  -> backend:8080
   -> Spring Security filter chain
        -> JwtAuthenticationFilter: parse token, load user, set SecurityContext
   -> @RestController          (HTTP concerns only: map URL, bind body, status code)
   -> @Service                 (business rules, transactions, authorization checks)
   -> Spring Data JpaRepository (interface -> Spring generates the implementation)
   -> Hibernate                (JPQL/SQL generation, entity <-> row mapping)
   -> PostgreSQL
   <- Entity -> DTO mapping in the service layer
   <- ResponseEntity<DTO> -> Jackson -> JSON
   <- Angular service returns Observable -> component renders
```

### 1.3 Laravel → Spring translation table (for your own reading)

| Laravel | Spring Boot | Note |
|---|---|---|
| `routes/api.php` + Controller | `@RestController` + `@RequestMapping` | Routes are declared *on* the controller, not in a separate file. |
| Controller method | `@GetMapping` / `@PostMapping` method | |
| Service class | `@Service` class | Same idea; Spring injects it for you. |
| Eloquent Model | JPA `@Entity` | Entity is a *plain class* — no query methods on it. |
| `Model::where(...)->get()` | `repository.findByX(...)` | Query lives in the repository, not the model. |
| Repository / query builder | `interface X extends JpaRepository<T,ID>` | You write **no implementation**; Spring generates it from the method name. |
| `app/Providers` + container | Spring IoC container | `@Component`/`@Service`/`@Repository` are auto-registered; constructor injection wires them. |
| `FormRequest` validation | Bean Validation on the DTO (`@NotBlank`, `@Email`) + `@Valid` | |
| `Handler.php` (exceptions) | `@RestControllerAdvice` class | Central place that turns exceptions into JSON error responses. |
| Migrations | `spring.jpa.hibernate.ddl-auto=update` (Hibernate generates the schema) | Kept simple deliberately; Flyway would be the "real" answer. |
| `DatabaseSeeder` | `CommandLineRunner` bean | |
| `config/*.php` + `.env` | `application.yml` + `${ENV_VAR}` placeholders | |
| Sanctum/Passport | Spring Security + jjwt (hand-rolled) | |
| `Hash::make()` | `BCryptPasswordEncoder.encode()` | Same BCrypt algorithm. |
| `$request->user()` | `SecurityContextHolder.getContext().getAuthentication()` | |
| Middleware `auth:sanctum` | `JwtAuthenticationFilter` in the security filter chain | |
| `->middleware('role:admin')` | `@PreAuthorize("hasRole('ADMIN')")` | |

---

## 2. Database design

### 2.1 Tables

**`employees`** — one table serving as both the employee record and the auth user.

| Column | Type | Constraints |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `employee_code` | VARCHAR(20) | NOT NULL, UNIQUE |
| `name` | VARCHAR(100) | NOT NULL |
| `email` | VARCHAR(150) | NOT NULL, UNIQUE — this is the login identifier |
| `password` | VARCHAR(100) | NOT NULL, **BCrypt hash**, never returned by any API |
| `department` | VARCHAR(100) | NOT NULL |
| `designation` | VARCHAR(100) | NOT NULL |
| `role` | VARCHAR(20) | NOT NULL, enum `ADMIN` \| `EMPLOYEE` |
| `active` | BOOLEAN | NOT NULL, default `true` |

**`leave_requests`**

| Column | Type | Constraints |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `employee_id` | BIGINT | NOT NULL, FK → `employees(id)` |
| `leave_type` | VARCHAR(20) | NOT NULL, enum `CASUAL` \| `SICK` \| `ANNUAL` |
| `start_date` | DATE | NOT NULL |
| `end_date` | DATE | NOT NULL, must be ≥ `start_date` |
| `reason` | VARCHAR(500) | NOT NULL |
| `status` | VARCHAR(20) | NOT NULL, enum `PENDING` \| `APPROVED` \| `REJECTED`, defaults to `PENDING` |
| `created_at` | TIMESTAMP | NOT NULL, set on insert |
| `reviewed_at` | TIMESTAMP | NULL until approved/rejected |

### 2.2 Relationship

```
employees 1 ──────< N leave_requests
```

- `LeaveRequest.employee` → `@ManyToOne(fetch = LAZY)` + `@JoinColumn(name = "employee_id")`
  — this is the **owning side**; it holds the FK column.
- `Employee.leaveRequests` → `@OneToMany(mappedBy = "employee", cascade = ALL, orphanRemoval = true)`
  — inverse side. `cascade`/`orphanRemoval` so deleting an employee deletes their leaves
  instead of throwing an FK violation.
- `fetch = LAZY` on the `@ManyToOne` so listing 50 leaves doesn't silently load 50 employees.
  Where we *do* need the employee's name in a list response, we map it explicitly in the service.

### 2.3 Enums

Stored as `@Enumerated(EnumType.STRING)` — readable in the DB, and safe against reordering.
(The default, `ORDINAL`, stores `0/1/2` and breaks the moment someone reorders the enum.
That is a good thing to be able to say in the interview.)

```java
enum Role        { ADMIN, EMPLOYEE }
enum LeaveStatus { PENDING, APPROVED, REJECTED }
enum LeaveType   { CASUAL, SICK, ANNUAL }
```

---

## 3. Folder structure

```
employee-leave-tracker/
├── docker-compose.yml
├── .env.example                  # committed (placeholders only)
├── .gitignore
├── README.md
├── TECHNICAL_EXPLANATION.md
├── API_DOCUMENTATION.md
│
├── backend/
│   ├── Dockerfile                # multi-stage: maven build -> temurin JRE
│   ├── .dockerignore
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd / .mvn/   # Maven wrapper
│   └── src/main/
│       ├── java/com/misl/leavetracker/
│       │   ├── LeaveTrackerApplication.java
│       │   ├── config/
│       │   │   └── DataSeeder.java              # CommandLineRunner demo data
│       │   ├── controller/
│       │   │   ├── AuthController.java
│       │   │   ├── EmployeeController.java
│       │   │   ├── LeaveController.java
│       │   │   └── DashboardController.java
│       │   ├── service/
│       │   │   ├── AuthService.java
│       │   │   ├── EmployeeService.java
│       │   │   ├── LeaveService.java
│       │   │   └── DashboardService.java
│       │   ├── repository/
│       │   │   ├── EmployeeRepository.java
│       │   │   └── LeaveRequestRepository.java
│       │   ├── entity/
│       │   │   ├── Employee.java
│       │   │   ├── LeaveRequest.java
│       │   │   ├── Role.java
│       │   │   ├── LeaveStatus.java
│       │   │   └── LeaveType.java
│       │   ├── dto/
│       │   │   ├── LoginRequest.java / LoginResponse.java
│       │   │   ├── EmployeeRequest.java / EmployeeResponse.java
│       │   │   ├── LeaveRequestDto.java / LeaveResponse.java
│       │   │   └── AdminDashboardResponse.java / EmployeeDashboardResponse.java
│       │   ├── security/
│       │   │   ├── SecurityConfig.java
│       │   │   ├── JwtService.java
│       │   │   ├── JwtAuthenticationFilter.java
│       │   │   ├── CustomUserDetailsService.java
│       │   │   └── JwtAuthenticationEntryPoint.java
│       │   └── exception/
│       │       ├── GlobalExceptionHandler.java
│       │       ├── ResourceNotFoundException.java
│       │       ├── BadRequestException.java
│       │       └── ApiError.java
│       └── resources/
│           └── application.yml
│
└── frontend/
    ├── Dockerfile                # multi-stage: node build -> nginx
    ├── nginx.conf                # SPA fallback + /api reverse proxy
    ├── .dockerignore
    ├── package.json
    ├── angular.json
    ├── tsconfig.json
    ├── proxy.conf.json           # for `ng serve` local dev only
    └── src/
        ├── index.html
        ├── main.ts
        ├── styles.css
        └── app/
            ├── app.ts / app.html / app.config.ts / app.routes.ts
            ├── core/
            │   ├── models/      auth.model.ts, employee.model.ts, leave.model.ts
            │   ├── services/    auth.service.ts, employee.service.ts, leave.service.ts
            │   ├── guards/      auth.guard.ts, role.guard.ts
            │   └── interceptors/ auth.interceptor.ts
            ├── layout/
            │   └── navbar/
            └── pages/
                ├── login/
                ├── admin/
                │   ├── admin-dashboard/
                │   ├── admin-employees/
                │   └── admin-leaves/
                └── employee/
                    ├── employee-dashboard/
                    ├── employee-leaves/
                    └── leave-form/
```

---

## 4. API list

Base path `/api`. All endpoints except `/api/auth/login` require
`Authorization: Bearer <token>`.

### Auth
| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/login` | public | Validate email+password, return JWT + user info |

### Employees
| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/api/employees` | ADMIN | List all employees |
| GET | `/api/employees/{id}` | ADMIN | Get one employee |
| POST | `/api/employees` | ADMIN | Create employee (password BCrypt-hashed) |
| PUT | `/api/employees/{id}` | ADMIN | Update employee (password optional — blank = unchanged) |
| DELETE | `/api/employees/{id}` | ADMIN | Delete employee (cascades their leaves) |

### Leaves
| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/api/leaves` | ADMIN | All leave requests, newest first |
| GET | `/api/leaves/my` | authenticated | **Own** leave requests only |
| GET | `/api/leaves/{id}` | ADMIN, or owner | One leave request |
| POST | `/api/leaves` | EMPLOYEE | Submit a leave request → status `PENDING` |
| PUT | `/api/leaves/{id}` | owner, only while `PENDING` | Edit own pending request |
| DELETE | `/api/leaves/{id}` | ADMIN, or owner while `PENDING` | Withdraw / delete |
| PATCH | `/api/leaves/{id}/approve` | ADMIN | `PENDING` → `APPROVED`, stamp `reviewedAt` |
| PATCH | `/api/leaves/{id}/reject` | ADMIN | `PENDING` → `REJECTED`, stamp `reviewedAt` |

### Dashboard
| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/api/dashboard/admin` | ADMIN | `{ totalEmployees, pending, approved, rejected }` |
| GET | `/api/dashboard/employee` | authenticated | own `{ total, pending, approved, rejected }` |

### Status codes used
| Code | When |
|---|---|
| 200 OK | successful GET / PUT / PATCH |
| 201 Created | successful POST |
| 204 No Content | successful DELETE |
| 400 Bad Request | validation failure, or illegal state (e.g. approving a non-PENDING leave) |
| 401 Unauthorized | missing / invalid / expired token, or wrong login credentials |
| 403 Forbidden | valid token but wrong role, or accessing another employee's data |
| 404 Not Found | id does not exist |
| 409 Conflict | duplicate email or employeeCode |

### Validation rules (enforced on the backend — Angular mirrors them for UX)
- **Employee:** `employeeCode` not blank; `name` not blank; `email` not blank + `@Email`;
  `password` ≥ 6 chars on create; `department`, `designation`, `role` required.
  Duplicate email/employeeCode → 409.
- **Leave:** `leaveType` required; `startDate` and `endDate` required;
  `endDate` ≥ `startDate` (checked in the service, since it spans two fields);
  `reason` not blank, ≤ 500 chars.
- **Transitions:** approve/reject only allowed when current status is `PENDING`; otherwise 400.
- **Ownership:** an EMPLOYEE reading/editing a leave whose `employee_id` ≠ their own id → 403.

---

## 5. Implementation order

Each phase = one reviewable chunk = one git commit. After each, I explain what was
built, the code that matters, how to run/test it, and the interview talking points.

| # | Phase | Deliverable | Commit message |
|---|---|---|---|
| 1 | Repo skeleton | folders, `.gitignore`, `.env.example` | `chore: initial project structure` |
| 2 | Backend skeleton | `pom.xml`, wrapper, main class, `application.yml`, entities, enums | `feat(backend): spring boot setup with jpa entities` |
| 3 | Employee CRUD | repository, DTOs, service, controller, exception handling, validation | `feat(backend): employee CRUD with validation` |
| 4 | Leave management | leave repo/DTOs/service/controller, `/my`, approve, reject | `feat(backend): leave request management` |
| 5 | JWT auth | security package, login endpoint, filter chain | `feat(backend): JWT authentication` |
| 6 | Role authorization + seeder | `@PreAuthorize`, ownership checks, dashboard, demo data | `feat(backend): role-based authorization and demo seed data` |
| 7 | Angular core | project, models, services, interceptor, guards, routes | `feat(frontend): angular setup with core services and guards` |
| 8 | Login + admin UI | `/login`, admin dashboard, employees, leaves | `feat(frontend): login and admin dashboard` |
| 9 | Employee UI | employee dashboard, my leaves, new leave form | `feat(frontend): employee leave pages` |
| 10 | Docker | both Dockerfiles, nginx.conf, `docker-compose.yml` | `feat: dockerize frontend, backend and postgres` |
| 11 | Documentation | README, TECHNICAL_EXPLANATION, API_DOCUMENTATION | `docs: add readme, technical explanation and api docs` |
| 12 | Final QA | walk the 15-point checklist, cleanup | `chore: final testing and cleanup` |

### Verification points
- **After phase 6** (backend complete): `mvn spring-boot:run` on your machine +
  a Postman/curl script I'll provide, exercising every endpoint and every 401/403/400 case.
- **After phase 9** (frontend complete): I run `npm run build` in my sandbox to prove it compiles.
- **After phase 10**: `docker compose up --build` on your machine — the real end-to-end test.

---

## 6. Demo credentials (to be seeded, documented in README)

| Role | Email | Password |
|---|---|---|
| ADMIN | `admin@misl.com` | `admin123` |
| EMPLOYEE | `rahim@misl.com` | `employee123` |
| EMPLOYEE | `karim@misl.com` | `employee123` |

Seeded with BCrypt hashes generated at startup, guarded by a row-count check so it
only runs on an empty database. Clearly labelled **development/demo credentials only**.

---

## 7. Secrets policy

- `.env.example` is committed with **placeholder** values.
- `.env` is git-ignored.
- `docker-compose.yml` reads `${POSTGRES_PASSWORD}`, `${JWT_SECRET}` etc. with
  safe defaults so `docker compose up --build` works out of the box for the grader.
- No real secret, key, or password is ever committed.

---

## 8. Deliberately NOT included (and why)

So you can answer "why didn't you do X?" confidently:

| Left out | Reason |
|---|---|
| Refresh tokens | Single short-lived access token is enough for the scope; refresh flow adds a token store and rotation logic to explain. |
| Flyway/Liquibase migrations | `ddl-auto=update` keeps setup to zero steps for the grader. Flyway is the production answer. |
| MapStruct / ModelMapper | Hand-written mapping methods are ~10 lines and completely transparent. |
| Lombok | You must explain every line; generated code you can't see is a liability in a viva. Constructors and getters are written out. |
| Pagination | Dataset is a handful of rows; `Pageable` would be noise. Easy to mention as the next step. |
| Unit/integration tests | Not in the assignment's criteria. If time remains on day 3, a couple of service tests are the highest-value addition. |
| Redis / Kafka / microservices | Explicitly out of scope per the brief. |
