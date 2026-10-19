# CBC Spring Boot integration

Spring exposes three SOAP operations to the .NET analyzer service. It keeps **configuration only** in a local PostgreSQL database: machine information, CBC category order and machine-specific extraction settings, and DHIS2 metadata/mappings. Patient, sample, analyzer result, and delivery records are not stored in Spring's database.

## SOAP contract

When Spring is running, the endpoint is `http://localhost:8080/ws` and the WSDL is `http://localhost:8080/ws/machineReaderService.wsdl`. Requests use namespace `http://health.goverment/service/cbc`, `Content-Type: text/xml`, and the `X-CBC-Api-Key` header matching `CBC_API_KEY` in `service/.env`.

When Spring is reached through an HTTPS reverse proxy such as Cloudflare Tunnel, forwarded headers are used to advertise the public HTTPS SOAP address in the WSDL.

| Operation | Request | Response |
| --- | --- | --- |
| `getSampleData` | `machineId`, `sampleId` | Ordered CBC category definitions: `categoryId`, `testCode`, `resultOrder`, optional `startPosition` and `categoryLength`, and `factor`. The `sampleId` is retained in the contract; ordering comes from machine/category configuration, not a stored sample. |
| `barCodeData` | `machineId`, `sampleId` | One 11-digit barcode and its machine ID. It uses the configured machine `sample_type_id` and the `2 + 2 + 7` format. Spring cannot verify a sample assignment because it stores no samples. |
| `submitCBCResults` | Stable `requestId`, `machineId`, `sampleBarcode`, `branchId`, `patientCode`, `patientName`, `patientGender`, `patientAge`, optional `measuredAt`, and one or more results with `testCode`, `testValue`, optional `testUnit` | `SUCCESS` or `FAILED`, counts, a deterministic DHIS2 `eventUid`, and a message. Spring sends patient details and results directly to DHIS2 without saving them locally. |

The .NET caller must supply the patient details, barcode and branch with every result request. On a DHIS2 failure, the .NET caller must retry the same payload with the same `requestId`; Spring has no local result queue. The event UID is derived from `requestId`, so retries target the same DHIS2 event. A reused `requestId` with different data cannot be detected locally. The exact .NET proxy contract still needs validation against its real WSDL/messages.

## PostgreSQL and seed

Spring connects to the existing local PostgreSQL database **analyzers** on `127.0.0.1:5432`. Copy `.env.example` to `.env` inside `service/`, then set `DB_USER`, `DB_PASSWORD`, `DHIS2_PASSWORD`, and `CBC_API_KEY`. Keep `.env` private; it is excluded from Git. Spring reads it when started directly from IntelliJ with `service` as the working directory; environment variables can override its values. Spring creates the 13 configuration tables from `src/main/resources/schema.sql` and loads `src/main/resources/seed.sql` on startup. The seed file is divided into DHIS2 connection/program, data elements, stage and sections, ordered CBC categories, result mappings, and site-specific machine/branch configuration. It is safe to run repeatedly: existing configuration rows are preserved.

The seed contains no invented machine or branch IDs. Add the real machine rows and branch-to-org-unit mappings in its last section once those IDs are known. No patient, sample, or result data is saved in this database.

From `service/`:

```powershell
.\config\run-local.ps1
```

`run-local.ps1` loads `service/.env` and runs Spring. Set `DHIS2_PASSWORD` for the local DHIS2 integration account and `CBC_API_KEY` for the .NET caller. The default JDBC URL is `jdbc:postgresql://127.0.0.1:5432/analyzers`.

The DHIS2 event program metadata is provisioned by `config/provision-dhis2-cbc.ps1`. It has three sections: **Analyzer**, **Patient Info**, and **Results**. Its local program UID is `CAB738DA06F`, stage UID `CC615299A29`, and org-unit UID `qABHIseAqcT`. A `dhis2_org_unit_mapping` row must map each incoming `branchId` to the correct DHIS2 org unit. The integration currently supports DHIS2 event programs; a tracker program needs enrollment handling.

Run `./mvnw.cmd test` to exercise the SOAP operations and a local DHIS2 stand-in. H2 is used only during tests; production runs against local PostgreSQL.
