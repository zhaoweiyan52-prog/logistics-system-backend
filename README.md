# Logistics Management System

Undergraduate Software Engineering Course Project: A web application that supports customers, logistics staff, and administrators.
[Online Demo](https://logistics-system-backend-o7if.onrender.com/logistics-admin.html) · [Chinese Maintenance Instructions](README.zh-CN.md)

## Review Navigation

- [Requirements Specification](docs/SRS.md): Current requirements baseline, business rules, scope limitations, and acceptance criteria.
- [Design and Interfaces](docs/DESIGN.md): Architecture, data model, state transitions, and 24 API operations.
- [Verification and Evolution](docs/VERIFICATION.md): Test evidence, fix logs, and demonstration steps.

These documents describe the system that was put in place as of October 28, 2026, at application commit `70bd1d2`. They can be used to evaluate the project, but they are not the same as the final report for the course (maximum 1,000 words) or each student's individual logbook.

## Scope

Customers can place orders, check their order status and tracking information, confirm receipt using a verification code, and submit feedback. Employees use a shared task pool to track progress, upload proof of delivery, and record inventory movements. Administrators manage roles and account activation/deactivation, view reports, and save settings. The interface and business prompts are currently in Chinese; the English documentation does not imply that the interface has been localized into English. The role labels are Customer (`客户`), Logistics Staff (`物流员工`), and System Administrator (`系统管理员`).

## Technology and Architecture

The backend targets Java 17 and uses Spring Boot 3.5.7, JdbcTemplate, and MySQL. The system has a layered architecture in a single Spring Boot application. Vue 3 and Element Plus provide the browser interface. Spring Boot serves the page and its dependencies, so there is no need for an npm build. The public demo runs on Render with an Aiven MySQL database.

## Installation and Execution

Requires JDK 17+, Maven 3.6.3+, and MySQL 8.0.16+. Frontend script checks also require Node.js that supports `node --test`. A verified environment uses JDK 24.0.1, Maven 3.9.11, and MySQL 8.0.46; Cloud-based Docker environments use Java 17. On new computers, you should install your own tools rather than relying on the maintainer’s portable cache.

### 1. Get the Code

```sh
git clone https://github.com/zhaoweiyan52-prog/logistics-system-backend.git
cd logistics-system-backend
java -version
mvn -version
```

### 2. Prepare a Standalone Database

From the root directory of the repository, start the MySQL command-line client using a database administrator account. Run the following SQL only for a new, empty database.

```sh
mysql -u root -p
```


```sql
SOURCE sql/01-create-database.sql;
USE logistics;
SOURCE sql/02-schema.sql;
SHOW TABLES;
CREATE USER 'logistics_app'@'localhost' IDENTIFIED BY '<YOUR_LOCAL_DATABASE_PASSWORD>';
GRANT SELECT, INSERT, UPDATE, DELETE ON logistics.* TO 'logistics_app'@'localhost';
```

Replace the password placeholder with your own local database password. There should be 13 tables. The application does not automatically create tables upon startup; do not re-run script 02 on an existing database, as some index statements cannot be re-executed. For legacy Phase 1 databases, use `sql/03-full-features.sql` to preserve data during the upgrade; databases that have already been upgraded do not need to be rebuilt.

### 3. Configure and Start

Windows PowerShell:

```powershell
$env:DB_USERNAME = 'logistics_app'
$env:DB_PASSWORD = '<YOUR_LOCAL_DATABASE_PASSWORD>'
$env:DB_URL = 'jdbc:mysql://localhost:3306/logistics?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
mvn spring-boot:run
```

macOS / Linux:

```sh
export DB_USERNAME='logistics_app'
export DB_PASSWORD='<YOUR_LOCAL_DATABASE_PASSWORD>'
export DB_URL='jdbc:mysql://localhost:3306/logistics?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
mvn spring-boot:run
```

Open [http://localhost:8080/logistics-admin.html](http://localhost:8080/logistics-admin.html) in your browser and verify that [http://localhost:8080/health](http://localhost:8080/health) returns `{"status":"UP"}`. Do not double-click the HTML file directly; the page’s `/api` requires a running backend. Local and production accounts are stored in separate databases. Passwords are configured locally only and should not be committed to Git.

### 4. Creating an Evaluation Account

There is no default login username or password. The registration tab (`注册账号`) allows you to select Customer (`我是客户`) or Staff (`我是物流员工`) and automatically logs you in upon successful registration. Only if your new database does not yet have an administrator:

1. First, register an account for evaluation purposes, then stop the application.
2. Set the environment variable `BOOTSTRAP_ADMIN_ACCOUNT` to that username, then restart the application.
3. Stop the application, delete this environment variable, restart the application, and log in again; the account will now be an administrator. This process revokes the account’s original token.

If an administrator already exists, this mechanism will not elevate other accounts; subsequent role assignments are performed on the administrator page. The production administrator has already been initialized and should not be configured again.

Use the following commands for steps 2 and 3, replacing `<REGISTERED_USERNAME>` with the account you created. Stop the running application with Ctrl+C before each restart. Keep the database environment variables set in the same terminal.

| Shell | Set before the first restart | Remove before the second restart |
|---|---|---|
| PowerShell | `$env:BOOTSTRAP_ADMIN_ACCOUNT = '<REGISTERED_USERNAME>'` | `Remove-Item Env:BOOTSTRAP_ADMIN_ACCOUNT` |
| macOS / Linux | `export BOOTSTRAP_ADMIN_ACCOUNT='<REGISTERED_USERNAME>'` | `unset BOOTSTRAP_ADMIN_ACCOUNT` |

Restart with `mvn spring-boot:run` after each step.

### 5. Testing and Packaging

```sh
mvn test
node --test scripts/test-phase-one-frontend.cjs
mvn package
java -jar target/logistics-backend-0.0.1-SNAPSHOT.jar
```

Stop the development service before starting the JAR to avoid port conflicts; the database environment variable must still be set. The backend uses a standalone H2 in-memory database by default for testing; MySQL is not required, and this does not guarantee that all MySQL behavior is correct. The frontend script names retain historical conventions, but the tests include the full mode. The `SERVER_PORT` environment variable can be used to change the port.

If startup fails, check the JDK, MySQL service, database tables, application account permissions, and port usage. Free production instances may require a startup delay. Please use separate local data for review; the public demo is not a data sandbox that can be cleared at will.

## Known Limitations

Inventory and shipping orders are separate; there are no personal task assignments, sender details or pickup addresses, a complete order cancellation process, feedback replies, password resets, or proof-of-delivery viewing interfaces. The receipt confirmation code is a teaching feature; shipping fees are calculated by the page based on settings and submitted, then validated and saved by the backend; they are not used for actual billing or identity verification. Notification read status is not persisted on the server. See the SRS for detailed boundaries.

## Repository Structure

`src/main/java`: Backend; `src/main/resources/static`: Actual pages and local dependencies; `src/test`: Automated tests; `sql`: Database creation and incremental upgrades; `scripts`: Auxiliary execution and validation; `docs`: English review materials and Chinese maintenance evidence; `reference`: Original pages, simulated services, and historical contracts.
Simulation verification in `reference` does not equate to acceptance of the current application; nor is it the application entry point. Build artifacts, credentials, and IDE settings are not part of the source code commit.
