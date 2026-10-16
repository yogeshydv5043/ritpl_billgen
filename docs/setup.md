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
| `POSTGRES_DB` | `billgen` | PostgreSQL database name. The Compose container creates that database, and a local run assembles its JDBC URL from it. |
| `POSTGRES_USER` | `billgen` | PostgreSQL role the **Compose** container creates for the application, and the credentials a local run picks up from an inherited `.env`. A run that names neither uses the local superuser instead - see `DB_USER`. |
| `POSTGRES_PASSWORD` | local-only `billgen_dev_only` | Password for the role above, shared by the container and the app inside Compose. A local run reads it from `.env` unless `DB_PASSWORD` is set. Use a unique secret outside local development. |
| `DB_USER` | `postgres` (development profiles) | The role a local `mvn spring-boot:run` connects as, when no `SPRING_DATASOURCE_USERNAME` and no `.env` role is set. Overrides `POSTGRES_USER`. |
| `DB_PASSWORD` | `postgres` (development profiles) | The password for that role. Overrides `POSTGRES_PASSWORD`, and yields to `SPRING_DATASOURCE_PASSWORD`. Set it instead of changing the local superuser's password. |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Compose sets all three | The application's own datasource settings, and the last word in every profile: they outrank `DB_*` and `POSTGRES_*`. Required, with no fallback, in `prod`. |
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
- In `prod` the datasource and Redis settings are bare variables: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`. The local defaults and `DB_USER` / `DB_PASSWORD` belong to the development profiles and are deliberately absent there, so a deployment with a missing secret fails at startup rather than connecting as something unintended.
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

Without Docker, no `.env` is required at all: the development configuration opens
`jdbc:postgresql://localhost:5432/billgen` as the superuser `postgres` with password `postgres`, the role
and password a local installation already has, so a `createdb -h localhost -U postgres billgen` is the only
setup step. If your local `postgres` password is not `postgres`, set `DB_PASSWORD` rather than resetting the
superuser. The full precedence, pinned by `DatabaseCredentialConfigurationTest`:
`SPRING_DATASOURCE_PASSWORD` > `DB_PASSWORD` > `POSTGRES_PASSWORD` (from `.env`) > `postgres`, and the same
shape for the role through `SPRING_DATASOURCE_USERNAME` > `DB_USER` > `POSTGRES_USER` > `postgres`.

Copying `.env.example` matters for Compose, where the postgres container creates a `billgen` role so the
application is not running as the superuser. A local run reads that same file, so a copied `.env` pins the
role pair to `billgen` / `billgen_dev_only` unless `DB_USER` / `DB_PASSWORD` say otherwise - name both or
neither, because half an inherited pair is the usual cause of the `28P01` below. The `.env` import is
optional; real environment variables always override it.

For `prod`, the required datasource and Redis settings have no fallback defaults. Run `mvn test` for the unit tier (plus the PostgreSQL integration tier when Docker is available), or `mvn package` to build the executable `target/billgen.jar`.

## Troubleshooting: PostgreSQL password authentication

`FATAL: password authentication failed for user "postgres"` - or for `billgen` when a project-root `.env`
is in play - (SQL state `28P01`) means PostgreSQL rejected the configured credentials; Flyway is only the
first component trying to connect. The JDK `sun.misc.Unsafe` / Netty message is a separate warning and is not
the startup failure.

Confirm the credentials themselves before touching the application. In a real PostgreSQL environment,
`psql` is the shortest test of exactly what the app will do, and it is read-only:

```powershell
psql -h localhost -p 5432 -U postgres -d billgen -c "select current_user, inet_server_port(), version();"
```

If that prints a row, the configuration is right and anything else is a role/database detail; if it asks for
a password you do not know, the role's password - not the app setting - is what has to change.

Then give the application the same credentials. For a local cluster, name the password the role actually
has; do not reset the superuser to match a file:

```powershell
# Windows PowerShell; use the actual password of the local role you are connecting as.
$env:DB_PASSWORD = "<your postgres role password>"
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

`DB_PASSWORD` beats the `POSTGRES_PASSWORD` in a project-root `.env`, and `SPRING_DATASOURCE_PASSWORD`
beats both. Check for a stale `SPRING_DATASOURCE_PASSWORD` in the shell or IDE Run Configuration first - it
overrides everything below it, including a `.env` you edited. A role that does not exist at all
(`FATAL: role "..." does not exist`, SQL state `28000`) is a different problem: the `.env` you copied names
the Compose role, so either create it (`createuser`) or drop the `POSTGRES_USER` / `POSTGRES_PASSWORD` lines
so the local superuser applies. In PowerShell, clear an old shell override with:

```powershell
Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

If you do not know the role password and this is a local development database, reset the role while signed in as a PostgreSQL administrator. For a native PostgreSQL installation, open an administrator `psql` session:

```powershell
psql -h localhost -U postgres -d postgres
```

At the `psql` prompt, enter `\password billgen`, set the password to the same value as `POSTGRES_PASSWORD` in the project-root `.env` (for the sample default, `billgen_dev_only`), then enter `\q`. That is the role Compose creates for the application, and resetting a dedicated role is a reasonable development step; resetting a shared or production superuser is not, and there `DB_PASSWORD` / `SPRING_DATASOURCE_PASSWORD` are the levers. For the Compose database, use `docker compose exec postgres psql -U billgen -d billgen` and run the same `\password billgen` command. This changes the role password without deleting database data. Do not reset a shared/production role this way.

Changing `.env` alone does not change a PostgreSQL role in a database initialized earlier. Avoid `docker compose down -v` as a password fix because it deletes the database volume.

The `sun.misc.Unsafe` warning is separate from SQL state `28P01` and does not stop startup. Netty 4.1 can emit it on Java 24+; this project targets Java 21, which is below that, so no workaround is needed. Check both `java -version` and `mvn -version` to see which JDK Maven is using.

## Troubleshooting: startup stops with `jpaSharedEM_entityManagerFactory`

If the application stops shortly after the Flyway line that prints the JDBC URL, and the first error reads like

```
Error creating bean with name 'accountStatusFilter' ... Unsatisfied dependency expressed through constructor
parameter 0: ... Cannot resolve reference to bean 'jpaSharedEM_entityManagerFactory'
```

then the bean named there is not the problem. The shared `EntityManager` is resolved from the
`entityManagerFactory` bean, so when that one fails to build, every JPA repository - and then the filters
that use them - reports an unsatisfied dependency. The cause is above this in the log; pull it out
explicitly:

```powershell
mvn spring-boot:run 2>&1 | Select-String -Pattern "Caused by","checksum","Schema-validation","Successfully applied" -Context 0,2
```

```bash
mvn spring-boot:run 2>&1 | grep -E -A2 "Caused by|checksum|Schema-validation|Successfully applied"
```

Two causes are possible in this project. Read the history table to tell them apart:

```sql
select installed_rank, version, description, checksum, success, installed_on
  from flyway_schema_history order by installed_rank;
```

- **`Migration checksum mismatch for migration version N`.** The SQL file behind a version this database
  already applied has changed. The number in the message is the proof: `Applied to database` is the stored
  checksum, `Resolved locally` is the file in your working tree, and the stored one matches the file as it
  stood in an earlier commit.
  - If your database is behind the repository, the fix is on the repository side and is already described by
    that pair of numbers: pull, and the pending versions apply. This is why `V13` and `V14` exist - the
    changes they carry were first written into `V12` and `V10` after those had already been applied.
  - `FlywayMigrationFilesTest` pins the checksum of every migration that has shipped, so editing an old file
    fails a test instead of your startup. If that test fails after you deliberately changed a migration that
    has already been released, you are doing the thing it warns about: put the change in a new version.
  - If *your* database applied a version whose file was later rewritten while it was still unreleased - the
    one case where your history is the odd one out - `flyway repair` rewrites the stored checksum to match
    the file. Understand what repair does and does not do: it updates Flyway metadata only, it never re-runs
    a migration, so the statements that were added to that file have still not executed on your database
    unless a later version applies them. Repair is a metadata correction for one database, agreed with that
    database's owner, and it is not a way to make a validation error go away.
  - Never set `spring.flyway.validate-on-migrate=false` to get past a mismatch on a shared or production
    database, and do not delete rows from `flyway_schema_history`: that is how a database ends up with a
    schema nobody can explain.
- **`Schema-validation: missing column ... in table ...`.** The entities and the DDL disagree, which normally
  means a migration is missing from this database (the history stops short of the version you expect) or a
  mapping is wrong. Re-pull and restart so the pending migration applies. Do not set
  `spring.jpa.hibernate.ddl-auto` to `update` or `none` to hide it - `validate` is what keeps the `@Column`
  definitions and the schema honest.

### Back up before touching migration history

`repair` or a rebuild are only ever run on a database you can restore, so take the snapshot first:

```bash
pg_dump -h localhost -U postgres -d billgen -Fc -f billgen-before-flyway-repair.dump
pg_restore --list billgen-before-flyway-repair.dump | head      # confirm the archive is readable
```

To put it back into a fresh, empty database of the same name:

```bash
createdb -h localhost -U postgres billgen
pg_restore -h localhost -U postgres -d billgen --no-owner billgen-before-flyway-repair.dump
```

For the Compose stack, a local database that is simply unwanted can be discarded instead:
`docker compose down -v && docker compose up -d` recreates an empty schema and every migration reapplies.
That deletes all local data; keep it to development.

### PostgreSQL 18 and the Flyway version

The log line

```
Flyway upgrade recommended: PostgreSQL 18.6 is newer than this version of Flyway and support has not been tested
```

is a warning, not the cause of anything above it: Flyway has read the version, connected, and continued.

Flyway's version comes from Spring Boot's dependency management, not from this project: Boot 3.5.16 manages
Flyway **11.7.2** (see `spring-boot-project/spring-boot-dependencies/build.gradle` at that tag), and 11.7.x
predates PostgreSQL 18, which is what the message says. Current Flyway documents PostgreSQL 12 to 18 as
supported, but the support landed after the 11.x line Boot 3.5 tests against - 11.8.2 still warns on
PostgreSQL 18 - so there is no version inside Boot's managed range that silences it. The options, in the
order they should be considered:

1. **Leave it.** The warning is cosmetic for this project: the migrations are plain DDL plus guarded data
   statements, and `spring.flyway.*` sets nothing beyond locations and enabled, so none of the configuration
   API that Flyway 12+ moved behind typed extensions is in use. The database it warns about is also a
   development choice - Compose pins `postgres:16-alpine`, and the test suite runs against that image.
2. **Move to a Spring Boot patch or line that manages a newer Flyway**, which keeps Flyway, the JDBC
   integration and the auto-configuration tested together. That is the only upgrade this project should do
   for this warning; do not override `flyway.version` alone, because a Flyway major outside Boot's managed
   line is a compatibility decision for the whole stack, not for one dependency.
3. If a newer Flyway is ever adopted, re-run the whole suite - `mvn test` - and watch a migration run against
   a database that already has history, since the interesting differences are in locking and transaction
   handling rather than in parsing SQL.

For a mismatch you have to repair rather than pull past, the two values in the Flyway message are enough to
name the commit your database is at: `Applied to database` is the version you have run, `Resolved locally` is
the file in your working tree, and `git log --oneline -- src/main/resources/db/migration/V< n >__*.sql` shows
what changed between them. `FlywayMigrationFilesTest` pins the checksum of every migration that has shipped,
so the mistake of rewriting an applied file fails a test with both numbers in its message.
