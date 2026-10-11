# Architecture and data model

## Request path

```text
Browser / API client
        │
        ▼
Nginx :80 (reverse proxy; least-connections for server-rendered UI)
   ┌────┴─────────────────────┐
   │                          │
   ├── UI + static paths       └── /api/** → Spring Cloud Gateway :8081
   │                                             │
   │                                      Spring Cloud LoadBalancer
   │                                       ┌─────┴─────┐
   ▼                                       ▼           ▼
BillGen app A :8080                  BillGen app A  BillGen app B
BillGen app B :8080                       └─────┬─────┘
      │                                         │
      ├── PostgreSQL (Flyway schema, JPA) ───────┤
      └── Redis (shared sessions and caches) ────┘
```

The Compose configuration has two independently running Spring Boot application containers. Nginx sends browser-facing pages to an upstream that uses `least_conn`; API paths first pass through the Spring Cloud Gateway. The gateway's `lb://billgen` route uses Spring Cloud LoadBalancer with two statically configured service instances. Both app nodes share PostgreSQL, Redis sessions and cache entries. Only Nginx publishes a host port in the Compose stack.

## Application layers

- **Thymeleaf MVC:** responsive server-rendered dashboard, customer workspace, invoices, catalogue, admin plan tools and a customer-only invoice portal.
- **Spring Security:** BCrypt passwords, form sign-in for the UI, role-based route rules, CSRF protection for browser forms, Redis-backed sessions and stateless HTTP Basic for `/api/**`.
- **Services:** invoice totals and rounding, lifecycle transitions, customer/catalogue/plan operations and Redis cache eviction.
- **Spring Data JPA:** PostgreSQL persistence with Flyway-managed schema and Hibernate schema validation.
- **REST API:** versioned JSON DTOs under `/api/v1`; clients should call the public Nginx endpoint, not an app container directly.
- **Presentation:** hand-authored local CSS/JavaScript, mobile navigation, responsive table containers and a browser-print invoice view with Classic, Modern and Minimal styles.

## Data model

- `customers` stores bill-to identity, contact, postal address and optional GSTIN.
- `app_users` stores a BCrypt password hash and one of `ADMIN`, `FINANCE` or `CUSTOMER`; a customer login may be linked to one customer record.
- `invoices` stores a unique invoice number, customer, dates, lifecycle state, currency, chosen design and calculated amounts.
- `invoice_items` stores a snapshot of description, quantity, unit price, tax rate and computed line amounts. A later catalogue edit does not change an issued invoice.
- `catalog_products` stores reusable goods/services and their current price/tax defaults.
- `subscription_plans` stores a public plan catalogue, prices, feature text and stated usage limits.

Flyway migration `V1__create_billgen_schema.sql` creates the initial schema and invoice-number sequence. `ddl-auto=validate` makes schema drift visible at startup without letting Hibernate change production tables.

## Invoice calculations and states

Each line subtotal is quantity × unit price, rounded to two decimal places using HALF_UP. Tax is calculated per line on that rounded subtotal and rounded to two decimal places using HALF_UP. The invoice subtotal, tax and total are sums of the persisted line values.

Supported lifecycle transitions are:

```text
DRAFT → SENT → PAID
```

Invoices can be created as Draft or Sent. A Sent invoice can be recorded as Paid. Amounts and line snapshots are not edited in place after creation; create a follow-up invoice for adjustments. The due-date check requires due date ≥ issue date.

## Redis

- `active-products` and `active-plans` cache active catalogues for the configured cache TTL (180 seconds by default).
- `dashboard` caches dashboard metrics for the same TTL.
- Writes to invoices/customers/products/plans evict the affected metric/catalogue cache.
- Spring Session stores HTTP sessions in Redis under the `billgen:sessions` namespace, allowing a login to be served by either app node.

## Deliberate starter limitations

The plan pages are informational catalogue records; there is no payment checkout, plan assignment or runtime quota enforcement. The project does not send invoice email, generate PDFs on the server, reconcile payments, split GST into CGST/SGST, apply exchange rates, configure an issuer company profile, or provide tenant isolation. Invoice print can be saved as PDF through the browser. Add and review those capabilities before using this starter as a production accounting or tax-compliance system.
