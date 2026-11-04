# Logistics System Test Cases
| Test ID | Role | Test Scenario | Input | Expected Result | Status |
| ---- | ---- | ---- | ---- | ---- | ---- |
| TC-C01 | Customer | Customer register account | Fill valid username, password, role | Account created successfully, can log in | Not Executed |
| TC-C02 | Customer | Create new delivery request | Fill receiver, dest, goods, weight, fee | New order created | Not Executed |
| TC-C03 | Customer | Generate tracking number | Submit new parcel order, run concurrent test, check UUID and DB primary key constraint | No duplicate tracking number, no database primary key conflict | Not Executed |
| TC-C04 | Customer | Query parcel status | Input tracking number | Show real-time parcel status | Not Executed |
| TC-C05 | Customer | View delivery history | Enter customer account page | List all past delivery records | Not Executed |
---
### Static Code Review Round 1 (Source Code Inspection, not runtime test)
| Test ID | Role | Test Scenario | Review Result |
| ---- | ---- | ---- | ---- |
| TC-C01 | Customer | Customer register account & log in | Pass. AuthController contains register & login API |
| TC-C02 | Customer | Create new delivery request | Pass. OrderController has create order API |
| TC-C03 | Customer | Generate tracking number | Pending. Tracking number logic is inside OrderService, cannot verify in controller |
| TC-C04 | Customer | Query parcel status | Pass. Detail API by tracking number exists |
| TC-C05 | Customer | View delivery history | Pass. List API to fetch user order history exists |