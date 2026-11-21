# KONGKLANG — Inventory & POS

Multi-store inventory, stock receipt, sales, purchasing, reporting and store administration.

Built with Java 21, Spring Boot 4, Thymeleaf, session authentication and MySQL-compatible storage. Includes the compiled Flutter web client under `/mobile/`.

## Deployment

See [Render + TiDB demo setup](docs/CLOUD_DEMO.md). Deployment files contain no database credentials. Configure secrets in the hosting dashboard. Live cloud validation is still required.

## Build

`./mvnw test` runs local unit tests. Database integration tests require an explicitly configured disposable MySQL database.

`./mvnw -DskipTests package` builds the executable JAR. Docker uses the same packaging process.
