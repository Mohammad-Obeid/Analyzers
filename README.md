# Analyzers

CBC analyzer integration with DHIS2.

- [`service/`](service/) is the runnable Spring Boot SOAP service. See its [setup and API guide](service/README.md).
- [`service - spring boot - dhis2/`](service%20-%20spring%20boot%20-%20dhis2/) contains the original service examples used as reference.
- [`CBC_Spring_Design.docx`](CBC_Spring_Design.docx) contains the design document.

Copy `service/.env.example` to `service/.env` and fill in local PostgreSQL, DHIS2, and API key values before starting the service. The real `.env` file is excluded from Git.
