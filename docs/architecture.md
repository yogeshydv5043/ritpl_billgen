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
- **Presentation:** local CSS/JavaScript, responsive customer/admin views, a browser-print invoice view with Classic, Modern and Minimal render layouts, and a server-side PDF renderer (OpenPDF) for the downloadable invoice.

## Roles and authorization

The application has exactly two roles:

- `ADMIN`: may manage customer accounts, plans and templates; may read records across all customer accounts; has read-only access to customer-owned invoices, products and categories.
- `CUSTOMER`: a login linked to exactly one customer account. The customer can access and modify only its own account's invoices, products, categories and custom templates, and can create invoices with templates bundled in its active plan or its own active custom templates.

The `TenantScope` is built server-side from the authenticated principal. A customer ID in a URL, form or API body never widens that scope. Cross-tenant record IDs are returned as `404` to avoid exposing whether another tenant's record exists. API and browser services enforce the same scope, so UI hiding is not the security boundary.

## Data model

- `customers` represents customer account/tenant identity and billing profile. `system_account` marks a hidden internal migration account used only to hold catalogue records that predated tenant ownership.
- `app_users` stores a BCrypt password hash and one of `ADMIN` or `CUSTOMER`; a Customer login is linked to one customer account.
- `subscription_plans` stores the plan catalogue, prices and feature text.
- `bill_templates` stores admin-managed template identity, active availability and one of the print renderer styles.
- `plan_templates` is a many-to-many relation: a plan may include multiple templates, and a template may be part of multiple plans.
- `customer_subscriptions` stores customer plan activations, status, term dates, limit overrides and invoice-usage carry-forward. There is at most one active subscription per account.
- `product_categories` belongs to one customer account; `(customer_id, name)` is unique.
- `catalog_products` belongs to one customer account and optionally references a category owned by that same account. SKU uniqueness is scoped to `(customer_id, sku)`.
- `custom_invoice_templates` stores customer-owned designs within the active plan's custom-template limit.
- `invoices` belongs to the customer account that generated it and stores line totals, flat discount, status, allowance attribution, company/logo snapshots and template snapshots. The service validates template entitlement and computes financial values on create.
- `invoice_items` stores a snapshot of description, quantity, unit price, tax rate and computed line amounts, and carries the customer id directly. A composite foreign key ensures each line's tenant matches its parent invoice. Later company-profile or catalogue edits do not change an existing invoice.
- `audit_records` is append-only, indexed by tenant and timestamp, and protected from database updates/deletes by a trigger. Notification logs track in-app expiry reminders and read status.

Flyway migration `V1__create_billgen_schema.sql` creates the original schema. `V2__plans_templates_and_tenant_isolation.sql` migrates roles, templates, plans, subscriptions and customer-owned catalogues. `V3` adds usage-limit overrides, audit records and expiry-notification logs; `V4` records invoice allowance attribution and the ten-calendar-month report window; `V5` adds company/logo and template snapshots; `V6` adds tenant-owned custom templates. Existing Finance accounts are promoted to Admin. Any old globally shared catalogue items are moved to a hidden internal account rather than assigned to an arbitrary customer. `V7` adds account status, soft deletion and plan entitlements; `V8` audit types, template versions and notification management; `V9` background jobs and audit retention; `V10` the tenant settings row and invoice presentation snapshots; `V11` the account alert inbox; `V12` the seven App Information tables (`app_info`, `policy_pages`, `reviews`, `faq_types`, `faqs`, `email_settings`, `notification_settings`); `V13` prunes the notice codes that have no producer.

**Migrations are append-only.** Flyway records a checksum for every applied version and refuses to start when the file behind an applied version changes, so a corrected schema is a new version rather than an edit to an old one. Rewriting an applied migration does not fix the databases that already ran it - it breaks them, with a startup error about `jpaSharedEM_entityManagerFactory` that names nothing near its cause, and a test-only database that starts from empty will never show it. `FlywayMigrationFilesTest` pins the checksum of every migration that has shipped so the rule is enforced by the build rather than remembered; `repair` is a metadata correction for one database, not a fix, and it never re-runs the SQL that was skipped. See *Setup and operations* for the recovery steps.

## Template entitlement enforcement

An Admin creates templates, assigns one or more active templates to a plan, and controls plan/template availability. A customer chooses an active plan in its portal, or an Admin assigns one. Template access is computed from the current active subscription and that plan's active templates. The same check is enforced in the invoice service for both MVC and API requests, not just in the template selector. A customer cannot forge a template ID or submit another customer's account ID to create an invoice.

Plan selection/renewal and subscription assignment activate immediately; there is no checkout or payment gateway. Deactivating a plan or bundled standard template removes it from customer selection and from new assignment. A term already running is grandfathered: entitlement is decided by the subscription's own dates and status, so taking a plan off the market neither cancels the subscription nor blocks invoices during the paid-for term (documented decision - the specification forbids a deactivation silently terminating existing subscriptions). A deactivated or soft-deleted plan is refused only where it is offered: at activation. Customers can also create plan-limited custom templates. Existing invoices keep company/logo and template presentation snapshots so profile/template edits do not alter historical print views or downloads. `InvoicePdfService` renders only those snapshot columns, so `GET /portal/invoices/{id}/invoice.pdf` (owner) and `GET /invoices/{id}/invoice.pdf` (read-only Admin) reproduce the document as issued; each download is recorded as an `EXPORT` audit event.

## Tenant isolation

- Invoices, products and categories have a required owner/customer foreign key.
- Customer list queries filter by the current principal's customer ID; admin reads use explicit cross-account queries.
- Single-record customer reads use an owner-scoped repository query or compare the owner with the session scope before returning data.
- Creates take the customer ID from the authenticated scope; any request-supplied customer ID is either ignored as an owner or rejected if it names another account.
- Updates, status transitions, deletes and category assignment validate that the target belongs to the authenticated customer. Categories may only be attached to products of the same customer.
- Admin writes to customer-owned records are blocked; Admin plan/template/account management is separate from those tenant write paths.

## Invoice calculations, quota and lifecycle

Each line subtotal is quantity × unit price, rounded to two decimal places using HALF_UP. Tax is calculated per line on that rounded subtotal and rounded to two decimal places using HALF_UP. The invoice subtotal and tax are sums of persisted line values. A flat, non-negative discount (maximum two decimal places) is subtracted after all line taxes and cannot exceed the pre-discount total. The server calculates all values; submitted totals are never trusted.

Supported lifecycle transitions are:

```text
DRAFT → SENT → PAID
  └──────→ CANCELLED
SENT ────→ CANCELLED
```

Drafts do not consume invoice allowance. Creating an invoice as Sent or issuing a Draft consumes one slot for the current subscription term. Marking it Paid does not add another slot, and cancelling an issued invoice never restores its slot; cancelling a Draft consumes none. A term change while the current term is unexpired carries issued-invoice usage into the new term. A new term after expiry starts at zero. Subscription and usage rows are locked during quota-sensitive writes so simultaneous invoice issues cannot overspend the allowance.

A customer may choose an available plan without checkout; an Admin may assign or override limits. Plan changes take effect immediately. Cancelling a subscription withdraws access to new invoices and plan templates; expired/cancelled subscriptions retain read access to historical invoices. A customer must renew or select an available plan to create new billing data. Expiry reminders are generated in-app seven, three and one day before expiry, plus an expired notice; reminders are deduplicated per subscription and offset. Product and custom-template limits are enforced when adding items/templates; reducing a limit does not invalidate existing custom templates, but blocks further creation until usage falls below the limit.

Amounts and line snapshots are not edited in place after creation; create a follow-up invoice for adjustments. The due-date check requires due date ≥ issue date.

## Audit trail and exports

Successful account, company-profile, product/category, invoice, subscription, plan/template, notification and export mutations are recorded with actor, outcome, tenant (when applicable), timestamp and allow-listed before/after summaries. Failed invoice create/issue and export attempts are recorded without persisting exception messages or credentials. Audit detail/list access and audit exports are themselves logged. Customers can only view/export their own tenant's audit records; Admin audit access is role-protected. The application exposes no audit edit/delete routes, and a PostgreSQL trigger rejects direct row updates/deletes. Audit payloads omit passwords, authentication tokens/secrets and payment credentials.

Product, invoice-report and audit XLSX exports reuse the corresponding tenant/admin authorization scope. The invoice report covers the current calendar month plus the previous nine, using the configured subscription timezone; it separates term allowance usage from monthly generation counts and groups financial totals by currency. Untrusted values are emitted as explicit text cells and formula-like prefixes are escaped to reduce spreadsheet formula-injection risk.

## Redis

- `active-plans` caches the active public plan catalogue.
- `dashboard` caches Admin dashboard metrics for the configured TTL (180 seconds by default).
- Writes to invoices, customer accounts, products, categories, plans or templates evict related caches.
- Spring Session stores HTTP sessions in Redis under the `billgen:sessions` namespace, allowing a login to be served by either app node.

## Customer panel

The customer workspace (`/portal/**`) is the same service layer as the API and the Admin screens, so every
rule below is enforced server-side and none of it depends on what a page hides.

- **Profile and security.** The company profile (`/portal/company`) is the invoice identity: name, contact,
  email, phone, address, GSTIN and logo. It is snapshotted onto each invoice at issue time, so a later edit
  cannot rewrite a delivered document. Sign-in email is not customer-editable (it is the account's external
  key and is changed by an administrator). `/portal/account` handles the customer's own password change,
  which re-verifies the current password, enforces the same length policy as registration, and never writes
  either password anywhere.
- **Settings (`CustomerSettings`).** One row per account, read as transient defaults when none exists yet:
  default plan/custom template, currency, invoice-number prefix, due-days, optional default tax rate, payment
  terms, notes, footer, product unit and category, list page size, audit viewing period, four alert switches
  and a default export range. A chosen default template must be one the plan currently grants (checked on
  save, not merely in the select box). `CustomerSettingsService.pageSize/auditWindowStart/wantsAlert` are what
  the list pages, the audit trail, the exports and the notification generators consult.
- **Audit viewing window vs retention.** `AuditHistoryPeriod` (15 days, 1, 3, 6 or 12 months) bounds what the
  account may list and export; the portal clamps the query's `from` date to that window, and the dashboard's
  recent-activity list uses the same bound. This is a *viewing* period, deliberately not a deletion rule:
  shortening it hides rows from the account and deletes nothing. Actual retention stays where it was - the
  per-audit-type retention days applied by the administrative retention job, which a customer cannot reach.
- **Plans.** A customer buys the plan's own billing period, or an explicit monthly/yearly term; the dates are
  derived from the plan (`usageWindow()`) rather than from the page. The allowance a term grants is the plan's
  per-period figure multiplied by the number of periods the term covers, so a twelve-month purchase of a
  monthly plan is twelve periods, not one - an administrator's override always wins. Renewal, upgrade and
  downgrade rules are enforced in `SubscriptionService.activate`: retired and inactive plans are refused, a
  mid-term change requires the current plan to allow it, a mid-term downgrade additionally requires the target
  plan to allow downgrades, usage issued inside an unexpired term carries forward, and a new term after expiry
  starts clean. No renewal is automatic: this workspace takes no payment.
- **Templates.** Only plan-granted system templates plus the account's own active custom designs are usable.
  Customisation (accent, font, layout density, logo, bank details, payment terms, footer, signature caption
  and name) is stored on the account's row, capped by the plan's custom-template limit, and applied through
  `template_*_snapshot` columns copied at issue time. A system template is never updated by a customer
  action. Resetting clears only the account's own presentation. A template used by any invoice is hidden rather
  than deleted so issued documents keep their layout.
- **Catalogue.** Products and categories support search, filter, whitelisted sort, database paging sized by
  the account's own setting, activate/hide, delete, and an Excel export that honours the same filter as the
  page. Products are validated against the plan's product limit. Deleting a product is safe by construction:
  invoice lines keep their own description, price and tax, so no issued document changes. Deleting a category
  unassigns its products instead of removing them.
- **Invoices.** `/portal/invoices` searches, filters (status, overdue, issue-date range), sorts (whitelist)
  and pages, and its preview panel runs the identical `InvoiceCalculator` path with no writes. A new invoice
  may start as `DRAFT` or `SENT`; a draft is edited in place (lines replaced, totals recomputed) and only a
  draft may be edited. The allowance is checked when an invoice is issued and deducted on success in the same
  transaction; a draft consumes nothing, and a cancelled invoice does not return its slot - which is what
  keeps a period's count honest. Repeated submissions are refused by a one-time form token
  (`FormTokenGuard`) rather than by a disabled button, and the message points the customer at their invoice
  list instead of creating a second document.
- **Dashboard.** `CustomerDashboardService` answers every card and list with an aggregate or a bounded query
  over that account's rows - template counts, custom-design slots, remaining allowance, invoices total and
  this month, products total/active/hidden, categories, subscription state with days to expiry - plus the last
  ten invoices, ten product changes, five category changes and ten audit rows. It is computed live; if a
  figure cannot be read the page says so instead of showing a placeholder.
- **Alerts.** `CustomerAlertService` writes the account's inbox entries for allowance warnings (deduplicated
  per remaining level, using the plan's warning percentage), recorded payments and credential changes. A
  switched-off topic writes nothing, except security notices, which an account cannot suppress on itself.
  Expiry reminders remain in `SubscriptionExpiryNotificationService`, which honours the same preference.
- **Multi-tenant rules.** Every portal read and write builds a `TenantScope` from the principal; a foreign id
  is a `404`. Export routes reuse the listing queries, so they cannot see more than the UI can. Audit rows
  stay append-only at the database level, and no portal route edits or deletes one.

## Deliberate starter limitations

The Customer account's company profile (including logo) is the invoice identity; there is no separate client list. The app immediately activates customer-selected plans but does not collect or reconcile payments. It does not send invoice email, split GST into CGST/SGST, apply exchange rates, or implement tax compliance. Invoice PDFs are generated from stored snapshots and the print view can also be saved as a PDF by the browser. Review tax/legal requirements before production accounting use.
