# Bug List
Test Environment: Public backend https://logistics-system-backend-o7if.onrender.com
Version: 1eb7d99
Tester: ZHANG Wenyu

Developer follow-up: [B03 investigation and retest instructions](B03-investigation.md). A separate public create/list/detail run passed; B03 remains Open pending the original resolved request and retest. Confirmed Postman history URL/response-shape issues were corrected in the collection.

Latest B03 follow-up: the tester's revised URL omitted one `F` from the actual history number (`F9514EFB` versus `F9514EFFB`). Same-customer public requests returned `40401` for the copied number and `0` for the exact history number. See the investigation record for both complete values. Keep Open until the tester confirms the corrected request.

| Bug ID | API Endpoint | Method | Request Body / Param | Expected Result | Actual Response | Reproduction Steps | Status |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| B01 | Customer | New parcel order can be created and unique tracking number generated | N/A | Passed | Verified |
| B02 | Customer | Customer history record can be displayed normally | N/A | Passed | Verified |
| B03 | Customer | Query parcel detail return 404 even with correct order | Typo / incomplete tracking number when copying | Fixed | Verified by tester. Full correct tracking number works. |
