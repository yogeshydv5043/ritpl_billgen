# BillGen REST API v1

Base path: `/api/v1`. API endpoints use HTTP Basic and are available to authenticated `ADMIN` and `CUSTOMER` roles. Use HTTPS in production. Every request derives its tenant scope from the authenticated account; a Customer cannot select another customer ID to gain access to its records. Admins may read across all accounts, but customer-owned records are read-only to Admin.

Call the API through the public Nginx endpoint (local default `http://localhost:8080`); `/api/**` passes through the Spring Cloud Gateway and load balancer.

```bash
curl -u "customer@demo.local:${DEMO_PASSWORD}" \
  http://localhost:8080/api/v1/invoices
```

Use provisioned accounts outside local development.

## Invoices

### `GET /invoices`

Returns invoices visible to the authenticated account, newest first. Admin receives all account invoices. A Customer receives only its own. Admin may optionally filter with `customerId`; Customer can supply only its own account ID (another account returns `404`).

### `GET /invoices/{id}`

Returns invoice details, customer summary, selected template, amounts and line-item snapshots. A Customer can only load its own invoice.

### `POST /invoices`

Creates an invoice for the authenticated Customer account. Admin credentials cannot create customer-owned invoices. `customerId` is optional; if a Customer supplies it, it must equal its own account ID. The backend resolves the owner from the principal and validates the chosen template against the active purchased plan.

```json
{
  "issueDate": "2026-10-10",
  "dueDate": "2026-10-24",
  "notes": "Thank you for your business.",
  "currency": "INR",
  "templateId": 1,
  "customTemplateId": null,
  "discountAmount": 250.00,
  "initialStatus": "SENT",
  "lines": [
    {
      "description": "Product design workshop",
      "quantity": 1,
      "unitPrice": 48000.00,
      "taxRate": 18.00
    }
  ]
}
```

`initialStatus` must be `DRAFT` or `SENT`. `currency` is a 3-letter code (defaults to INR when omitted). Provide exactly one of `templateId` (an active standard template in the Customer's plan) or `customTemplateId` (an active template owned by the Customer). `discountAmount` is optional and defaults to zero; it must be non-negative, have at most two decimal places, and not exceed subtotal plus tax. Each invoice must have 1–30 lines. Quantity must be positive with up to three fractional digits; price and tax rate allow two. Tax rate is 0–100%.

The application calculates subtotal, tax, discount and total server-side; submitted totals are not accepted. Drafts do not consume invoice allowance. Creating an invoice as `SENT` consumes one slot; issuing a draft does too. Cancelling an issued invoice does not restore its slot.

### `PATCH /invoices/{id}/status`

Moves an owned invoice from `DRAFT` to `SENT`, `SENT` to `PAID`, or `DRAFT`/`SENT` to `CANCELLED`. Cancellation never restores usage for an issued invoice.

```json
{ "status": "PAID" }
```

## Invoice PDF download

`GET /portal/invoices/{id}/invoice.pdf` (owning account) and `GET /invoices/{id}/invoice.pdf` (read-only Admin view) return the invoice as a single-page PDF.

* The tenant is resolved from the session; the record is loaded through the same tenant-scoped query as the invoice screen, so another account's id returns `404` rather than `403`.
* Every value in the document - company name, contact, address, GSTIN, logo, template name/accent, line descriptions, quantities, unit prices, tax rates, subtotal, tax, discount, total and currency - is read from the invoice's stored snapshots, so a later profile, catalogue or template edit cannot change an issued document.
* Each download writes an `EXPORT` audit event with the invoice number and the total that was handed out.
* The response is `inline` with a filename derived from the server-generated invoice number and issue date, and `Cache-Control: no-store`.

## Products

### `GET /products`

Admin receives the cross-account catalogue. Customer receives its own products only.

### `POST /products`

Creates a product for the authenticated Customer. The owner is taken from the principal. A supplied `categoryId` must belong to the same account.

```json
{
  "sku": "SVC-CONSULT",
  "name": "Consulting services",
  "productType": "SERVICE",
  "categoryId": 1,
  "description": "Strategy and consulting, billed by the hour.",
  "unitPrice": 3500.00,
  "taxRate": 18.00,
  "active": true
}
```

### `PUT /products/{id}` and `DELETE /products/{id}`

Update or delete a product owned by the authenticated Customer. Admin writes are forbidden. Product IDs owned by a different customer return `404`.

## Product categories

### `GET /categories`

Admin receives categories across accounts; Customer receives its own categories only.

### `POST /categories`

Creates a category for the authenticated Customer.

```json
{ "name": "Professional services", "description": "Consulting and design" }
```

### `PUT /categories/{id}` and `DELETE /categories/{id}`

Update or delete a category owned by the authenticated Customer. On deletion, products in that category remain in the catalogue and become uncategorized. Admin writes are forbidden; another account's category ID returns `404`.

## Templates and plans

### `GET /templates`

Returns active templates. Admin receives all active templates; Customer receives only active templates included in its active purchased plan. No customer-accessible endpoint returns the full template catalogue.

### `GET /plans`

Returns active plans and their included template metadata.

## Customer subscriptions

### `GET /customers/{id}/subscription`

Returns the active subscription for the requested account. Admin may read any account. Customer may request only its own account; another account returns `404`.

### `POST /customers/{id}/subscription`

Activates an available plan. A Customer may only activate a plan on its own account; Admin may assign a plan to any customer account. Plan activation is immediate in this starter; payment checkout is not implemented.

```json
{ "planId": 2 }
```

### `DELETE /customers/{id}/subscription`

Cancels the active subscription. Template access is withdrawn immediately. Customer can cancel only its own subscription; Admin may manage any account.

### Term, usage and access policy

Customers can choose any available plan and activation is immediate; no payment gateway or checkout is required. A renewal or plan change before the current term expires carries issued-invoice usage forward into the new term. A new term after expiry resets usage. DRAFT invoices do not consume allowance; creating/issuing SENT consumes one slot; cancelling an issued invoice does not restore it. Existing custom templates remain usable if a limit is reduced below current use; only additional template creation is blocked until usage falls below the limit. When a subscription expires or is cancelled, customers can still read their historical invoices but cannot create or issue new invoices until a plan is active again. In-app notices are generated seven, three and one day before expiry and after expiry.

## Company profile and custom templates

### `GET /company-profile` and `PUT /company-profile`

Read or update the authenticated Customer's invoice company identity. The API accepts company/contact, email, address, country and GSTIN fields; logo upload/clear is available in the portal. Each invoice stores a company/logo snapshot, so later profile changes affect new invoices only.

### `GET /custom-templates`, `POST /custom-templates`, `PUT /custom-templates/{id}`, `DELETE /custom-templates/{id}`

Customers may list and manage only their own custom invoice templates. Create/update fields are `name`, `styleKey` (`CLASSIC`, `MODERN` or `MINIMAL`), optional hex `accentColor`, and `active`. Creation is limited by the active plan; a used template is archived rather than removed so historical invoices remain stable. Admin has read-only access to customer-owned templates.

## Expiry notifications

### `GET /notifications`

Returns the signed-in Customer's recent in-app subscription notifications.

### `POST /notifications/{id}/read`

Marks one of the Customer's own notifications as read; other tenants' notification IDs return `404`.

## Customer accounts and statistics

### `GET /customers`

Admin receives all visible customer accounts. Customer receives only its own account.

### `GET /customers/{id}`

Returns account details only if the caller is an Admin or the account's own Customer.

### `GET /customers/{id}/stats`

Returns invoice, product and category counts, total billed, login details and subscription/plan details for one account. Customer may retrieve only its own statistics.

### `GET /account-stats`

Admin receives customer-wise statistics for every visible account. Customer receives only its own row.

## Site configuration (App Information)

These routes sit outside `/api/v1`: the administrator's half under `/api/admin/app-information`, the
public half under `/api/public`. The administrator's routes use the same HTTP Basic credential as the rest
of the API and refuse a customer's credential with `403` - these rows describe the product itself, so they
are not scoped to a tenant. `GET /api/public/app-information` needs no credential at all and returns only
what a public page already shows.

### `GET` and `PUT /api/admin/app-information/app-info`

Branding, support contacts, social links and footer text - one row for the whole application. `PUT`
accepts the same fields; a colour must be a `#rrggbb` hex value and a link must start with `http://` or
`https://`. Reading it before anything has been saved returns the branding the build ships with, marked
`"configured": false`.

### `/api/admin/app-information/policies[/{type}]`

`GET` the list or one document, `PUT` to save it, `PUT /{type}/status` with `{"status":"ACTIVE"}` to
publish or `"INACTIVE"` to withdraw. `{type}` is `REFUND`, `PRIVACY` or `TERMS_CONDITIONS`; each type has
exactly one row, revised in place rather than versioned. `pageContent` is HTML: it is reduced to the tags a
policy is written with (`p`, `br`, `ul`, `ol`, `li`, `strong`, `b`, `em`, `i`, `h2`-`h4`, `blockquote` and
`a` with an `http(s)`, `mailto` or in-document `href`); every other tag and attribute is removed on the way
in, so what the API stored is what a visitor's browser may render.

### `/api/admin/app-information/reviews[/{id}]`

`GET` (paged, `search`, `status`, `minRating`), `POST`, `PUT`, `DELETE`, plus
`PUT /{id}/status` and `POST /{id}/restore`. `rating` is an integer 1-5. `DELETE` removes a review from the
public page and the administrator's list without erasing the row, which is why the list can show what was
published before it was taken down.

### `/api/admin/app-information/faq-types[/{id}]` and `/faqs[/{id}]`

FAQ types carry a unique lower-case `typeCode` used by public links; leave it blank on `POST` and it is
derived from the name. A type with questions in it cannot be deleted - the response says how many are still
inside. FAQs are `PUT` with their `faqTypeId`, and a question only appears publicly while both it and its
type are published.

### `/api/admin/app-information/email-settings`

`GET` returns the transport without the password: `passwordSet`, a `passwordHint`, and `transportSummary`
saying whether the application will actually send. `PUT` accepts `password`; a blank or absent value keeps
the stored credential, and the stored value is encrypted with `APP_SECRETS_ENCRYPTION_KEY`, so a
deployment without that key is told it cannot save one rather than having it written in plain text.
`POST /email-settings/test` with `{"recipient":"you@example.com"}` sends one real message and returns
`{"sent":true|false,"message":"..."}` - the message explains a refusal in a sentence, never as a stack
trace, and never repeats a credential.

### `/api/admin/app-information/notification-settings[/{code}]`

`GET` lists the notices the platform can produce (the codes are fixed by the application), `PUT /{code}`
edits name, description, `triggerDaysBefore`, `repeatIntervalDays`, the two channels and the status, and
`PUT /{code}/status` switches one off without losing its configuration. `triggerDaysBefore: 0` means "when
it happens"; `repeatIntervalDays: 0` means "once while the condition lasts". An unknown code is a `400`,
not a new row.

## Errors

- `400 Bad Request`: invalid data, an unavailable template, or a template not included in the active plan.
- `401 Unauthorized`: missing or invalid credentials.
- `403 Forbidden`: authenticated role is not permitted to perform an action (e.g. Admin attempting to write a customer-owned product).
- `404 Not Found`: missing resource or a record owned by a different Customer account.
- `409 Conflict`: a database uniqueness/constraint conflict.
