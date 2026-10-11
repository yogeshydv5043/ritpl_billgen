# BillGen REST API v1

Base path: `/api/v1`. JSON endpoints are protected with HTTP Basic authentication for `ADMIN` and `FINANCE` users. Call them through the Nginx entry point (local default `http://localhost:8080`); `/api/**` passes through the Spring Cloud Gateway and load balancer. Use HTTPS in production. Customer-role credentials cannot call the management API.

```bash
curl -u "finance@billgen.local:${DEMO_PASSWORD}" \
  http://localhost:8080/api/v1/invoices
```

Replace the demo account/password with provisioned credentials outside local development.

## Invoices

### `GET /invoices`

Returns the invoice list, newest first. Optional query parameter: `customerId`.

```http
GET /api/v1/invoices?customerId=1
```

### `GET /invoices/{id}`

Returns invoice details, customer summary, amounts and line-item snapshots.

### `POST /invoices`

Creates an invoice and returns `201 Created` with a `Location` header and the new resource.

```json
{
  "customerId": 1,
  "issueDate": "2026-10-10",
  "dueDate": "2026-10-24",
  "notes": "Thank you for your business.",
  "currency": "INR",
  "templateCode": "CLASSIC",
  "initialStatus": "SENT",
  "lines": [
    {
      "description": "Product design workshop",
      "quantity": 1,
      "unitPrice": 48000.00,
      "taxRate": 18.00
    },
    {
      "description": "Research and discovery",
      "quantity": 8,
      "unitPrice": 3500.00,
      "taxRate": 18.00
    }
  ]
}
```

`initialStatus` is `DRAFT` or `SENT`. `currency` is a 3-letter code (defaults to INR when omitted). Supported `templateCode` values are `CLASSIC`, `MODERN` and `MINIMAL`. Each invoice must have 1–30 lines. Quantity must be positive with up to three fractional digits; price and tax rate allow two. Tax rate is 0–100%.

The application calculates subtotal, tax and total server-side; submitted totals are not accepted.

### `PATCH /invoices/{id}/status`

Moves an invoice one step forward. Valid transitions: `DRAFT` → `SENT`, `SENT` → `PAID`.

```json
{ "status": "PAID" }
```

A repeated request for the invoice's current state is idempotent. Reverse transitions and skipped states return `400 Bad Request`.

## Customers

- `GET /customers` — returns the customer directory.
- `GET /customers/{id}` — returns one customer's contact and billing details.

Customer records are managed through the web application in this starter.

## Catalogue and plans

- `GET /products` — active products and services, including current price and tax defaults.
- `GET /plans` — active subscription plan catalogue.

The separate public pricing page is available as HTML at `/plans` without signing in.

## Response and errors

Successful responses use JSON (`application/json`). Validation/business errors use RFC 9457-style Problem Details (`application/problem+json`) with an HTTP status, title and detail, for example:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "The due date must be on or after the issue date."
}
```

Common statuses: `200` success, `201` created, `400` invalid request or transition, `401` missing/invalid credentials, `403` insufficient role, `404` unknown resource, `409` uniqueness/reference conflict.
