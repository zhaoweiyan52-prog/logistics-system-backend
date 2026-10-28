# Validation, Evolution, and Demonstration

October 28, 2026 · [Documentation index](README.md) · [Requirements](SRS.md)

## Evidence and Limitations

The following summarizes existing execution records. For the purpose of compiling this English documentation, we did not rerun the tests or revalidate the installation on a brand-new machine.

| Check | Existing Records | What It Indicates / Limitations |
|---|---|---|
| H2 Backend Regression | 37 tests passed; during the 2026-10-28 fix | Controller, Service, authentication, SQL, transaction, and concurrency tests; does not cover all MySQL behaviors |
| Frontend Scripts | 26 tests passed; application version 70bd1d2 | VM tests executing actual inline page scripts; not 26 browser click tests |
| MySQL 8.0.46 | Previously passed 35 business tests and repeated incremental upgrade verification | Validates the MySQL results from that time; latest fixes not retested |
| Cloud MySQL 8.4 | Previously passed 23 API checks, including site Origin | Historical evidence of business workflows; full set of write checks not retested for the latest release |
| Latest release 70bd1d2 | Render deployed to live; HTML consistent; health UP; empty login business code 40001; HTTP 401 for orders without a token | Evidence of release and basic authentication; does not equate to complete manual acceptance testing across three roles |
| Browser checks | Authentication prompts, pagination, and file removal in isolated environments; Public deployment session expiration and password recovery instructions | Bounded interaction verification; does not cover all buttons, inputs, narrow screens, and keyboard operations |

[Backend Testing](../src/test/java/com/example/logistics/) includes CoreApiTest, FullWorkflowTest, ConcurrentOrderTest, ProxyCorsTest, and HealthControllerTest. [Frontend Checks](../scripts/test-phase-one-frontend.cjs) cover role loading, authentication errors, filtering, responses to expired sessions, and successful/failed logouts.
Detailed evidence in Chinese can be found in the [Fix Log](修缮清单与验证.md) and [Cloud Records](云端部署验证记录.md), and [Local MySQL Records](本机MySQL配置记录.md). Local logs and screenshots under the `target` directory are byproducts and are not distributed with the repository; reproducible test source code is retained in the repository.

## Selected Checks and Recorded Results

The cases below connect requirements to concrete assertions and recorded observations. Backend cases are part of the previously recorded 37-test H2 run; script cases are part of the 26-test frontend run. This documentation update did not rerun them.

| ID / requirement | Environment and check | Expected result | Recorded result and evidence |
|---|---|---|---|
| T01 / FR-03, NFR-01 | H2: request another customer's order; compare staff/admin access | Customer denied; staff and administrators allowed | Passed — [role access test](../src/test/java/com/example/logistics/FullWorkflowTest.java#L76) |
| T02 / FR-04–06, FR-10 | H2: advance an order, try a wrong receipt code, confirm with the correct code, submit feedback twice | Invalid transitions, wrong code, and duplicate feedback rejected; valid receipt and feedback saved; notifications scoped to the owner | Passed — [order workflow test](../src/test/java/com/example/logistics/FullWorkflowTest.java#L48) |
| T03 / FR-07 | H2: upload fake image bytes, a valid PNG, and a duplicate | Reject fake and duplicate images; store the PNG; leave the order in `delivering` | Passed — [image upload test](../src/test/java/com/example/logistics/FullWorkflowTest.java#L134) |
| T04 / FR-08, NFR-03 | H2: two concurrent withdrawals of 8 units from stock of 10 | Exactly one succeeds; stock and warehouse usage both remain 2 | Passed — [concurrent withdrawal test](../src/test/java/com/example/logistics/ConcurrentOrderTest.java#L48) |
| T05 / FR-09 | H2: disable or demote the only enabled administrator | Both operations rejected with business code 40908 | Passed — [last-administrator test](../src/test/java/com/example/logistics/FullWorkflowTest.java#L113) |
| T06 / FR-11 | H2: customer attempts a settings update; administrator saves and customer reads | Customer write rejected; saved value returned on read | Passed — [settings test](../src/test/java/com/example/logistics/FullWorkflowTest.java#L119) |
| T07 / NFR-05 | Node VM: delay old-session success, 401, and JSON-body responses until after account switching | Old responses neither replace current data nor log out the new session | Passed: old-session and delayed-JSON cases in [Frontend tests](../scripts/test-phase-one-frontend.cjs) |
| T08 / NFR-05 | Node VM: fail server logout, then test successful logout separately | Failure preserves a retryable session; success clears it after server confirmation | Passed: failed/successful logout cases in [Frontend tests](../scripts/test-phase-one-frontend.cjs) |
| T09 / FR-03 | Isolated browser with synthetic orders: navigate pages, change filter, select 20 items per page | Pagination visible; filter resets page; 20 rows displayed | Observed as expected in the second-round [repair log](修缮清单与验证.md); not a production-data check |
| T10 / deployment readiness | Public release `70bd1d2`: request health, empty login with site Origin, and orders without a token | Health UP; login reaches validation; protected orders return HTTP 401 | Observed: UP, business code 40001, and HTTP 401 in [cloud records](云端部署验证记录.md) |

Run the automated suites with `mvn test` and `node --test scripts/test-phase-one-frontend.cjs` after following the root README. H2 results do not substitute for MySQL compatibility checks. The table is a representative selection, not a claim of complete requirement coverage.

| Remaining check | Expected outcome | Status |
|---|---|---|
| Full customer, staff, and administrator browser demonstration | Complete the workflow below against one identified deployment and record each result | Pending |
| Installation on a clean machine | A reviewer can follow the README, start the service, and create evaluation accounts | Pending |
| Narrow-screen and keyboard use | Forms, errors, navigation, and actions remain accessible | Pending |

## Evolution Examples

| Issue | Changes and Reasons | Verification Evidence |
|---|---|---|
| HTTPS same-origin requests via proxy were rejected by CORS | Handle CORS after restoring forwarded headers, while retaining origin restrictions | ProxyCorsTest and public deployment checks with `Origin` |
| Login/registration errors were hidden behind the authentication overlay | Place errors within the form and keep them visible until input is modified or the form is resubmitted | Page scripts and browser verification |
| Customer pagination bar not displayed | Add bidirectional update binding for controlled `page-size` | Isolated browser checks confirmed page navigation and switching to 20 items per page |
| Switching filters retains old page numbers | Returns to the first page when status/role changes | Customer and administrator script checks, plus customer browser verification |
| Old requests interfering with new accounts | Verify the token that initiated the request in the response; discard responses from old sessions | Regression testing for delayed success responses, 401 errors, and JSON parsing |
| Still displays success after logout failure | Clean up the current session only after the server confirms token revocation; retain retry status upon failure | Regression testing for success and network failures |

We initially suspected that image removal was not synchronized, but browser testing confirmed that the original component synchronizes and disables submission; therefore, we reverted the unnecessary changes. This is an example of controlling the scope of modifications based on reproduction results.

The evidence supports discussion of requirements traceability, architectural design, quality assurance, and software evolution. Team processes, meeting records, and individual contributions still need to be documented in the project report and personal logbooks.

## Evaluation Demonstration Steps

Prepare customer, staff, and administrator accounts in a separate database; use synthetic business data.

1. A customer logs in, creates an order, records the tracking number, and views the list and tracking history.
2. A staff member moves items from the shared to-do list from “pending” → “transit” → “delivering”; each step should generate a tracking record and corresponding to-do item.
3. A staff member uploads synthetic PNG/JPEG files that meet the restrictions for a delivery task and confirms that the upload does not automatically mark the item as received.
4. The customer first enters an incorrect confirmation code, then uses the correct code from the order; after confirming receipt, submit feedback once and re-query the order.
5. The administrator views statistics and monthly reports, modifies the roles of designated test accounts, or enables/disables them; reload the page after saving settings.
6. Verify that other customers cannot view this order; use synthetic items to demonstrate one-in, one-out inventory.

This is a proposed demonstration checklist. A full run is still pending; the steps are not a record of completed public deployment tests.

## Next Steps

Complete end-to-end browser acceptance testing for all three roles, verify installation on a brand-new machine, and check for compatibility with narrow screens and keyboards. Potential extensions include pickup addresses, individual task assignments, feedback replies, and persistent notification read status. These require team agreement on scope. Organize team management processes and individual logs based on actual meeting and task records.
