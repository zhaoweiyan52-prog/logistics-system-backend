# Logistics System Test Cases
Test Environment: Public backend https://logistics-system-backend-o7if.onrender.com
Version: 1eb7d99
Tester: ZHANG Wenyu

| Test ID | Role | Test Scenario | Input | Expected Result | Status |
| ---- | ---- | ---- | ---- | ---- | ---- |
| TC-C01 | Customer | Customer register account | Fill valid username, password, role | Account created successfully, can log in | Passed |
| TC-C02 | Customer | Create new delivery request | Fill receiver, dest, goods, weight, fee | New order created | Passed |
| TC-C03 | Customer | Generate tracking number | Create multiple parcel orders and compare returned order numbers | Distinct order numbers across the submitted orders | Passed |
| TC-C04 | Customer | Query parcel status | Input tracking number | Show real-time parcel status | Failed — B03 Open |
| TC-C05 | Customer | View delivery history | Enter customer account page | List all past delivery records | Passed |

Dynamic statuses above reflect the tester's reported execution. Repeated-order uniqueness does not establish concurrency coverage. B03 remains Open; see [developer investigation](B03-investigation.md).

---
### Static Code Review Round 1 (Source Code Inspection, not runtime test)
| Test ID | Role | Test Scenario | Review Result |
| ---- | ---- | ---- | ---- |
| TC-C01 | Customer | Customer register account & log in | Pass. AuthController contains register & login API |
| TC-C02 | Customer | Create new delivery request | Pass. OrderController has create order API |
| TC-C03 | Customer | Generate tracking number | Pending. Tracking number logic is inside OrderService, cannot verify in controller |
| TC-C04 | Customer | Query parcel status | Pass. Detail API by tracking number exists |
| TC-C05 | Customer | View delivery history | Pass. List API to fetch user order history exists |
