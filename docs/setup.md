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
| `POSTGRES_PASSWORD` | local-only `billgen_dev_only` | PostgreSQL password; use a unique secret outside local development. |
| `REDIS_PASSWORD` | blank locally | Redis password. If non-empty in Compose, Redis starts with authentication enabled. |
| `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` | `redis` / `6379` in Compose | Redis endpoint for cache and Spring Session. |
| `APP_DEMO_SEED_ENABLED` | `true` in the local Compose stack | Seed example accounts, customers, invoices, products and plans. Set false outside local development. |
| `DEMO_PASSWORD` | local-only `ChangeMe123!` | Shared password for the three seeded demo accounts. |
| `BOOTSTRAP_ADMIN_EMAIL` | blank | Optional first Admin when demo data is disabled. |
| `BOOTSTRAP_ADMIN_PASSWORD` | blank | Password for the bootstrap Admin; set with the email. |
| `SESSION_COOKIE_SECURE` | `false` in dev | Mark session cookies Secure. Set true when serving over HTTPS. |
| `DB_POOL_SIZE` | `6` in Compose | Per-instance Hikari maximum pool size. |

The complete local example is [.env.example](../.env.example). A real `.env` is git-ignored and excluded from the Docker build context and project ZIP.

### Production checklist

- Use `SPRING_PROFILES_ACTIVE=prod` and `APP_DEMO_SEED_ENABLED=false`.
- Set strong, unique PostgreSQL and Redis passwords; configure secrets through the deployment platform, not a committed file.
- Set both `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` for initial provisioning; rotate/remove the bootstrap secret after use.
- Set `SESSION_COOKIE_SECURE=true`; expose BillGen only over HTTPS through a trusted reverse proxy.
- Keep PostgreSQL, Redis, both application instances and the gateway on a private network. Only the ingress/Nginx entry point should be public.
- Restrict actuator access, configure logs/metrics and backups, and review retention/data-protection requirements before handling customer data.
- Supply the gateway with the correct `BILLGEN_A_URI` and `BILLGEN_B_URI` targets for the two app instances.

The bundled Nginx config listens on plain HTTP inside the Compose network; it is a local reverse proxy, **not** a TLS terminator. Put a managed HTTPS ingress/load balancer in front for production and ensure the forwarded scheme/host headers come only from trusted infrastructure.

## Running just the web app

Start PostgreSQL and Redis locally, then export the desired variables and run:

```bash
export SPRING_PROFILES_ACTIVE=dev
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/billgen
export SPRING_DATASOURCE_USERNAME=billgen
export SPRING_DATASOURCE_PASSWORD=billgen
export SPRING_DATA_REDIS_HOST=localhost
export SPRING_DATA_REDIS_PORT=6379
mvn spring-boot:run
```

For `prod`, the required datasource and Redis settings have no local fallbacks. Run `mvn test` for unit tests, or `mvn package` to build the executable `target/billgen.jar`.
