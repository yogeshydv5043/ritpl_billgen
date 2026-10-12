package com.ritpl.billgen.security;

import com.ritpl.billgen.service.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;

/**
 * The data-isolation boundary of the application.
 *
 * <p>Every service method that reads or writes customer-owned data takes a {@code TenantScope} and
 * must use it to build its query or to validate the owner of the record it loaded. A scope is
 * either:</p>
 *
 * <ul>
 *   <li><b>admin</b> - authorized to read every tenant record (never to write them), or</li>
 *   <li><b>customer</b> - pinned to exactly one customer id and only that customer's rows.</li>
 * </ul>
 *
 * <p>Scoping happens on the server: the controllers derive the scope from the authenticated
 * principal, and a customer can never widen it, because the customer id comes from the session
 * rather than from the request.</p>
 */
public record TenantScope(Long customerId, boolean admin) {

    /** Scope of a signed-in administrator: may read every tenant record. */
    public static TenantScope administrator() {
        return new TenantScope(null, true);
    }

    /** Scope acting as a single customer account. */
    public static TenantScope ofCustomer(long customerId) {
        if (customerId <= 0) throw new IllegalArgumentException("Invalid customer account.");
        return new TenantScope(customerId, false);
    }

    /**
     * Builds the scope of the authenticated user. Administrator sessions get the admin scope,
     * customer sessions get their own account, and a customer login that is not linked to an
     * account is rejected outright.
     */
    public static TenantScope from(AppUserPrincipal principal) {
        if (principal == null) throw new AccessDeniedException("Authentication is required.");
        if (principal.isAdmin()) return administrator();
        return ofCustomer(principal.requireCustomerId());
    }

    public boolean isAdminScope() {
        return admin;
    }

    /** The customer this scope belongs to; administrators have none. */
    public long requireCustomerId() {
        if (admin || customerId == null) {
            throw new AccessDeniedException("This action is only available to a customer account.");
        }
        return customerId;
    }

    public boolean canAccessCustomer(Long ownerCustomerId) {
        return admin || (customerId != null && customerId.equals(ownerCustomerId));
    }

    /**
     * Authorises access to a single record. A record owned by another account is reported as
     * "not found" so that ids cannot be probed to discover other tenants' data.
     */
    public void requireCustomerAccess(Long ownerCustomerId) {
        if (!canAccessCustomer(ownerCustomerId)) {
            throw new ResourceNotFoundException("That record does not exist.");
        }
    }

    /**
     * Guards writes to customer-owned data. Administrators have read-only access across
     * accounts; each customer manages its own products, categories and invoices.
     */
    public void requireWriteAccess() {
        if (admin) {
            throw new AccessDeniedException("Administrator accounts have read-only access to customer records.");
        }
        requireCustomerId();
    }
}