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
import com.ritpl.billgen.entity.AppUser;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.UserRole;
import com.ritpl.billgen.repository.AppUserRepository;
import com.ritpl.billgen.repository.CustomerRepository;
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

    public CustomerService(CustomerRepository customers, AppUserRepository users, PasswordEncoder passwordEncoder, AuditService audit) {
        this.customers = customers; this.users = users; this.passwordEncoder = passwordEncoder; this.audit = audit;
    }

    /** Admins may search all real customer accounts; customers only receive their own account. */
    @Transactional(readOnly = true)
    public List<Customer> findAll(TenantScope scope, String search) {
        if (!scope.isAdminScope()) return List.of(findById(scope, scope.requireCustomerId()));
        if (search == null || search.isBlank()) return customers.findBySystemAccountFalseOrderByCompanyNameAsc();
        return customers.searchVisible(search.trim());
    }

    @Transactional(readOnly = true)
    public Customer findById(TenantScope scope, long id) {
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
        if (duplicate) throw new IllegalArgumentException("A customer account with this email already exists.");
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
}