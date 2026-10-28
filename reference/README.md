# Original Reference Materials

[Project overview](../README.md) · [Current documentation](../docs/README.md)

This directory preserves the original materials used during integration:

| File | Role |
|---|---|
| [API_CONTRACT.md](API_CONTRACT.md) | Original frontend/backend interface agreement |
| [logistics-admin.html](logistics-admin.html) | Original single-page frontend |
| [mock-server.js](mock-server.js) | Mock service used with the original frontend |

The running application uses [src/main/resources/static/logistics-admin.html](../src/main/resources/static/logistics-admin.html). Follow the root README to run the Spring Boot application.

Sample accounts and mock verification results in this folder are not credentials or acceptance results for the current deployment. The original contract says 20 operations but lists 21; the current implementation adds three operations, for 24 in total. See the [current API contract](../docs/DESIGN.md#http-contract) and [verification evidence](../docs/VERIFICATION.md).

These source materials are retained for provenance. Current requirements and behavior are described in the project documentation.
