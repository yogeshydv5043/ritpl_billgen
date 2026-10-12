package com.ritpl.billgen.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.ritpl.billgen.service.ResourceNotFoundException;

class TenantScopeTest {

    @Test
    void customerScopeOnlyAccessesItsOwnAccount() {
        TenantScope scope = TenantScope.ofCustomer(17L);

        assertDoesNotThrow(() -> scope.requireCustomerAccess(17L));
        assertThrows(ResourceNotFoundException.class, () -> scope.requireCustomerAccess(18L));
    }

    @Test
    void customerScopeIsPinnedToItsSessionAccount() {
        TenantScope scope = TenantScope.ofCustomer(17L);

        assertEquals(17L, scope.requireCustomerId());
        assertDoesNotThrow(scope::requireWriteAccess);
    }

    @Test
    void adminCanReadAcrossAccountsButCannotWriteCustomerData() {
        TenantScope scope = TenantScope.administrator();

        assertDoesNotThrow(() -> scope.requireCustomerAccess(18L));
        assertThrows(AccessDeniedException.class, scope::requireWriteAccess);
    }
}