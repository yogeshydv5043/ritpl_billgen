package com.ritpl.billgen.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ritpl.billgen.entity.AccountStatus;
import com.ritpl.billgen.entity.AppUser;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.InvoiceStatus;
import com.ritpl.billgen.entity.SubscriptionStatus;
import com.ritpl.billgen.entity.UserRole;
import com.ritpl.billgen.repository.AppUserRepository;
import com.ritpl.billgen.repository.CustomerRepository;
import com.ritpl.billgen.repository.CustomerSubscriptionRepository;
import com.ritpl.billgen.repository.InvoiceRepository;
import com.ritpl.billgen.security.TenantScope;

/**
 * Customer accounts. An account is the tenant boundary of the application: its invoices, products
 * and categories belong to it, and exactly one customer login is linked to it. Public lookup
 * methods take a {@link TenantScope}; writes to account/login administration require Admin scope.
 */
@Service
public class CustomerService {
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final CustomerRepository customers;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final InvoiceRepository invoices;
    private final CustomerSubscriptionRepository subscriptions;
    /**
     * Documented deletion policy. Outstanding invoices block a soft delete unless the administrator
     * confirms the override, because the invoice history still belongs to a live relationship.
     */
    private final boolean blockOutstandingInvoices;

    public CustomerService(CustomerRepository customers, AppUserRepository users, PasswordEncoder passwordEncoder,
            AuditService audit, InvoiceRepository invoices, CustomerSubscriptionRepository subscriptions,
            @org.springframework.beans.factory.annotation.Value("${app.customers.deletion.block-outstanding-invoices:true}")
            boolean blockOutstandingInvoices) {
        this.customers = customers; this.users = users; this.passwordEncoder = passwordEncoder; this.audit = audit;
        this.invoices = invoices; this.subscriptions = subscriptions;
        this.blockOutstandingInvoices = blockOutstandingInvoices;
    }

    /** Admins may search all real customer accounts; customers only receive their own account. */
    @Transactional(readOnly = true)
    public List<Customer> findAll(TenantScope scope, String search) {
        if (!scope.isAdminScope()) return List.of(findById(scope, scope.requireCustomerId()));
        if (search == null || search.isBlank()) return customers.findBySystemAccountFalseAndDeletedAtIsNullOrderByCompanyNameAsc();
        return customers.searchVisible(search.trim());
    }

    /** Live accounts only: a soft-deleted account is invisible to the ordinary lookups. */
    @Transactional(readOnly = true)
    public Customer findById(TenantScope scope, long id) {
        scope.requireCustomerAccess(id);
        Customer customer = customers.findByIdAndDeletedAtIsNullAndSystemAccountFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        return customer;
    }

    /** Used by the deleted-accounts view and by restore. */
    @Transactional(readOnly = true)
    public Customer findByIdIncludingDeleted(TenantScope scope, long id) {
        scope.requireCustomerAccess(id);
        Customer customer = customers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        if (customer.isSystemAccount()) throw new ResourceNotFoundException("Customer not found.");
        return customer;
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findLogin(TenantScope scope, long customerId) {
        findById(scope, customerId);
        return users.findByCustomer_Id(customerId);
    }

    /** Creates a self-service Customer account; plan activation is a separate immediate action. */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Customer register(RegistrationCommand command) {
        if (command == null) throw new IllegalArgumentException("Enter the account details.");
        CustomerAccountCommand account = new CustomerAccountCommand(command.companyName(), command.contactName(), command.email(),
                command.phone(), null, null, null, null, command.country(), null, command.email(), command.password(), true);
        Customer customer = new Customer(null, null, null);
        apply(account, customer);
        validatePassword(command.password());
        if (users.existsByEmailIgnoreCase(customer.getEmail())) throw new IllegalArgumentException("An account with this email already exists.");
        customers.save(customer);
        users.save(newLogin(customer.getEmail(), command.password(), customer, true));
        audit.record(AuditEventCommand.success(customer.getId(), "REGISTER", "CUSTOMER", "Customer account", customer.getId(),
                "Customer account registered; no paid subscription or payment credentials were created.", null,
                Map.of("companyName", customer.getCompanyName(), "email", customer.getEmail())).withActor("PUBLIC_SIGNUP"));
        return customer;
    }

    /** Customer-managed invoice identity and logo. The login email/password are never changed here. */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Customer updateCompanyProfile(TenantScope scope, CompanyProfileCommand command) {
        scope.requireWriteAccess();
        long customerId = scope.requireCustomerId();
        Customer customer = findById(scope, customerId);
        if (command == null) throw new IllegalArgumentException("Enter the company profile.");
        validateCompanyProfile(command);
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        if (customers.existsByEmailIgnoreCaseAndIdNot(email, customerId)) throw new IllegalArgumentException("A company profile already uses this email.");
        Map<String, Object> before = Map.of("companyName", customer.getCompanyName(), "email", customer.getEmail(),
                "hasLogo", customer.getLogoData() != null && customer.getLogoData().length > 0);
        customer.setCompanyName(command.companyName().trim()); customer.setContactName(command.contactName().trim());
        customer.setEmail(email); customer.setPhone(blankToNull(command.phone())); customer.setBillingAddress(blankToNull(command.billingAddress()));
        customer.setCity(blankToNull(command.city())); customer.setState(blankToNull(command.state()));
        customer.setPostalCode(blankToNull(command.postalCode())); customer.setCountry(blankToNull(command.country()) == null ? "India" : command.country().trim());
        customer.setGstin(blankToNull(command.gstin()));
        if (command.clearLogo()) { customer.setLogoData(null); customer.setLogoContentType(null); }
        else if (command.logoData() != null && command.logoData().length > 0) {
            validateLogo(command.logoData(), command.logoContentType());
            customer.setLogoData(command.logoData()); customer.setLogoContentType(command.logoContentType());
        }
        audit.record(AuditEventCommand.success(customerId, "UPDATE", "COMPANY_PROFILE", "Company profile", customerId,
                "Customer updated its company invoice identity.", before,
                Map.of("companyName", customer.getCompanyName(), "email", customer.getEmail(),
                        "hasLogo", customer.getLogoData() != null && customer.getLogoData().length > 0)));
        return customer;
    }

    /** Creates the account together with the login that gives the account its own data scope. */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Customer createAccount(TenantScope scope, CustomerAccountCommand command) {
        requireAdmin(scope);
        Customer customer = new Customer(null, null, null);
        apply(command, customer);
        customers.save(customer);
        String loginEmail = loginEmail(command, customer.getEmail());
        validatePassword(command.password());
        if (users.existsByEmailIgnoreCase(loginEmail)) throw new IllegalArgumentException("A login with this email already exists.");
        users.save(newLogin(loginEmail, command.password(), customer, command.loginEnabled() == null || command.loginEnabled()));
        audit.record(AuditEventCommand.success(customer.getId(), "CREATE", "CUSTOMER", "Customer account", customer.getId(),
                "Administrator created a customer account and login.", null,
                Map.of("companyName", customer.getCompanyName(), "email", customer.getEmail(), "loginEnabled", command.loginEnabled() == null || command.loginEnabled())));
        return customer;
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Customer updateAccount(TenantScope scope, long id, CustomerAccountCommand command) {
        requireAdmin(scope);
        Customer current = findById(scope, id);
        Map<String, Object> before = Map.of("companyName", current.getCompanyName(), "email", current.getEmail());
        apply(command, current);
        customers.save(current);
        updateLogin(current, command);
        audit.record(AuditEventCommand.success(id, "UPDATE", "CUSTOMER", "Customer account", id,
                "Administrator updated customer account settings.", before,
                Map.of("companyName", current.getCompanyName(), "email", current.getEmail())));
        return current;
    }

    /** Enables or disables the account login without touching any billing data. */
    @Transactional
    public void setLoginEnabled(TenantScope scope, long id, boolean enabled) {
        requireAdmin(scope);
        AppUser login = findLogin(scope, id).orElseThrow(() -> new ResourceNotFoundException("This account has no login."));
        boolean before = login.isEnabled(); login.setEnabled(enabled);
        audit.record(AuditEventCommand.success(id, "UPDATE", "CUSTOMER_LOGIN", "Customer login", login.getId(),
                "Administrator changed customer login availability.", Map.of("enabled", before), Map.of("enabled", enabled)));
    }

    @Transactional
    public void resetPassword(TenantScope scope, long id, String password) {
        requireAdmin(scope);
        validatePassword(password);
        AppUser login = findLogin(scope, id).orElseThrow(() -> new ResourceNotFoundException("This account has no login."));
        login.setPasswordHash(passwordEncoder.encode(password));
        audit.record(AuditEventCommand.success(id, "RESET_PASSWORD", "CUSTOMER_LOGIN", "Customer login", login.getId(),
                "Administrator reset a customer login password; the password itself is never logged.", null, Map.of("passwordChanged", true)));
    }

    /**
     * Lets a signed-in administrator change their own password. The current password is verified
     * first, and neither password is ever written to the audit trail.
     */
    @Transactional
    public void changeAdminPassword(com.ritpl.billgen.security.AppUserPrincipal principal, String currentPassword,
            String newPassword) {
        if (principal == null || principal.getId() == null) throw new IllegalArgumentException("Sign in again to change a password.");
        AppUser user = users.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            audit.record(AuditEventCommand.failure(user.getCustomer() == null ? null : user.getCustomer().getId(),
                    "PASSWORD_CHANGE_FAILURE", "ADMIN_ACCOUNT", "Administrator login", user.getId(),
                    "A password change was rejected because the current password did not match.", Map.of()));
            throw new IllegalArgumentException("The current password is not correct.");
        }
        validatePassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
        audit.record(AuditEventCommand.success(null, "PASSWORD_CHANGE", "ADMIN_ACCOUNT", "Administrator login", user.getId(),
                "Administrator changed their own password. The password itself is never logged.", null,
                Map.of("passwordChanged", true)));
    }

    private void apply(CustomerAccountCommand command, Customer customer) {
        if (command == null) throw new IllegalArgumentException("Enter the account details.");
        if (command.companyName() == null || command.companyName().isBlank() || command.companyName().trim().length() > 160) {
            throw new IllegalArgumentException("Enter a company name of at most 160 characters.");
        }
        if (command.contactName() == null || command.contactName().isBlank() || command.contactName().trim().length() > 120) {
            throw new IllegalArgumentException("Enter a contact name of at most 120 characters.");
        }
        if (command.email() == null || command.email().isBlank()) throw new IllegalArgumentException("Enter an email address.");
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        boolean duplicate = customer.getId() == null
                ? customers.existsByEmailIgnoreCase(email)
                : customers.existsByEmailIgnoreCaseAndIdNot(email, customer.getId());
        if (duplicate) {
            boolean archived = customers.findByEmailIgnoreCaseAndDeletedAtIsNotNull(email).isPresent();
            throw new IllegalArgumentException(archived
                    ? "That email belongs to a deleted account. Restore it first, or use another address."
                    : "A customer account with this email already exists.");
        }
        if (command.gstin() != null && !command.gstin().isBlank() && !command.gstin().trim().matches("[0-9A-Za-z]{10,24}")) {
            throw new IllegalArgumentException("Tax or registration details may use 10-24 letters and digits.");
        }
        if (command.phone() != null && !command.phone().isBlank() && !command.phone().trim().matches("[0-9+()\\- ]{6,40}")) {
            throw new IllegalArgumentException("A phone number may only use digits, spaces and +()-.");
        }
        customer.setCompanyName(command.companyName().trim());
        customer.setContactName(command.contactName().trim());
        customer.setEmail(email);
        customer.setPhone(blankToNull(command.phone()));
        customer.setBillingAddress(blankToNull(command.billingAddress()));
        customer.setCity(blankToNull(command.city()));
        customer.setState(blankToNull(command.state()));
        customer.setPostalCode(blankToNull(command.postalCode()));
        customer.setCountry(command.country() == null || command.country().isBlank() ? "India" : command.country().trim());
        customer.setGstin(blankToNull(command.gstin()));
    }

    private void updateLogin(Customer customer, CustomerAccountCommand command) {
        Optional<AppUser> existing = users.findByCustomer_Id(customer.getId());
        if (existing.isEmpty()) {
            String loginEmail = loginEmail(command, customer.getEmail());
            validatePassword(command.password());
            if (users.existsByEmailIgnoreCase(loginEmail)) throw new IllegalArgumentException("A login with this email already exists.");
            users.save(newLogin(loginEmail, command.password(), customer, command.loginEnabled() == null || command.loginEnabled()));
            return;
        }
        AppUser login = existing.get();
        boolean emailChanged = command.loginEmail() != null && !command.loginEmail().isBlank()
                && !command.loginEmail().trim().equalsIgnoreCase(login.getEmail());
        boolean passwordProvided = command.password() != null && !command.password().isBlank();
        if (emailChanged) {
            // Do not silently lock the account out: changing its sign-in address also requires
            // an explicitly chosen password.
            if (!passwordProvided) throw new IllegalArgumentException("Enter a new password when changing the login email.");
            String password = command.password();
            validatePassword(password);
            String changedEmail = command.loginEmail().trim();
            if (users.existsByEmailIgnoreCaseAndIdNot(changedEmail, login.getId())) {
                throw new IllegalArgumentException("A login with this email already exists.");
            }
            login.setEmail(changedEmail);
            login.setPasswordHash(passwordEncoder.encode(password));
            if (command.loginEnabled() != null) login.setEnabled(command.loginEnabled());
            return;
        }
        if (passwordProvided) {
            validatePassword(command.password());
            login.setPasswordHash(passwordEncoder.encode(command.password()));
        }
        if (command.loginEnabled() != null) login.setEnabled(command.loginEnabled());
    }

    private AppUser newLogin(String loginEmail, String password, Customer customer, boolean enabled) {
        AppUser user = new AppUser(loginEmail, passwordEncoder.encode(password), UserRole.CUSTOMER);
        user.setCustomer(customer);
        user.setEnabled(enabled);
        return user;
    }

    private String loginEmail(CustomerAccountCommand command, String fallback) {
        String email = command.loginEmail() == null || command.loginEmail().isBlank() ? fallback : command.loginEmail().trim();
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Enter a login email address.");
        return email.toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Set a login password of at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
    }

    private void validateCompanyProfile(CompanyProfileCommand c) {
        if (c.companyName() == null || c.companyName().isBlank() || c.companyName().trim().length() > 160)
            throw new IllegalArgumentException("Enter a company name of at most 160 characters.");
        if (c.contactName() == null || c.contactName().isBlank() || c.contactName().trim().length() > 120)
            throw new IllegalArgumentException("Enter a contact name of at most 120 characters.");
        if (c.email() == null || c.email().isBlank() || c.email().trim().length() > 190
                || !Pattern.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", c.email().trim()))
            throw new IllegalArgumentException("Enter a valid company email address.");
        if (c.phone() != null && c.phone().length() > 40 || c.billingAddress() != null && c.billingAddress().length() > 255
                || c.city() != null && c.city().length() > 80 || c.state() != null && c.state().length() > 80
                || c.postalCode() != null && c.postalCode().length() > 24 || c.country() != null && c.country().length() > 80
                || c.gstin() != null && c.gstin().length() > 24)
            throw new IllegalArgumentException("One of the company profile fields is too long.");
        if (c.logoData() != null && c.logoData().length > 0) validateLogo(c.logoData(), c.logoContentType());
    }

    private void validateLogo(byte[] data, String contentType) {
        if (data == null || data.length == 0) return;
        if (data.length > 1_048_576) throw new IllegalArgumentException("Company logo must be 1 MB or smaller.");
        boolean png = "image/png".equalsIgnoreCase(contentType) && data.length >= 8
                && (data[0] & 0xff) == 0x89 && data[1] == 0x50 && data[2] == 0x4e && data[3] == 0x47;
        boolean jpeg = "image/jpeg".equalsIgnoreCase(contentType) && data.length >= 3
                && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff;
        boolean webp = "image/webp".equalsIgnoreCase(contentType) && data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
        if (!png && !jpeg && !webp) throw new IllegalArgumentException("Upload a valid PNG, JPEG or WebP company logo.");
    }

    private void requireAdmin(TenantScope scope) {
        if (!scope.isAdminScope()) throw new org.springframework.security.access.AccessDeniedException("Administrator access is required.");
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    // ------------------------------------------------------------------------------------------
    // Account lifecycle for the Super Admin panel: enable, disable, soft delete, restore.
    // ------------------------------------------------------------------------------------------

    /** Enabling or disabling an account. A disabled account keeps every record and cannot sign in. */
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Customer setAccountStatus(TenantScope scope, long id, AccountStatus status, String actor) {
        requireAdmin(scope);
        Customer customer = findById(scope, id);
        AccountStatus before = customer.getStatus();
        if (before == status) return customer;
        customer.setStatus(status);
        // Blocking an account blocks its login, but enabling one does not silently re-open a login an
        // administrator had switched off on its own: only the blocking direction is written here, and
        // sign-in stays refused while the account is unusable because the authentication check ANDs the
        // two switches.
        if (status != AccountStatus.ACTIVE) {
            users.findByCustomer_Id(id).ifPresent(login -> login.setEnabled(false));
        }
        audit.record(AuditEventCommand.success(id, status == AccountStatus.ACTIVE ? "ENABLE" : "DISABLE", "CUSTOMER",
                "Customer account", id, "Administrator changed a customer account's availability.",
                Map.of("status", before.name()), Map.of("status", status.name(), "actor", actor == null ? "admin" : actor)));
        return customers.save(customer);
    }

    /** What a deletion would affect, shown in the confirmation dialog before anything changes. */
    @Transactional(readOnly = true)
    public Map<String, Object> deletionAssessment(TenantScope scope, long id) {
        requireAdmin(scope);
        Customer customer = findById(scope, id);
        long outstanding = invoices.countByCustomer_IdAndStatus(id, InvoiceStatus.SENT);
        long issued = invoices.countByCustomer_IdAndUsageCountedTrue(id);
        boolean activeSubscription = subscriptions
                .findFirstByCustomer_IdAndStatus(id, SubscriptionStatus.ACTIVE)
                .filter(subscription -> subscription.isEffective(java.time.LocalDate.now()))
                .isPresent();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("companyName", customer.getCompanyName());
        result.put("outstandingInvoices", outstanding);
        result.put("issuedInvoices", issued);
        result.put("activeSubscription", activeSubscription);
        result.put("blocked", blockOutstandingInvoices && outstanding > 0);
        result.put("needsConfirmation", activeSubscription || outstanding > 0 || issued > 0);
        return result;
    }

    /**
     * Soft deletion. Nothing is destroyed: invoices, audit records and the login all survive, the
     * account disappears from standard lists, and the timestamp and administrator are recorded.
     */
    @Transactional
    @CacheEvict(cacheNames = {"dashboard", "active-plans", "customer-templates"}, allEntries = true)
    public void softDelete(TenantScope scope, long id, String reason, boolean force, String actor) {
        requireAdmin(scope);
        Customer customer = findById(scope, id);
        Map<String, Object> assessment = deletionAssessment(scope, id);
        if (!force) {
            if (Boolean.TRUE.equals(assessment.get("activeSubscription"))) {
                throw new IllegalArgumentException("This account has an active subscription. Cancel or move it to a plan "
                        + "before deleting, or confirm the deletion with the override checked.");
            }
            if (Boolean.TRUE.equals(assessment.get("blocked"))) {
                throw new IllegalArgumentException("This account has " + assessment.get("outstandingInvoices")
                        + " outstanding invoice(s). Resolve them or confirm the deletion with the override checked.");
            }
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Record why the account is being deleted.");
        }
        AccountStatus previous = customer.getStatus();
        customer.setDeletedAt(java.time.Instant.now());
        customer.setDeletedBy(actor == null ? "admin" : actor);
        customer.setDeletionReason(reason.trim().length() > 400 ? reason.trim().substring(0, 400) : reason.trim());
        customer.setStatus(AccountStatus.DISABLED);
        users.findByCustomer_Id(id).ifPresent(login -> login.setEnabled(false));
        subscriptions.findFirstByCustomer_IdAndStatus(id, SubscriptionStatus.ACTIVE)
                .ifPresent(subscription -> {
                    subscription.setStatus(SubscriptionStatus.CANCELLED);
                    subscriptions.save(subscription);
                });
        audit.record(AuditEventCommand.success(id, "SOFT_DELETE", "CUSTOMER", "Customer account", id,
                "Administrator soft-deleted a customer account; invoices and audit history were preserved.",
                Map.of("status", previous.name(), "outstandingInvoices", assessment.get("outstandingInvoices")),
                Map.of("deleted", true, "reason", customer.getDeletionReason(),
                        "outstandingInvoices", assessment.get("outstandingInvoices"),
                        "activeSubscription", assessment.get("activeSubscription"))));
        customers.save(customer);
    }

    /** Restores a soft-deleted account. It comes back disabled so the administrator re-enables it deliberately. */
    @Transactional
    @CacheEvict(cacheNames = {"dashboard", "active-plans", "customer-templates"}, allEntries = true)
    public Customer restore(TenantScope scope, long id, String actor) {
        requireAdmin(scope);
        Customer customer = findByIdIncludingDeleted(scope, id);
        if (!customer.isDeleted()) throw new IllegalArgumentException("This account is not deleted.");
        customer.setDeletedAt(null);
        customer.setDeletedBy(null);
        customer.setDeletionReason(null);
        customer.setStatus(AccountStatus.DISABLED);
        users.findByCustomer_Id(id).ifPresent(login -> login.setEnabled(false));
        Customer saved = customers.save(customer);
        audit.record(AuditEventCommand.success(id, "RESTORE", "CUSTOMER", "Customer account", id,
                "Administrator restored a soft-deleted customer account. The login stays disabled until enabled.",
                Map.of("deleted", true), Map.of("deleted", false, "actor", actor == null ? "admin" : actor)));
        return saved;
    }
}
