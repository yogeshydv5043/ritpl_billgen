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
      ├── PostgreSQL (Flyway schema, JPA) ──────┤
      └── Redis (shared sessions and caches) ───┘
```

The Compose configuration has two independently running Spring Boot application containers. Nginx sends browser-facing pages to an upstream that uses `least_conn`; API paths first pass through the Spring Cloud Gateway. Both app nodes share PostgreSQL and Redis sessions/cache entries. Only Nginx publishes a host port.

## Application layers

- **Thymeleaf MVC:** role-specific Admin and Customer workspaces, customer account management, plans/templates, invoices and customer-owned catalogue screens.
- **Spring Security:** BCrypt passwords, form sign-in for the UI, HTTP Basic for `/api/**`, role-based route rules, CSRF protection for browser forms, Redis-backed sessions and stateless API requests.
- **Services:** tenant scoping and ownership checks, invoice totals and lifecycle transitions, plan purchases and template entitlements, customer-owned product/category management.
- **Spring Data JPA:** PostgreSQL persistence with Flyway-managed schema and Hibernate schema validation.
- **REST API:** versioned JSON DTOs under `/api/v1`; endpoints derive tenant identity from the authenticated principal rather than trusting customer IDs supplied by the caller.
- **Presentation:** local CSS/JavaScript, responsive customer/admin views and a browser-print invoice view with Classic, Modern and Minimal render layouts.

## Roles and authorization

The application has exactly two roles:

- `ADMIN`: may manage customer accounts, plans and templates; may read records across all customer accounts; has read-only access to customer-owned invoices, products and categories.
- `CUSTOMER`: a login linked to exactly one customer account. The customer can access and modify only its own account's invoices, products and categories, and can use only templates in its active purchased plan.

The `TenantScope` is built server-side from the authenticated principal. A customer ID in a URL, form or API body never widens that scope. Cross-tenant record IDs are returned as `404` to avoid exposing whether another tenant's record exists. API and browser services enforce the same scope, so UI hiding is not the security boundary.

## Data model

- `customers` represents customer account/tenant identity and billing profile. `system_account` marks a hidden internal migration account used only to hold catalogue records that predated tenant ownership.
- `app_users` stores a BCrypt password hash and one of `ADMIN` or `CUSTOMER`; a Customer login is linked to one customer account.
- `subscription_plans` stores the plan catalogue, prices and feature text.
- `bill_templates` stores admin-managed template identity, active availability and one of the print renderer styles.
- `plan_templates` is a many-to-many relation: a plan may include multiple templates, and a template may be part of multiple plans.
- `customer_subscriptions` stores customer plan activations, status and dates. There is at most one active subscription per account.
- `product_categories` belongs to one customer account; `(customer_id, name)` is unique.
- `catalog_products` belongs to one customer account and optionally references a category owned by that same account. SKU uniqueness is scoped to `(customer_id, sku)`.
- `invoices` belongs to the customer account that generated it and references its selected template. The service validates template entitlement on create.
- `invoice_items` stores a snapshot of description, quantity, unit price, tax rate and computed line amounts, and carries the customer id directly. A composite foreign key ensures each line's tenant matches its parent invoice. A later catalogue edit does not change an issued invoice.

Flyway migration `V1__create_billgen_schema.sql` creates the original schema. `V2__plans_templates_and_tenant_isolation.sql` migrates roles, templates, plans, subscriptions and customer-owned catalogues. Existing Finance accounts are promoted to Admin. Any old globally shared catalogue items are moved to a hidden internal account rather than assigned to an arbitrary customer.

## Template entitlement enforcement

An Admin creates templates, assigns one or more active templates to a plan, and controls plan/template availability. A customer chooses an active plan in its portal, or an Admin assigns one. Template access is computed from the current active subscription and that plan's active templates. The same check is enforced in the invoice service for both MVC and API requests, not just in the template selector. A customer cannot forge a template ID or submit another customer's account ID to create an invoice.

Template activation and subscription activation happen immediately in this starter; payment checkout is intentionally not implemented. Deactivating a plan or template removes it from customer selection and invoice creation. Existing invoices retain a reference to the template they were created with so print views continue to work.

## Tenant isolation

- Invoices, products and categories have a required owner/customer foreign key.
- Customer list queries filter by the current principal's customer ID; admin reads use explicit cross-account queries.
- Single-record customer reads use an owner-scoped repository query or compare the owner with the session scope before returning data.
- Creates take the customer ID from the authenticated scope; any request-supplied customer ID is either ignored as an owner or rejected if it names another account.
- Updates, status transitions, deletes and category assignment validate that the target belongs to the authenticated customer. Categories may only be attached to products of the same customer.
- Admin writes to customer-owned records are blocked; Admin plan/template/account management is separate from those tenant write paths.

## Invoice calculations and states

Each line subtotal is quantity × unit price, rounded to two decimal places using HALF_UP. Tax is calculated per line on that rounded subtotal and rounded to two decimal places using HALF_UP. The invoice subtotal, tax and total are sums of the persisted line values.

Supported lifecycle transitions are:

```text
DRAFT → SENT → PAID
```

A new invoice can start as Draft or Sent. A Sent invoice can be recorded as Paid. Amounts and line snapshots are not edited in place after creation; create a follow-up invoice for adjustments. The due-date check requires due date ≥ issue date.

## Redis

- `active-plans` caches the active public plan catalogue.
- `dashboard` caches Admin dashboard metrics for the configured TTL (180 seconds by default).
- Writes to invoices, customer accounts, products, categories, plans or templates evict related caches.
- Spring Session stores HTTP sessions in Redis under the `billgen:sessions` namespace, allowing a login to be served by either app node.

## Deliberate starter limitations

The app immediately activates customer-selected plans but does not collect or reconcile payments. It does not send invoice email, generate PDFs on the server, split GST into CGST/SGST, apply exchange rates, configure an issuer-company profile, or implement tax compliance. Invoice print can be saved as PDF through the browser. Review the application and add payment/tax controls before production accounting use.
