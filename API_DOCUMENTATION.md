# API Documentation

Base URL

| Environment | URL |
|---|---|
| Through the app (Docker or `ng serve`) | `http://localhost:4200/api` |
| Directly against the backend | `http://localhost:8080/api` |

All request and response bodies are `application/json`.

Every endpoint **except `POST /api/auth/login`** requires an `Authorization` header:

```
Authorization: Bearer <token>
```

---

## Common error shape

Every failure — validation, auth, not-found, conflict — returns the same object, so a
client needs one error handler rather than one per endpoint.

```json
{
  "timestamp": "2026-09-04T05:46:41.975876818",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/leaves",
  "fieldErrors": {
    "reason": "Reason is required",
    "leaveType": "Leave type is required"
  }
}
```

`fieldErrors` is present **only** on Bean Validation failures; it is omitted entirely
otherwise.

### Status codes used

| Code | When |
|---|---|
| `200 OK` | Successful `GET`, `PUT`, `PATCH` |
| `201 Created` | Successful `POST` |
| `204 No Content` | Successful `DELETE` |
| `400 Bad Request` | Validation failure, or an illegal state change |
| `401 Unauthorized` | Missing / invalid / expired token, or wrong login credentials |
| `403 Forbidden` | Authenticated, but wrong role or not the owner |
| `404 Not Found` | No resource with that id |
| `409 Conflict` | Duplicate email or employee code |

---

# Authentication

## `POST /api/auth/login`

Validates credentials and returns a JWT. The only public endpoint.

**Request**

```json
{
  "email": "admin@misl.com",
  "password": "admin123"
}
```

**Response — `200 OK`**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBtaXNsLmNvbSIsImVtcGxveWVlSWQiOjEsInJvbGUiOiJBRE1JTiIsImlhdCI6MTc4ODU0MjMwMSwiZXhwIjoxNzg4NjI4NzAxfQ.xxxxx",
  "employeeId": 1,
  "name": "Ayesha Rahman",
  "email": "admin@misl.com",
  "role": "ADMIN"
}
```

The token is valid for 24 hours (`JWT_EXPIRATION_MS`). The extra fields let the client
render the navbar and choose a landing route without decoding the token.

**Errors**

| Code | Cause |
|---|---|
| `400` | Blank email or password |
| `401` | Unknown email, wrong password, **or a deactivated account** — all three return the same message, `"Invalid email or password"`, so the endpoint reveals nothing about which accounts exist |

---

# Employees

All employee endpoints require role `ADMIN`. A valid token with role `EMPLOYEE`
returns `403`.

## `GET /api/employees`

List every employee.

**Response — `200 OK`**

```json
[
  {
    "id": 1,
    "employeeCode": "ADM001",
    "name": "Ayesha Rahman",
    "email": "admin@misl.com",
    "department": "Human Resources",
    "designation": "HR Manager",
    "role": "ADMIN",
    "active": true
  },
  {
    "id": 2,
    "employeeCode": "EMP001",
    "name": "Rahim Uddin",
    "email": "rahim@misl.com",
    "department": "Engineering",
    "designation": "Software Engineer",
    "role": "EMPLOYEE",
    "active": true
  }
]
```

> Note the absence of `password`. No employee endpoint ever returns the hash — the
> response DTO has no such field, so it cannot be leaked by accident.

---

## `GET /api/employees/{id}`

**Response — `200 OK`** — a single object in the shape above.

| Code | Cause |
|---|---|
| `404` | `{"message": "Employee not found with id 99"}` |

---

## `POST /api/employees`

Create an employee. The password is BCrypt-hashed before storage.

**Request**

```json
{
  "employeeCode": "EMP003",
  "name": "Nusrat Jahan",
  "email": "nusrat@misl.com",
  "password": "employee123",
  "department": "Finance",
  "designation": "Accounts Officer",
  "role": "EMPLOYEE",
  "active": true
}
```

**Response — `201 Created`**

```json
{
  "id": 4,
  "employeeCode": "EMP003",
  "name": "Nusrat Jahan",
  "email": "nusrat@misl.com",
  "department": "Finance",
  "designation": "Accounts Officer",
  "role": "EMPLOYEE",
  "active": true
}
```

**Validation**

| Field | Rule |
|---|---|
| `employeeCode` | required, ≤ 20 characters, unique |
| `name` | required, ≤ 100 characters |
| `email` | required, valid email format, ≤ 150 characters, unique |
| `password` | **required on create**, 6–100 characters |
| `department` | required, ≤ 100 characters |
| `designation` | required, ≤ 100 characters |
| `role` | required, `ADMIN` or `EMPLOYEE` |
| `active` | optional, defaults to `true` |

| Code | Cause |
|---|---|
| `400` | Any rule above fails — see `fieldErrors` |
| `409` | `"An employee with email nusrat@misl.com already exists"` |

---

## `PUT /api/employees/{id}`

Update an employee. Same body as `POST`, with one difference:

> **`password` is optional here.** Omit it, or send an empty string, to keep the
> existing password. Send a new value to change it.

**Request**

```json
{
  "employeeCode": "EMP003",
  "name": "Nusrat Jahan Mim",
  "email": "nusrat@misl.com",
  "department": "Finance",
  "designation": "Senior Accounts Officer",
  "role": "EMPLOYEE",
  "active": true
}
```

**Response — `200 OK`** — the updated employee.

| Code | Cause |
|---|---|
| `400` | Validation failure |
| `404` | No employee with that id |
| `409` | Another employee already uses that email or code |

---

## `DELETE /api/employees/{id}`

Deletes the employee **and all of their leave requests** (cascade).

**Response — `204 No Content`**, empty body.

| Code | Cause |
|---|---|
| `404` | No employee with that id |

---

# Leave Requests

## `GET /api/leaves`

Every leave request in the system, newest first. **ADMIN only.**

**Response — `200 OK`**

```json
[
  {
    "id": 3,
    "employeeId": 3,
    "employeeName": "Karim Hossain",
    "employeeCode": "EMP002",
    "leaveType": "SICK",
    "startDate": "2026-09-07",
    "endDate": "2026-09-08",
    "reason": "Medical appointment",
    "status": "REJECTED",
    "createdAt": "2026-09-04T06:45:11.482",
    "reviewedAt": "2026-09-04T06:45:11.482"
  },
  {
    "id": 1,
    "employeeId": 2,
    "employeeName": "Rahim Uddin",
    "employeeCode": "EMP001",
    "leaveType": "CASUAL",
    "startDate": "2026-09-12",
    "endDate": "2026-09-14",
    "reason": "Family function at home district",
    "status": "PENDING",
    "createdAt": "2026-09-04T06:45:11.470",
    "reviewedAt": null
  }
]
```

The employee is flattened into three scalar fields rather than nested, because that is
all a list row needs — and it avoids the infinite `employee → leaveRequests → employee`
recursion that serialising the entity would cause.

| Code | Cause |
|---|---|
| `403` | Caller is an `EMPLOYEE` |

---

## `GET /api/leaves/my`

The **caller's own** requests, newest first. Any authenticated user.

**Response — `200 OK`** — same array shape as above, filtered to the caller.

> There is no id in the URL and none in the body. The employee id is taken from the
> JWT, so this endpoint cannot be pointed at another person's data — there is nothing
> in the request to tamper with.

---

## `GET /api/leaves/{id}`

One leave request. An `ADMIN` may read any; an `EMPLOYEE` may read only their own.

**Response — `200 OK`** — a single object in the shape above.

| Code | Cause |
|---|---|
| `403` | `"You can only access your own leave requests"` |
| `404` | No leave request with that id |

---

## `POST /api/leaves`

Submit a leave request. Any authenticated user.

**Request**

```json
{
  "leaveType": "CASUAL",
  "startDate": "2026-09-12",
  "endDate": "2026-09-14",
  "reason": "Family function at home district"
}
```

**Response — `201 Created`**

```json
{
  "id": 5,
  "employeeId": 2,
  "employeeName": "Rahim Uddin",
  "employeeCode": "EMP001",
  "leaveType": "CASUAL",
  "startDate": "2026-09-12",
  "endDate": "2026-09-14",
  "reason": "Family function at home district",
  "status": "PENDING",
  "createdAt": "2026-09-04T07:25:03.117",
  "reviewedAt": null
}
```

> The request body has **no `employeeId` and no `status`** — by design. The owner comes
> from the token, and the status is always `PENDING`. If a client could send either, an
> employee could file leave in someone else's name, or approve their own.

**Validation**

| Field | Rule |
|---|---|
| `leaveType` | required, `CASUAL` \| `SICK` \| `ANNUAL` |
| `startDate` | required, `YYYY-MM-DD` |
| `endDate` | required, `YYYY-MM-DD`, **must not be before `startDate`** |
| `reason` | required, ≤ 500 characters |

A single-day leave (`startDate == endDate`) is valid.

| Code | Cause |
|---|---|
| `400` | `"End date must not be before start date"`, or a `fieldErrors` object |

The date-range rule is checked in the service rather than by an annotation, because it
compares two fields — a per-field annotation can only see its own value.

---

## `PUT /api/leaves/{id}`

Edit a request. Only the **owner**, and only while the status is `PENDING`.

**Request** — identical to `POST /api/leaves`.

**Response — `200 OK`** — the updated request.

| Code | Cause |
|---|---|
| `400` | `"Only a PENDING leave request can be edited. This request is already APPROVED"` |
| `403` | Not the owner |
| `404` | No leave request with that id |

Once reviewed, a request records a decision — editing it would change what was decided,
so it is refused rather than silently updated.

---

## `DELETE /api/leaves/{id}`

Withdraw a request. An `ADMIN` may delete any; an `EMPLOYEE` may delete only their own,
and only while `PENDING`.

**Response — `204 No Content`**, empty body.

| Code | Cause |
|---|---|
| `400` | `"Only a PENDING leave request can be withdrawn..."` (employee only) |
| `403` | Not the owner |
| `404` | No leave request with that id |

---

## `PATCH /api/leaves/{id}/approve`

Approve a pending request. **ADMIN only.** No request body.

**Response — `200 OK`**

```json
{
  "id": 1,
  "employeeId": 2,
  "employeeName": "Rahim Uddin",
  "employeeCode": "EMP001",
  "leaveType": "CASUAL",
  "startDate": "2026-09-12",
  "endDate": "2026-09-14",
  "reason": "Family function at home district",
  "status": "APPROVED",
  "createdAt": "2026-09-04T06:45:11.470",
  "reviewedAt": "2026-09-04T07:25:21.734"
}
```

`status` changes and `reviewedAt` is stamped. The updated resource is returned so the
client can replace that one row instead of refetching the whole list.

| Code | Cause |
|---|---|
| `400` | `"Only a PENDING leave request can be reviewed. This request is already APPROVED"` |
| `403` | Caller is not an `ADMIN` |
| `404` | No leave request with that id |

`PATCH` rather than `PUT` because this changes a single field rather than replacing the
resource, and it carries no body — the URL expresses the entire instruction.

---

## `PATCH /api/leaves/{id}/reject`

Identical to `approve`, but sets `status` to `REJECTED`. **ADMIN only.**

The only legal transitions in the system are:

```
PENDING → APPROVED
PENDING → REJECTED
```

Anything else is a `400`.

---

# Dashboard

## `GET /api/dashboard/admin`

Company-wide counters. **ADMIN only.**

**Response — `200 OK`**

```json
{
  "totalEmployees": 3,
  "totalLeaves": 3,
  "pendingLeaves": 1,
  "approvedLeaves": 1,
  "rejectedLeaves": 1
}
```

---

## `GET /api/dashboard/employee`

The caller's own counters. Any authenticated user.

**Response — `200 OK`**

```json
{
  "totalEmployees": 0,
  "totalLeaves": 2,
  "pendingLeaves": 1,
  "approvedLeaves": 1,
  "rejectedLeaves": 0
}
```

`totalEmployees` is `0` here — it has no meaning on a personal dashboard. Both
endpoints share one response type because the shape is identical; the endpoint decides
the scope.

Every figure comes from a `COUNT` query rather than loading rows and counting in Java,
so an employee never receives leave rows they are not permitted to see merely to
compute a total.

---

# Quick reference

| Method | Endpoint | Access |
|---|---|---|
| `POST` | `/api/auth/login` | public |
| `GET` | `/api/employees` | ADMIN |
| `GET` | `/api/employees/{id}` | ADMIN |
| `POST` | `/api/employees` | ADMIN |
| `PUT` | `/api/employees/{id}` | ADMIN |
| `DELETE` | `/api/employees/{id}` | ADMIN |
| `GET` | `/api/leaves` | ADMIN |
| `GET` | `/api/leaves/my` | authenticated |
| `GET` | `/api/leaves/{id}` | ADMIN or owner |
| `POST` | `/api/leaves` | authenticated |
| `PUT` | `/api/leaves/{id}` | owner, while PENDING |
| `DELETE` | `/api/leaves/{id}` | ADMIN, or owner while PENDING |
| `PATCH` | `/api/leaves/{id}/approve` | ADMIN |
| `PATCH` | `/api/leaves/{id}/reject` | ADMIN |
| `GET` | `/api/dashboard/admin` | ADMIN |
| `GET` | `/api/dashboard/employee` | authenticated |

---

# Testing with curl

```bash
# 1. log in and capture the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@misl.com","password":"admin123"}' | jq -r .token)

# 2. call a protected endpoint
curl -s http://localhost:8080/api/employees -H "Authorization: Bearer $TOKEN" | jq

# 3. confirm the 401 path
curl -i http://localhost:8080/api/employees

# 4. confirm the 403 path — log in as an employee, then call an admin endpoint
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"rahim@misl.com","password":"employee123"}' | jq -r .token)
curl -i http://localhost:8080/api/employees -H "Authorization: Bearer $TOKEN"
```

On Windows PowerShell, use `curl.exe` explicitly — plain `curl` is an alias for
`Invoke-WebRequest`, which does not accept `-X`, `-H` or `-d`.
