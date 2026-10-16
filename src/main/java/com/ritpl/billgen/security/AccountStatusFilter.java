package com.ritpl.billgen.security;

import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ritpl.billgen.entity.AccountStatus;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.repository.CustomerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Makes an account-level block effective immediately instead of at the next sign-in.
 *
 * <p>Disabling or soft-deleting an account must stop it from being used, and a session that is
 * already signed in would otherwise keep working until it expires. This filter re-reads the
 * customer row behind a session on each request and ends the session when the account is no longer
 * usable. It runs after the security filter chain, so the authentication it inspects is the one the
 * request is acting on, and it deliberately skips the stateless API - that path authenticates per
 * request and is already refused by {@link AppUserDetailsService}.</p>
 */
@Component
public class AccountStatusFilter extends OncePerRequestFilter {
    private final CustomerRepository customers;

    public AccountStatusFilter(CustomerRepository customers) { this.customers = customers; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) path = path.substring(context.length());
        if (path == null || path.isBlank()) path = "/";
        // The API authenticates on every request, and these paths are reachable while signed out.
        return path.startsWith("/api/") || path.startsWith("/login") || path.startsWith("/register")
                || path.startsWith("/logout") || path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/") || path.startsWith("/actuator/") || path.equals("/favicon.ico")
                || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal principal && !principal.isAdmin()
                && principal.getCustomerId() != null) {
            Customer customer = customers.findById(principal.getCustomerId()).orElse(null);
            boolean usable = customer != null && !customer.isDeleted() && customer.getStatus() == AccountStatus.ACTIVE;
            if (!usable) {
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                logger.debug("Ended session for customer account " + principal.getCustomerId()
                        + " because it is disabled or deleted.");
                response.sendRedirect(request.getContextPath() + "/login?blocked");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
