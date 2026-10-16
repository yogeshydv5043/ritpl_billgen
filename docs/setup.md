# Setup and operations

## Local stack

Use the root Compose file for the full local stack:

```bash
cp .env.example .env
# Edit DEMO_PASSWORD / other local values.
docker compose up --build -d
docker compose ps
```

The public entry point is `http://localhost:${HTTP_PORT:-8080}`. The app uses the `dev` profile and waits for PostgreSQL and Redis health checks before startup. Flyway applies migrations automatically.

Common maintenance:

```bash
docker compose logs -f nginx gateway billgen-a billgen-b
docker compose restart billgen-a
docker compose down
```

The named volumes `postgres-data` and `redis-data` persist between container restarts. `docker compose down -v` permanently removes them.

## Environment variables

| Variable | Default / profile behavior | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` in Compose | Selects `dev` or `prod` configuration. |
| `HTTP_PORT` | `8080` | Host port exposed by Nginx. |
| `POSTGRES_DB` | `billgen` | PostgreSQL database name. |
| `POSTGRES_USER` | `billgen` | PostgreSQL application user. |
| `POSTGRES_PASSWORD` | local-only `billgen_dev_only` | PostgreSQL password. The dev profile uses this as a fallback when `SPRING_DATASOURCE_PASSWORD` is not set; use a unique secret outside local development. |
| `REDIS_PASSWORD` | blank locally | Redis password. If non-empty in Compose, Redis starts with authentication enabled. |
| `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` | `redis` / `6379` in Compose | Redis endpoint for cache and Spring Session. |
| `APP_DEMO_SEED_ENABLED` | `true` in the local Compose stack | Seed example accounts, customers, invoices, products and plans. Set false outside local development. |
| `DEMO_PASSWORD` | local-only `ChangeMe123!` | Shared password for the three seeded demo accounts. |
| `BOOTSTRAP_ADMIN_EMAIL` | blank | Optional first Admin when demo data is disabled. |
| `BOOTSTRAP_ADMIN_PASSWORD` | blank | Password for the bootstrap Admin; set with the email. |
| `SESSION_COOKIE_SECURE` | `false` in dev | Mark session cookies Secure. Set true when serving over HTTPS. |
| `DB_POOL_SIZE` | `6` in Compose | Per-instance Hikari maximum pool size. |
| `APP_SECRETS_ENCRYPTION_KEY` | blank | Encrypts the SMTP password an administrator stores in Email settings. Generate one per environment with `openssl rand -hex 32`. With no key the application runs and says so on that screen, but a mail password cannot be saved - it is never written in plain text. Changing the key makes a previously stored password unreadable, so enter the password again after rotating it. |

The complete local example is [.env.example](../.env.example). Spring Boot optionally imports `.env` as properties when launched from the project directory, so the same `POSTGRES_PASSWORD` can configure a local Maven run; Compose also reads it. A real `.env` is git-ignored and excluded from the Docker build context and project ZIP.

### Production checklist

- Use `SPRING_PROFILES_ACTIVE=prod` and `APP_DEMO_SEED_ENABLED=false`.
- Set strong, unique PostgreSQL and Redis passwords; configure secrets through the deployment platform, not a committed file.
- Set both `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` for initial provisioning; rotate/remove the bootstrap secret after use.
- Set `APP_SECRETS_ENCRYPTION_KEY` before using the mail settings screen, and treat it like the database password: it is the only thing standing between a stolen backup and a usable SMTP credential.
- Set `SESSION_COOKIE_SECURE=true`; expose BillGen only over HTTPS through a trusted reverse proxy.
- Keep PostgreSQL, Redis, both application instances and the gateway on a private network. Only the ingress/Nginx entry point should be public.
- Restrict actuator access, configure logs/metrics and backups, and review retention/data-protection requirements before handling customer data.
- Supply the gateway with the correct `BILLGEN_A_URI` and `BILLGEN_B_URI` targets for the two app instances.

The bundled Nginx config listens on plain HTTP inside the Compose network; it is a local reverse proxy, **not** a TLS terminator. Put a managed HTTPS ingress/load balancer in front for production and ensure the forwarded scheme/host headers come only from trusted infrastructure.

## Running just the web app

Start PostgreSQL and Redis locally. Copy `.env.example` to `.env`, then run from the project directory:

```bash
# macOS / Linux
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run
```

```powershell
# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

The sample local role uses `billgen_dev_only`. If your existing PostgreSQL role has another password, either change `POSTGRES_PASSWORD` in `.env` to that role's password or set `SPRING_DATASOURCE_PASSWORD` in the shell. `SPRING_DATASOURCE_PASSWORD` takes precedence. The `.env` import is optional; environment variables always override it.

For `prod`, the required datasource and Redis settings have no fallback defaults. Run `mvn test` for the unit tier (plus the PostgreSQL integration tier when Docker is available), or `mvn package` to build the executable `target/billgen.jar`.

## Troubleshooting: PostgreSQL password authentication

`FATAL: password authentication failed for user "billgen"` (SQL state `28P01`) means PostgreSQL rejected the configured credentials; Flyway is only the first component trying to connect. The JDK `sun.misc.Unsafe` / Netty message is a separate warning and is not the startup failure.

Make the application's datasource password match the password assigned to the PostgreSQL role `billgen`:

```powershell
# Windows PowerShell; use your actual local PostgreSQL role password.
$env:SPRING_DATASOURCE_PASSWORD = "<your billgen role password>"
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

Alternatively, put that same value in `POSTGRES_PASSWORD` in the project-root `.env`; the dev configuration uses it when `SPRING_DATASOURCE_PASSWORD` is unset. Check for a stale `SPRING_DATASOURCE_PASSWORD` in the shell or IDE Run Configuration first—it overrides `.env`. In PowerShell, clear an old shell override with:

```powershell
Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

If you do not know the role password and this is a local development database, reset the role while signed in as a PostgreSQL administrator. For a native PostgreSQL installation, open an administrator `psql` session:

```powershell
psql -h localhost -U postgres -d postgres
```

At the `psql` prompt, enter `\password billgen`, set the password to the same value as `POSTGRES_PASSWORD` in the project-root `.env` (for the sample default, `billgen_dev_only`), then enter `\q`. For the Compose database, use `docker compose exec postgres psql -U billgen -d billgen` and run the same `\password billgen` command. This changes the role password without deleting database data. Do not reset a shared/production role this way.

Changing `.env` alone does not change a PostgreSQL role in a database initialized earlier. Avoid `docker compose down -v` as a password fix because it deletes the database volume.

The `sun.misc.Unsafe` warning is separate from SQL state `28P01` and does not stop startup. Netty 4.1 can emit it on Java 24+; this project targets Java 21, which is below that, so no workaround is needed. Check both `java -version` and `mvn -version` to see which JDK Maven is using.

## Troubleshooting: startup stops with `jpaSharedEM_entityManagerFactory`

If the application stops shortly after the Flyway line that prints the JDBC URL, and the first error reads like

```
Error creating bean with name 'accountStatusFilter' ... Unsatisfied dependency expressed through constructor
parameter 0: ... Cannot resolve reference to bean 'jpaSharedEM_entityManagerFactory'
```

then the bean named there is not the problem. The shared `EntityManager` is resolved from the
`entityManagerFactory` bean, so when that one fails to build, every JPA repository, and then the filters
that use them, report an unsatisfied dependency. The cause is above this in the log - look for the first
`APPLICATION FAILED TO START` or `Caused by:` block:

```powershell
mvn spring-boot:run 2>&1 | Select-String -Pattern "Caused by","Migr","Validation","Schema" -Context 0,2
```

```bash
mvn spring-boot:run 2>&1 | grep -E -A2 "Caused by|Successfully validated|Successfully applied|Schema-validation|Checksum mismatch"
```

Two causes are possible in this project, and both are read from the same table:

```sql
select installed_rank, version, description, success, checksum from flyway_schema_history order by installed_rank;
```

- **`Migration checksum mismatch for version N`.** The SQL file behind a version that this database already
  applied has changed underneath it - usually a pulled branch whose migration was edited rather than
  extended. The fix belongs in the repository (an append-only `V(n+1)`, as `V13__notification_settings_prune_inert_codes.sql`
  is for `V12`). To get a development database moving again while that lands, run Flyway once without
  checksum validation - `SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false mvn spring-boot:run` - which applies the
  pending versions and leaves the data alone. Do not use it to get past a mismatch on a shared or production
  database: there the file that changed is the defect.
- **`Schema-validation: missing column ... in table ...`.** The entities and the DDL disagree, which means a
  migration is missing from this database (`version` will stop short of the one you expect) or the mapping is
  wrong. Re-pull and restart so the pending migration applies; do not set `spring.jpa.hibernate.ddl-auto` to
  `update` or `none` to hide it - `validate` is what keeps `@Column` definitions and the schema honest.

A local database that is simply behind can be rebuilt from scratch instead. For the Compose stack,
`docker compose down -v && docker compose up -d` recreates an empty schema and every migration reapplies;
this deletes all local data, so keep it to development.
