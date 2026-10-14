package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ritpl.billgen.entity.AppUser;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.UserRole;
import com.ritpl.billgen.repository.AppUserRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.CustomerSubscriptionRepository;
import com.ritpl.billgen.service.CustomerAlertService;
import com.ritpl.billgen.repository.InvoiceRepository;

/** Public self-service registration: role assignment, password hashing and duplicate prevention. */
class CustomerRegistrationTest {

    private static final String PASSWORD = "Str0ngPass!1";

    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuditService audit = mock(AuditService.class);
    // Registration touches neither invoices nor subscriptions, but the deletion guard is enabled so
    // the same policy the web tier uses is under test.
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final CustomerSubscriptionRepository subscriptions = mock(CustomerSubscriptionRepository.class);
    // Alerts are an inbox side effect registration does not exercise; the notice itself is covered by
    // the security-alert path in the service.
    private final CustomerAlertService alerts = mock(CustomerAlertService.class);
    private final CustomerService service = new CustomerService(customers, users, encoder, audit, invoices,
            subscriptions, true, alerts);

    @Test
    void registerCreatesACustomerLoginWithAHashedPassword() {
        when(encoder.encode(PASSWORD)).thenReturn("{bcrypt}$2a$10$hashed-value");

        Customer registered = service.register(command("owner@acme.test"));

        assertEquals("Acme Traders", registered.getCompanyName());
        assertEquals("owner@acme.test", registered.getEmail());

        AppUser login = savedLogin();
        assertEquals(UserRole.CUSTOMER, login.getRole(), "public sign-up may only create the customer role");
        assertEquals("{bcrypt}$2a$10$hashed-value", login.getPasswordHash());
        assertNotEquals(PASSWORD, login.getPasswordHash(), "the raw password is never stored");
        assertEquals("owner@acme.test", login.getEmail(), "login email is normalised to lower case");
        assertEquals(registered, login.getCustomer(), "the login is linked to the company it owns");
        verify(encoder).encode(PASSWORD);
        verify(audit).record(any(AuditEventCommand.class));
    }

    @Test
    void publicSignUpCanNeverCreateAnAdministrator() {
        when(encoder.encode(PASSWORD)).thenReturn("{bcrypt}$2a$10$hashed-value");

        service.register(command("owner@acme.test"));

        assertNotEquals(UserRole.ADMIN, savedLogin().getRole(),
                "the public registration payload carries no role, so escalation to the admin role is impossible");
    }

    @Test
    void registerRejectsADuplicateEmail() {
        when(users.existsByEmailIgnoreCase("owner@acme.test")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.register(command("owner@acme.test")));

        verify(users, never()).save(any(AppUser.class));
        verify(encoder, never()).encode(anyString());
    }

    @Test
    void registerRejectsAWeakPassword() {
        RegistrationCommand weak = new RegistrationCommand("Acme Traders", "Owner", "owner2@acme.test",
                "9876543210", "India", "short");

        assertThrows(IllegalArgumentException.class, () -> service.register(weak));

        verify(users, never()).save(any(AppUser.class));
    }

    @Test
    void registerRejectsAMissingCompanyName() {
        RegistrationCommand blank = new RegistrationCommand("  ", "Owner", "owner3@acme.test",
                "9876543210", "India", PASSWORD);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.register(blank));

        assertTrue(error.getMessage().contains("company name"), "the failure explains which field is invalid");
        verify(users, never()).save(any(AppUser.class));
    }

    private RegistrationCommand command(String email) {
        return new RegistrationCommand("Acme Traders", "Owner", email, "9876543210", "India", PASSWORD);
    }

    private AppUser savedLogin() {
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        return captor.getValue();
    }
}
