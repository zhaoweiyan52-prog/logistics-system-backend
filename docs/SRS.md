# Software Requirements Specification (SRS)

Version 1.1 · October 28, 2026 · Application baseline: `70bd1d2`

[Documentation index](README.md) · [Project overview](../README.md)

## 1. Purpose and Baseline

This specification records the implemented requirements and their acceptance criteria. It was compiled retrospectively from the code, interface agreements, and verification records. Requirement IDs link the scope to implementation and tests; they do not imply approval before development.

## 2. Users and Scope

Customers create and track their own orders. Staff process shared tasks and inventory movements. Administrators manage accounts, settings, and reports. Administrator permissions do not include staff-only write operations.

The system supports an educational logistics workflow. It does not connect to a real transport network, process payments, or verify identities by SMS.

## 3. Functional Requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-01 | The system shall register customers and staff, authenticate users, and end sessions. | Duplicate usernames and administrator self-registration are rejected. Successful registration or login returns a token. Logout invalidates that session's token. |
| FR-02 | Customers shall be able to create an order with a unique tracking number. | The order, initial tracking event, and pickup task are saved in one transaction. A failed transaction leaves no partial order records. |
| FR-03 | Users shall be able to search, filter, and page through permitted orders and view tracking details. | Customers see only their own orders. Staff and administrators see all orders. Search matches tracking numbers or goods; status filters and pagination apply. |
| FR-04 | Staff shall be able to advance shared tasks through permitted order states. | A valid change records a tracking event and completes the current task. A non-terminal state creates the next task. Invalid transitions and repeated completion are rejected. |
| FR-05 | Customers shall be able to confirm receipt of their own orders using a confirmation code. | Only orders in `delivering` accept confirmation. An incorrect code or another customer's request is rejected. Success sets `delivered` and closes remaining tasks. |
| FR-06 | Customers shall be able to submit feedback on their own orders. | One feedback entry is allowed per order and is returned in order details. Duplicate submissions are rejected. Feedback is not restricted to delivered orders. |
| FR-07 | Staff shall be able to upload proof-of-delivery images. | A delivery task accepts one upload when its order is `delivering` or `delivered`: 1–3 PNG/JPEG images, each at most 2 MB and 4096 pixels on either side. Uploading does not confirm receipt. |
| FR-08 | Staff shall be able to record stock entering or leaving a warehouse. | Stock is tracked by warehouse and goods. Insufficient stock and capacity overflow are rejected. Stock, warehouse usage, and movement records are updated atomically. |
| FR-09 | Administrators shall be able to search accounts, assign roles, and enable or disable accounts. | Batch role changes succeed or roll back together. Role changes and disabling an account revoke its existing tokens. The last enabled administrator cannot be disabled or demoted. |
| FR-10 | The system shall provide authorized statistics and notifications. | Customer statistics cover only their orders; administrators can read monthly reports. Notifications contain the user's events and enabled system announcements. |
| FR-11 | Administrators shall be able to save system settings; authenticated users shall be able to read them. | Saved values are returned on a subsequent read. Customer and staff attempts to change settings are rejected. |

## 4. Quality Constraints

| ID | Constraint | Verification approach |
|---|---|---|
| NFR-01 | The server shall enforce identity, ownership, and role permissions. | Reject forged or expired tokens, cross-customer access, and unauthorized operations. |
| NFR-02 | Passwords shall use BCrypt hashes; stored tokens shall use SHA-256 digests. Deployment credentials shall remain outside source control. | Inspect stored values in authentication tests and review configuration. |
| NFR-03 | Concurrent operations shall preserve order, task, and inventory consistency. | Test simultaneous orders, duplicate task completion, and competing stock withdrawals. |
| NFR-04 | API paths, fields, and response conventions shall remain compatible with the integrated frontend. | Compare controllers with frontend requests and the current [HTTP contract](DESIGN.md#http-contract). |
| NFR-05 | Authentication failures shall remain visible; responses from old sessions shall not affect the current session. | Check form messages and delayed responses in script tests and targeted browser checks. |
| NFR-06 | The repository shall provide installation instructions and reproducible test commands. | Follow the README on a clean machine; this acceptance check remains pending. |

No throughput, concurrent-user, or availability target has been agreed. Existing functional tests do not establish production performance.

## 5. Scope Boundaries and Open Decisions

- Order states are `pending`, `transit`, `delivering`, `delivered`, `exception`, and `cancelled`. There is no cancellation workflow despite the reserved state.
- Tasks are shared rather than individually assigned. Orders have no sender details or pickup address; task addresses currently use the destination.
- Inventory is independent of orders. Its API has no order reference, and quantity units have not been standardized.
- Proof-of-delivery images can be uploaded but cannot yet be viewed or downloaded through the application. Feedback replies, warehouse configuration, password resets, SMS confirmation, and payments are outside the implemented scope.
- Shipping fees are calculated by the frontend and validated and stored by the backend; there is no server-side repricing. Notification read status is not persisted.
- Open staff registration follows the existing interface agreement. Restricting registration or adding the capabilities above requires a team decision on requirements and permissions.

## 6. Traceability

| Requirements | Main implementation | Automated test sources |
|---|---|---|
| FR-01; NFR-01–02 | AuthController, AuthService, ApiAuthFilter | [CoreApiTest](../src/test/java/com/example/logistics/CoreApiTest.java) |
| FR-02–03 | OrderController, OrderService | [CoreApiTest](../src/test/java/com/example/logistics/CoreApiTest.java), [ConcurrentOrderTest](../src/test/java/com/example/logistics/ConcurrentOrderTest.java) |
| FR-04–07 | WorkflowService, OperationsController | [FullWorkflowTest](../src/test/java/com/example/logistics/FullWorkflowTest.java), [ConcurrentOrderTest](../src/test/java/com/example/logistics/ConcurrentOrderTest.java) |
| FR-08; NFR-03 | InventoryService; transactional order and task services | [FullWorkflowTest](../src/test/java/com/example/logistics/FullWorkflowTest.java), [ConcurrentOrderTest](../src/test/java/com/example/logistics/ConcurrentOrderTest.java) |
| FR-09, FR-11 | AdminService; BootstrapAdmin for first-admin setup | [FullWorkflowTest](../src/test/java/com/example/logistics/FullWorkflowTest.java); bootstrap instructions in the README |
| FR-10 | ReportService | [FullWorkflowTest](../src/test/java/com/example/logistics/FullWorkflowTest.java) |
| NFR-04–05 | logistics-admin.html, CorsConfig | [Frontend script tests](../scripts/test-phase-one-frontend.cjs), [ProxyCorsTest](../src/test/java/com/example/logistics/ProxyCorsTest.java) |
| NFR-06 | README installation and test instructions | Clean-machine installation remains pending |

This mapping identifies relevant evidence, not complete coverage of every acceptance criterion. See [Verification](VERIFICATION.md) for recorded results, environments, and remaining checks.
