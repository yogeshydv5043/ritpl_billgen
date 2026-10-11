# BillGen

BillGen is a responsive billing and invoice workspace built with Java 17, Spring Boot, PostgreSQL, Redis, Spring Security and Thymeleaf. It includes invoice creation and print-ready templates, customer records, a product/service catalogue, subscription-plan pages, an authenticated REST API, a Spring Cloud API gateway, and an Nginx reverse proxy/load balancer.

> **Project status:** this repository was reconstructed as a complete starter application. The billing workflow, catalogue, plans, demo data, API, gateway, Docker Compose stack and Nginx configuration are included. Subscription checkout/entitlement enforcement, outbound email delivery, and server-side PDF generation are intentionally not included.

## Quick start with Docker Compose

Requirements: Docker Engine with the Compose plugin.

```bash
cp .env.example .env
# Edit .env and choose a local demo password, if desired.
docker compose up --build -d
```

Open **http://localhost:8080**. Compose starts PostgreSQL, Redis, two BillGen application instances, the Spring Cloud Gateway and Nginx. Nginx balances the web UI across both application instances; `/api/**` is sent through the API gateway, which uses Spring Cloud LoadBalancer to balance API requests across those same instances. Redis backs shared HTTP sessions and application caches.

The development seed creates these accounts when demo data is enabled:

| Role | Email |
|---|---|
| Administrator | `admin@billgen.local` |
| Finance | `finance@billgen.local` |
| Customer portal | `customer@demo.local` |

The password for all seeded demo accounts is the `DEMO_PASSWORD` value in `.env` (default in `.env.example`: `ChangeMe123!`). **Change it before sharing a development environment.** The seed also creates sample customers, invoices, products/services and plans. Seeding is idempotent and only adds missing sample records.

Useful commands:

```bash
docker compose ps
docker compose logs -f billgen-a
docker compose logs -f gateway
docker compose down
```

`docker compose down -v` also deletes the local PostgreSQL and Redis data volumes. Do not run that unless you intend to reset the local data.

## Run the application without Docker

Requirements: JDK 17+, Maven 3.9+, PostgreSQL and Redis.

1. Create a PostgreSQL database/user and start Redis.
2. Set `SPRING_PROFILES_ACTIVE=dev`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and (if needed) `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT`.
3. Run the web application:

   ```bash
   mvn spring-boot:run
   ```

4. Open <http://localhost:8080>.

The development profile defaults to `jdbc:postgresql://localhost:5432/billgen`, user/password `billgen`, Redis at `localhost:6379`, and demo seeding enabled. Flyway creates the schema on first startup. Hibernate validates the schema; it does not create or update tables.

To run the gateway separately, build and start the `gateway/` Maven project after starting BillGen. Set `BILLGEN_A_URI` and `BILLGEN_B_URI` to reachable application instances. For the complete reverse-proxy/load-balancer setup, use Docker Compose.

## Roles and features

- **Admin:** dashboard, customers, invoices, products/services, subscription-plan administration.
- **Finance:** dashboard, customers, invoices, products/services.
- **Customer:** a read-only portal for their own invoices and print view.
- **Invoices:** multiple line items, quantity, unit price, line-level tax, due dates, draft/sent/paid states, customer notes, currency code and three designs: Classic, Modern and Minimal. Browser print can save the print view as PDF.
- **Catalogue:** reusable product and service items, tax rate, category, active/hidden state.
- **Plans:** public pricing page and admin-managed plan catalogue.
- **Caching:** Redis caches active catalogue items and plans plus dashboard metrics (180-second default TTL); writes evict related caches.
- **Sessions:** Spring Session stores web sessions in Redis so either application instance can serve a signed-in user.
- **API:** versioned JSON endpoints under `/api/v1`, protected by HTTP Basic for Admin/Finance; see [API reference](docs/api-reference.md).

## Configuration

Copy `.env.example` to `.env` for local Compose overrides. Never put production secrets in Git. The main profiles are:

- `dev`: local-friendly defaults and optional demo data.
- `prod`: requires PostgreSQL/Redis connection settings, enables secure session cookies and disables demo seeding.

When demo seeding is disabled, create the first administrator by setting both `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`. The bootstrap account is only added if that email is not already present.

For production, replace every development password; set `APP_DEMO_SEED_ENABLED=false`; set `SPRING_PROFILES_ACTIVE=prod`; configure a strong `POSTGRES_PASSWORD` and `REDIS_PASSWORD`; set `SESSION_COOKIE_SECURE=true`; terminate TLS at a trusted ingress/load balancer; restrict access to the gateway, database and Redis; and back up PostgreSQL. See [setup and operations](docs/setup.md) and [architecture](docs/architecture.md).

## Project map

```text
src/main/java/com/ritpl/billgen/   Spring Boot app (web, API, security, services, JPA)
src/main/resources/db/migration/  Flyway schema
src/main/resources/templates/     Responsive Thymeleaf pages and print view
src/main/resources/static/         Local CSS and JavaScript
src/test/                          Unit tests
 gateway/                          Spring Cloud Gateway application
 nginx/nginx.conf                  Nginx reverse proxy and web load balancing
 Dockerfile                        Multi-stage app image
 compose.yaml                      Local multi-service stack
```

## Quality checks

```bash
mvn test
mvn package
```

The invoice calculator has unit tests. A live integration check also needs PostgreSQL, Redis and Docker; run `docker compose up --build` and test the pages/API in a browser/client.
