# ShahMir Online Examination System (ExamPro)

Enterprise Spring Boot 3.3.4 Web Application with Spring Security Role-Based Access Control (RBAC) and complete Review 1 Core Java Architecture: OOP, inheritance, polymorphism, generic `Question<T>`, multithreading, JDBC CRUD with `PreparedStatement`, atomic transactions, and embedded H2 database.

---

## Temporary Demo Credentials

> [!WARNING]
> **LOCAL-DEMO-ONLY CREDENTIALS**: The credentials below are provided strictly for local development, demonstration, and evaluation testing. Passwords are encrypted using BCrypt (`BCryptPasswordEncoder`). **You must change all passwords before any production or public deployment.**

| Role | Username / Email | Temporary Password | Login Portal URL | Landing Dashboard | Permissions & Scope |
|---|---|---|---|---|---|
| **ADMIN** | `admin@exampro.edu` | `AdminPassword123!` | [`/admin/login`](http://localhost:8080/admin/login) | [`/admin/dashboard`](http://localhost:8080/admin/dashboard) | Full access: student management, user accounts, exam authoring, results, and system administration. |
| **TEACHER** | `teacher@exampro.edu` | `TeacherPassword123!` | [`/teacher/login`](http://localhost:8080/teacher/login) | [`/teacher/dashboard`](http://localhost:8080/teacher/dashboard) | Create, preview, and manage exams; view results; cannot delete students or manage user accounts. |
| **STUDENT** | `ada@exampro.edu` | `StudentPassword123!` | [`/student/login`](http://localhost:8080/student/login) | [`/student/dashboard`](http://localhost:8080/student/dashboard) | Browse available exams, take timed exams under verified identity, view only their own scorecards. |
| **STUDENT** | `alan@exampro.edu` | `StudentPassword123!` | [`/student/login`](http://localhost:8080/student/login) | [`/student/dashboard`](http://localhost:8080/student/dashboard) | Enrolled student candidate; cannot view Ada's scorecards or submit on behalf of other candidates. |

Each login portal validates the account's role. Attempting to log into a portal without the matching role is rejected with a clear **Role Mismatch** error (`?error=role_mismatch`).

---

## Quick Start (Spring Boot Web App)

Requires **JDK 17+** and **Maven**.

### 1. Run All Tests
```bash
mvn clean test
```
Runs 25 automated unit and integration tests across OOP, Collections/Generics, Transactions, Concurrency, Controllers, Spring Security RBAC, and Admin User Management.

### 2. Launch Spring Boot Server
```bash
mvn spring-boot:run
```
Starts the web application on single server **port 8080**:
- **Application URL**: [http://localhost:8080](http://localhost:8080)
- **Status Indicator**: `Spring Boot server :8080; H2 database connected`
- **Database**: Embedded H2 database stored in `./data/exampro_v2` (seeded and schema-initialized automatically).

### 3. Run Console Demo (Optional)
To execute the terminal-only Review 1 verification routine:
```bash
mvn compile exec:java -Dexec.args="--console"
```

---

## Security & Architecture Highlights

1. **Admin User Management (`/admin/users`) &mdash; ADMIN Only:**
   - Available exclusively to administrators with `ROLE_ADMIN` (enforced by Spring Security and `adminFilterChain`).
   - Create `ADMIN`, `TEACHER`, and `STUDENT` accounts with name, email, role, and temporary password.
   - **BCrypt Hashing**: All passwords are encrypted with 10-round salted `BCryptPasswordEncoder`. Plaintext passwords are never stored or logged.
   - **Duplicate Email Prevention**: Checks both `app_users` and `students` tables, rejecting duplicate email registrations with clear alert feedback.
   - **Secure Change-Password Form**: Administrator can select any user account to reset and re-hash their password with confirmation checks.
   - Clear privacy and safety guidance: No real Gmail or personal credentials are used; sample accounts use local-demo-only credentials.

2. **Dedicated Role Portals & Authentication:**
   - Separate login templates: `/admin/login`, `/teacher/login`, and `/student/login`.
   - `RoleCheckingAuthenticationSuccessHandler` validates that the user possesses the required role before redirecting to the respective dashboard (`/admin/dashboard`, `/teacher/dashboard`, `/student/dashboard`). Role mismatches immediately invalidate the session and redirect with user-friendly notices.

3. **Role-Based Authorization Rules:**
   - **ADMIN**: Access to `/admin/**` (including `/admin/users`), `/students/**`, `/exams/**`, `/results/**`.
   - **TEACHER**: Access to `/teacher/**`, `/exams/new`, `/exams`, `/results`. Blocked from student deletion and user management (`/admin/**`).
   - **STUDENT**: Access to `/student/**`, `/exams/*/take`, `/results`, `/attempts/{id}`. Strictly restricted to viewing only their own scorecards and taking exams under their authenticated student identity. Tampering with `studentId` parameters is prevented with `AccessDeniedException`.
   - **Public**: Login pages (`/admin/login`, `/teacher/login`, `/student/login`), static CSS (`/css/**`), JavaScript (`/js/**`), and `/access-denied`.

3. **CSRF & Session Protection:**
   - All forms protected with Spring Security CSRF tokens (`_csrf`).
   - Secure POST logout integrated into the navigation bar across all templates.
   - Custom access-denied handling with clear feedback on unauthorized access attempts.

---

## Review 1 Rubric Mapping

| Rubric Item (Marks) | Implementation in Codebase |
|---|---|
| **OOP: Inheritance, Polymorphism, Interfaces, Exceptions (10)** | `model/User` &rarr; `Student`, `Instructor`; `model/Question<T>` &rarr; `MultipleChoiceQuestion`, `TrueFalseQuestion`; Interfaces `Identifiable`, `CrudRepository<T>`, `AttemptRepository`; Custom exceptions in `exception/`. |
| **Collections & Generics (6)** | `Exam` (`List`), `ExamSubmissionService` (`Set`, `Map`, `ConcurrentHashMap`), `ExamRepository` (`Map`), `Question<T>`, `InMemoryCache<T>`, `CrudRepository<T>`, `Transactions.SqlWork<R>`. |
| **Multithreading & Synchronization (4)** | `ExamSubmissionService`: 4-thread `ExecutorService`, `CompletableFuture`, one `ReentrantLock` per `(exam, student)`, check-and-save under lock. |
| **Database Model & Operations (7)** | `db/DatabaseInitializer` (7 normalized tables including `app_users`, keys, constraints, cascades), model classes mirror the schema. |
| **JDBC CRUD, PreparedStatement (3)** | `StudentRepository` (full CRUD), `ExamRepository`, `JdbcAttemptRepository`, `AppUserRepository`. |
| **JDBC Transaction Management (3)** | `db/Transactions.run(...)` used by `ExamRepository#createWithQuestions` and `JdbcAttemptRepository#save`. |
