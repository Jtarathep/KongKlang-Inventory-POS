# Render + TiDB demo deployment

Status: application packaging is prepared; live TiDB compatibility and hosted smoke tests are required before sharing the demo.

## Database

Create a TiDB Starter MySQL-compatible instance with a $0 spending limit. Use a dedicated empty demo database, separate from business data. Import the reviewed schema before starting the application: Hibernate uses `validate`, not automatic schema creation. Run `database/tidb-demo-schema.sql` once, stopping at the first error. It creates the new `kongklang_demo` database and preserves foreign keys and CHECK constraints, with parent tables first and Unicode collations. It enables TiDB CHECK enforcement before creating tables. Generated-file structural checks have passed; live TiDB execution is still pending. Do not use the original `database/schema.sql` as a cloud import: it contains DROP statements. Do not import local production data into this public demo.

Product images are stored in the database in `product_photos`, not on the ephemeral web-service filesystem.

## Render

Use the repository's `render.yaml` Blueprint. It explicitly selects the free Docker web-service plan and the `cloud` Spring profile. Supply these secret values in the Render dashboard:

- `KONGKLANG_DB_URL`: `jdbc:mysql://HOST:4000/kongklang_demo?sslMode=VERIFY_IDENTITY&serverTimezone=Asia/Bangkok&characterEncoding=UTF-8`
- `KONGKLANG_DB_USER`: the TiDB connection username (including its prefix)
- `KONGKLANG_DB_PASSWORD`: the database password

Use the actual hostname and port from TiDB Connect. Keep TLS identity verification enabled. Never commit credentials or database dumps.

After schema validation succeeds, provision an initial system administrator using the existing explicit bootstrap mechanism: temporarily set `APP_SYSTEM_ADMIN_BOOTSTRAP=true`, `KONGKLANG_SYSTEM_USERNAME`, and a strong `KONGKLANG_SYSTEM_PASSWORD` of 12–72 UTF-8 bytes. After successful provisioning, disable bootstrap and remove the bootstrap credentials. Create separate demo stores/users through the system administrator UI. Do not enable the local development launcher on the host.

## Behavior and checks

- Secure session cookies and forwarded HTTPS support are enabled by the cloud profile.
- The connection pool is limited to five connections.
- Local Ollama/llama.cpp is disabled on the cloud profile. Deterministic store-data answers remain available; generated-model suggestions require a separately configured hosted model.
- Free hosting may sleep and has resource quotas. This configuration is for demonstration, not an assurance of production availability.
- Before sharing: test login/logout, store isolation, stock receipt, sales, images, reports, and mobile login using the hosted HTTPS URL. Test a restart to confirm data persists.
