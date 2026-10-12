# BillGen

BillGen is a multi-tenant billing and invoice workspace built with Java 17, Spring Boot, PostgreSQL, Redis, Spring Security and Thymeleaf. It includes tenant-isolated customer workspaces, company profiles and logo snapshots, products and categories, plan/template entitlements and quotas, server-calculated invoices and discounts, subscription expiry reminders, protected audit trails, scoped Excel exports, an authenticated REST API, a Spring Cloud API gateway, and an Nginx reverse proxy/load balancer.

> **Project status:** the application has exactly two roles: Admin (`ADMIN`) and Customer (`CUSTOMER`). Customers can activate an available plan from their portal, or an Admin can assign a plan. Activation is immediate; payment checkout, outbound email delivery and server-side PDF generation are not included. Invoice print views can be saved as PDF in the browser.

## Quick start with Docker Compose

Requirements: Docker Engine with the Compose plugin.

```bash
cp .env.example .env
# Edit .env and choose a local demo password, if desired.
docker compose up --build -d
```

Open **http://localhost:8080**. Compose starts PostgreSQL, Redis, two BillGen application instances, the Spring Cloud Gateway and Nginx. Nginx balances the web UI across both application instances; `/api/**` is sent through the API gateway, which uses Spring Cloud LoadBalancer to balance API requests across those same instances. Redis backs shared HTTP sessions and application caches.

The development seed creates these accounts when demo data is enabled:

| Role | Email | Seeded plan |
|---|---|---|
| Admin | `admin@billgen.local` | — |
| Customer | `customer@demo.local` | Starter |
| Customer | `customer2@demo.local` | Growth |

The password for all seeded demo accounts is the `DEMO_PASSWORD` value in `.env` (default in `.env.example`: `ChangeMe123!`). **Change it before sharing a development environment.** The seed also creates isolated products, categories and invoices for each account. Seeding is idempotent and only adds missing sample records. Existing `FINANCE` users are promoted to `ADMIN` by the migration so that the application has exactly two roles.

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
2. Copy `.env.example` to `.env`. Spring Boot reads that local file when run from the project directory; by default it expects PostgreSQL user `billgen` with password `billgen_dev_only`. Set `POSTGRES_PASSWORD` in `.env` to the password already assigned to your local `billgen` role, or set `SPRING_DATASOURCE_PASSWORD` in your shell to override it.
3. Activate the dev profile and run the web application:

   ```bash
   export SPRING_PROFILES_ACTIVE=dev
   mvn spring-boot:run
   ```

4. Open <http://localhost:8080>.

The development profile defaults to `jdbc:postgresql://localhost:5432/billgen`, PostgreSQL user `billgen` / password `billgen_dev_only`, Redis at `localhost:6379`, and demo seeding enabled. Override these with matching PostgreSQL role credentials if your local database was initialized differently. If startup reports SQL state `28P01`, follow the [database authentication troubleshooting steps](docs/setup.md#troubleshooting-postgresql-password-authentication) to align the app setting and role password. Flyway applies schema migrations on first startup. Hibernate validates the schema; it does not create or update tables.

To run the gateway separately, build and start the `gateway/` Maven project after starting BillGen. Set `BILLGEN_A_URI` and `BILLGEN_B_URI` to reachable application instances. For the complete reverse-proxy/load-balancer setup, use Docker Compose.

## Roles and features

- **Admin:** customer account provisioning, plan and template management, cross-account read-only data access, invoice/product/category statistics and subscription management.
- **Customer:** private workspace for its own invoices, products, categories and custom templates; can activate an available plan and generate invoices using templates in that plan or its own active custom templates.
- **Templates and plans:** Admins create templates, assign multiple templates to a plan, and control availability. A customer subscription grants template access; hiding a template or cancelling the plan immediately withdraws that access.
- **Invoices:** multiple line items, quantity, unit price, line-level tax, flat post-tax discount, due dates, draft/sent/paid/cancelled states, currency and notes. Totals and unique numbers are server-calculated; invoice allowances are concurrency-safe and snapshot company/logo/template details for history.
- **Plans and renewals:** Customers choose an available plan with immediate activation; Admin may assign plans and override limits. Active-term plan changes carry issued-invoice usage forward; a new term after expiry resets it. Drafts do not consume allowance; cancelling an issued invoice does not restore its slot.
- **Company identity and custom designs:** each Customer account is its own invoice identity, with a profile/logo saved as a snapshot on each invoice. Customers can create custom invoice templates within plan limits; reducing a limit does not disable existing templates.
- **Product catalogue:** products/services and product categories are owned by one customer account. SKUs are unique within an account; categories may have the same name across accounts.
- **Data isolation:** all customer-owned rows carry a tenant/customer id, including invoice line items (whose tenant must match their parent invoice). Customer repository queries are scoped to the session account, foreign IDs return `404`, and customer create/update/delete operations validate ownership in the backend. Admin access to customer data is read-only.
- **Admin dashboard and reports:** authorized customer-wise statistics plus monthly invoice reports for the current calendar month and previous nine months, including term allowance usage and currency-grouped financial totals.
- **Exports and audit:** scoped product, invoice and audit XLSX exports protect untrusted text against spreadsheet formulas. Audit access/exports are logged; audit rows are append-only and exclude passwords, auth secrets and payment credentials.
- **Expiry reminders:** in-app reminders are generated seven, three and one day before subscription expiry and when a plan expires; customers retain read access to historical invoices and must renew to create new billing data.
- **Caching:** Redis caches active plans and dashboard metrics (180-second default TTL); administrative and customer writes evict affected entries.
- **Sessions:** Spring Session stores web sessions in Redis so either application instance can serve a signed-in user.
- **API:** versioned JSON endpoints under `/api/v1`, protected by HTTP Basic for Admin and Customer accounts with the same tenant checks as the web UI; see [API reference](docs/api-reference.md).

## Configuration

Copy `.env.example` to `.env` for local Compose and standalone development overrides. Never put production secrets in Git. The main profiles are:

- `dev`: local-friendly defaults and optional demo data.
- `prod`: requires PostgreSQL/Redis connection settings, enables secure session cookies and disables demo seeding.

When demo seeding is disabled, create the first administrator by setting both `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`. The bootstrap account is only added if that email is not already present.

For production, replace every development password; set `APP_DEMO_SEED_ENABLED=false`; set `SPRING_PROFILES_ACTIVE=prod`; configure a strong `POSTGRES_PASSWORD` and `REDIS_PASSWORD`; set `SESSION_COOKIE_SECURE=true`; terminate TLS at a trusted ingress/load balancer; restrict access to the gateway, database and Redis; and back up PostgreSQL. See [setup and operations](docs/setup.md) and [architecture](docs/architecture.md).

## Project map

```text
src/main/java/com/ritpl/billgen/   Spring Boot app (web, API, security, services, JPA)
src/main/resources/db/migration/  Flyway schema and tenant migration
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

Testing happens in two tiers.

| Tier | Command | What it needs | What it proves |
| --- | --- | --- | --- |
| Unit | `mvn test` | nothing but the JDK | server-side invoice math (tax, flat discount, rounding), tenant-scope rules, quota and allowance policy, export windowing and formula-injection protection |
| Integration | `mvn test` with Docker running | Docker (Testcontainers starts PostgreSQL 16) | Flyway migrations apply, the JPA mapping validates against the schema, every Spring Data query parses and plans, concurrent invoice generation cannot exceed the allowance, invoice snapshots survive later edits, expiry blocks writes but not history, audit search stays inside the tenant, and expiry reminders are never duplicated |

`BillingIntegrationTest` skips itself automatically when Docker is unavailable, so `mvn test` still
passes on a machine without a container runtime. Redis is not required for the tests; sessions and
caches are switched off in the test context.

For the full multi-service stack (Nginx, gateway, two app instances, PostgreSQL, Redis) run
`docker compose up --build` and exercise the pages and API in a browser or client.

### Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request. It provisions JDK 17 (Temurin) and a PostgreSQL 16 service container, then runs:

```bash
mvn -B -ntp -DskipTests compile
mvn -B -ntp test
mvn -B -ntp -DskipTests package
```

Surefire reports are uploaded as a workflow artifact on every run, including failures, and the test
totals are published as a check-run annotation. The integration tests bring up their own throwaway
PostgreSQL container, so the workflow needs no database credentials at all. When CI fails, the
Maven diagnostics and the failing Surefire reports are posted as a comment on the open pull request.
