# Software Requirements Specification (SRS)

Version 1.0 · October 28, 2026 · Application baseline: `70bd1d2`
[Back to README](../README.md)

## 1. Purpose and Baseline of This Document

This specification documents the current implementation and its acceptance criteria. It was created by looking back at the code, agreed interfaces, and verification records. The requirement identifiers support traceability; they do not imply that a formal specification was approved before implementation. Alignment with the assigned project topic remains subject to team review.

## 2. Users and Scope

Customers are responsible for shipping items and tracking their own orders; employees are responsible for task sharing and inventory operations; administrators are responsible for user permissions, settings, and statistics. Administrators are not automatic substitutes for employees in write operations. The system supports logistics processes for educational purposes and does not include actual transportation networks, payment settlement, or SMS identity verification.

## 3. Functional Requirements and Acceptance Criteria

| No. | System Requirements | Acceptance Criteria |
|---|---|---|
| FR-01 | Register customers or employees; log in and log out of the current session; self-registration as an administrator is prohibited | Duplicate usernames are rejected; a token is returned upon successful login; after logging out, this token can no longer access business APIs |
| FR-02 | Customers create orders and obtain a unique waybill number | The order, initial tracking information, and pickup task are saved together; no partial business records are retained in case of failure |
| FR-03 | Customers can search for their own orders by page and view tracking information; employees and administrators can query all orders | Other customers cannot access this order via lists, searches, or details; keyword matching applies to waybill numbers or items |
| FR-04 | Employees process shared to-do items according to permitted status transitions | Legitimate updates are logged in the tracking history and complete the current task; non-final statuses generate the next task; duplicate completions and invalid status transitions are rejected |
| FR-05 | Customers confirm their own orders currently in delivery using a valid confirmation code | Rejected if the code is incorrect, the order does not belong to the customer, or the order is not in the “In Delivery” status; remaining to-do items are closed upon successful confirmation |
| FR-06 | Customers submit one piece of feedback per order | Duplicate feedback is rejected; saved feedback is returned in the order details. The current interface does not have a restriction requiring “feedback only after confirmation” |
| FR-07 | Staff upload proof-of-delivery images, including after receipt confirmation | Only accepts delivery tasks for waybills that are “in delivery” or “signed for”; 1–3 PNG/JPEG images per upload, with each image up to 2 MB and a maximum side length of 4096 pixels; no duplicate uploads for the same task; upload does not automatically mark the order as signed for |
| FR-08 | Employees record inventory inflows and outflows broken down by warehouse and item | Inventory cannot be negative, and usage cannot exceed capacity; inventory, capacity, and transaction logs are saved simultaneously |
| FR-09 | Administrators search for users, assign roles, and enable/disable accounts | Batch role changes must either succeed entirely or roll back entirely; role changes and account deactivation invalidate the old token; the last active administrator cannot be disabled or demoted |
| FR-10 | Provide role-based authorization statistics and in-system notifications | Customer statistics are limited to their own orders; administrators can view monthly reports; notifications only display events specific to the user and enabled system announcements |
| FR-11 | Administrators save system settings; logged-in users retrieve settings | Settings are retained after modification and re-retrieval; customers and staff cannot write to settings |

## 4. Quality Constraints

| No. | Requirement | Verification Method |
|---|---|---|
| NFR-01 | Server-side authorization; do not trust the identity claimed by the browser | Token, customer isolation, role-based access control regression testing |
| NFR-02 | Passwords are stored as BCrypt hashes; tokens are stored as SHA-256 digests; credentials are not included in the source code | Authentication testing and configuration checks |
| NFR-03 | Orders, tasks, and inventory maintain transactional consistency | Concurrent order placement, duplicate task handling, and inventory contention testing |
| NFR-04 | Interface paths, fields, and response conventions are preserved | Controller and front-end request verification |
| NFR-05 | Reasons for login/registration failures and session expiration are visible; responses from old sessions must not affect new sessions | Front-end scripts and bounded browser testing |
| NFR-06 | Source code includes installation and runtime instructions, as well as an entry point for repeatable testing | README, Maven, and Node commands; acceptance testing on a brand-new machine is still pending |

No SLAs have been defined for throughput, concurrent user count, or service availability; production-level performance conclusions cannot be derived from existing functional tests.

## 5. Business Boundaries

Six statuses are maintained: `pending`, `transit`, `delivering`, `delivered`, `exception`, and `cancelled`. The status enumeration includes “cancelled,” but there is currently no complete cancellation interface or UI.
Tasks lack an “assigned to” field; orders lack sender details and a pickup address. The task address currently uses the destination address, which does not represent the actual pickup address.
The inventory API does not include waybill numbers, and units of measure have not yet been standardized; therefore, waybill statuses are not automatically updated. Vouchers can currently only be uploaded; there is no complete interface for viewing or downloading them. There are no features for feedback replies, warehouse configuration management, password reset, SMS confirmation of receipt, or payment.
Open employee registration is part of the existing agreement. Tightening registration permissions, adding sender fields, implementing server-side repricing, or introducing inventory integration are all considered request changes that require confirmation.

## 6. Requirement Tracking

| Requirement | Primary Implementation | Automated Test Basis |
|---|---|---|
| FR-01 / NFR-01–02 | AuthController, AuthService, ApiAuthFilter | CoreApiTest |
| FR-02–03 | OrderController, OrderService | CoreApiTest, ConcurrentOrderTest |
| FR-04–07 | WorkflowService, OperationsController | FullWorkflowTest, ConcurrentOrderTest |
| FR-08 | InventoryService | FullWorkflowTest, ConcurrentOrderTest |
| FR-09 / FR-11 | AdminService, BootstrapAdmin | FullWorkflowTest; see README for instructions on setting up the administrator environment |
| FR-10 | ReportService | FullWorkflowTest |
| NFR-04–05 | logistics-admin.html, CorsConfig | test-phase-one-frontend.cjs, ProxyCorsTest |

Test names correspond to [test directories](../src/test/java/com/example/logistics/); see [Verification Instructions](VERIFICATION.md) for specific execution environments and boundaries of evidence. This mapping illustrates traceability relationships and does not imply that manual browser testing has been completed for every acceptance criterion.
