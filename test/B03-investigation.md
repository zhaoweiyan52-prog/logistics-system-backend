# B03 Investigation — Open

November 4, 2026. This record supplements the tester's report; it does not close B03 or replace independent retesting.

## Findings

Both list and detail queries restrict customer results using the authenticated user's ID. The exported collection already uses `GET /api/orders/{{orderNo}}` and saves `data.no` after creation. The export alone does not establish which URL, variable scope, or account was used in the failing request.

A separate synthetic customer was registered and logged in against the public backend. With one unchanged customer token, the developer created an order, found its exact number in the history list, and queried its detail:

| Check | Observed result |
|---|---|
| Create `POST /api/orders` | `code: 0`; one initial trace |
| History `GET /api/orders?page=1&size=10` | `code: 0`; exactly one matching entry |
| Detail `GET /api/orders/SF20261104402AC09909FD4EEB9784B4D3D7FA23E2` | `code: 0`; same number; one initial trace |
| Logout | `code: 0` |

The synthetic account `b03_88c9133b62` and its order are retained as test evidence. Existing customer data was not changed. Credentials and tokens were not included in this record. B03 was **not reproduced in this run**; its cause remains unconfirmed.

## Collection Corrections

- History URL: removed the literal `GET ` prefix from the URL field. The method remains GET.
- History assertions: use `data.list` (array) and `data.total` (number); the outer `data` is a pagination object.
- Creation asserts a complete generated order number before saving it. Detail assertions check that the resolved `orderNo` equals the saved environment value and that the response returns that same number and a trace array.

These are confirmed collection corrections and diagnostic checks, not a proven fix for B03. No backend ownership checks or API contracts were changed.

## Retest

Import the updated collection and environment, set `baseUrl`, and use a dedicated customer account. Run Login, Create Parcel, Track Parcel, then View History in the same environment. The exported Register/Login bodies contain the tester's fixed test account; use your own test credentials as appropriate, and do not repeatedly register an already registered username.

If B03 persists, provide the fully resolved request URL from Postman Console, the matching history entry's complete `no`, the login response's account/role, and response `code`/message. Do not export Authorization headers or passwords. Check for whitespace, an unresolved variable, a higher-priority variable overriding `orderNo`, and requests using different accounts or base URLs. These are investigation possibilities, not established causes.

Developer verification used direct HTTP calls. JSON parsing and script syntax checks do not constitute execution in the tester's Postman session. The tester should retain B03 as Open until the failing case is explained and retested.
