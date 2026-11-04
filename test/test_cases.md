# Logistics System Test Cases
Test Environment: Public backend https://logistics-system-backend-o7if.onrender.com
Version: 1eb7d99
Tester: ZHANG Wenyu

| Test Case ID | Role | Test Scenario | Input | Expected Output | Status |
| ---- | ---- | ---- | ---- | ---- | ---- |
| TC-C01 | Customer | Customer register and login | Username, password | Account registered and login successfully | Passed |
| TC-C02 | Customer | Create new parcel application | Sender address, receiver address, parcel info | New order created, unique tracking number generated | Passed |
| TC-C03 | Customer | Generate unique tracking number for new parcel | Submit new parcel request | System return unique tracking number | Passed |
| TC-C04 | Customer | Query parcel real-time status | Complete tracking number | System shows latest parcel information | Passed |
| TC-C05 | Customer | View historical delivery records | Enter customer account page | All past parcel records displayed | Passed |

---
### Static Code Review (Source Code Inspection, not run-time test)
| Review ID | Role | Review Item | Expected Result | Review Result |
| ---- | ---- | ---- | ---- | ---- |
| SRC-C01 | Customer | Register & Login code | Authentication logic reasonable | Passed |
| SRC-C02 | Customer | Create order code | Data validation included | Passed |
| SRC-C03 | Customer | Tracking number generation code | Guarantee uniqueness | Passed |
| SRC-C04 | Customer | Parcel query code | Correct lookup by tracking number | Passed |
| SRC-C05 | Customer | History record code | Can fetch user-related records | Passed |