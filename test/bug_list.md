# Bug List
Test Environment: Public backend https://logistics-system-backend-o7if.onrender.com
Version: 1eb7d99
Tester: ZHANG Wenyu

Developer follow-up: [B03 investigation and retest instructions](B03-investigation.md). A separate public create/list/detail run passed; B03 remains Open pending the original resolved request and retest. Confirmed Postman history URL/response-shape issues were corrected in the collection.

Latest B03 follow-up: the tester's revised URL omitted one `F` from the actual history number (`F9514EFB` versus `F9514EFFB`). Same-customer public requests returned `40401` for the copied number and `0` for the exact history number. See the investigation record for both complete values. Keep Open until the tester confirms the corrected request.

| Bug ID | API Endpoint | Method | Request Body / Param | Expected Result | Actual Response | Reproduction Steps | Status |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| B01 | /api/orders | POST | {<br>    "receiver": "Bob",<br>    "dest": "HKMU",<br>    "goods": "books",<br>    "weight": 1.2,<br>    "fee": 25<br>} | System generates unique tracking number for each parcel | Multiple orders created, all order no are distinct | 1. Register and login<br>2. Create several orders repeatedly<br>3. Check generated order no | Passed |
| B02 | /api/orders | POST | {<br>    "receiver": "Bob",<br>    "dest": "HKMU",<br>    "goods": "books",<br>    "weight": 1.2,<br>    "fee": 25<br>} | code=0, parcel created successfully, return order no | {<br>  "code": 0,<br>  "data": {<br>    "no": "SF20261104A5EC426BDB294F3AAA0013EAFA91576D",<br>    "goods": "books",<br>    "dest": "HKMU",<br>    "receiver": "Bob",<br>    "fee": 25.00,<br>    "weight": 1.200,<br>    "status": "pending",<br>    "time": "2026-11-04T06:37:44.860020Z",<br>    "eta": "2026-11-07T06:37:44.860020Z",<br>    "signCode": "1425",<br>    "traces": []<br>  },<br>  "message": "成功"<br>} | 1. Register testuser01 and login<br>2. Obtain Bearer token<br>3. Send POST request with valid parcel data | Fixed |
| B03 | /api/orders/{no} | GET | Path variable: orderNo = SF20261104A5EC426BDB294F3AAA0013EAFA91576D | code=0, return order detail of the given order no | {<br>  "code": 40401,<br>  "data": null,<br>  "message": "运单不存在或无权查看"<br>} | 1. Register testuser01 and login<br>2. Create order successfully and get orderNo<br>3. The same user can view this order in order list, but GET /api/orders/{{orderNo}} fails | Open |
