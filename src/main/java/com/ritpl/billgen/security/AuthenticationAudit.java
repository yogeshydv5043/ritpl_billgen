package com.ritpl.billgen.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.ritpl.billgen.service.AuditEventCommand;
import com.ritpl.billgen.service.AuditService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Sign-in, sign-out and rejected credentials are audit events like any other, so they are recorded
 * through the same registry instead of a separate log. The recorder lives next to the security
 * configuration on purpose: the filters decide when a session changes, and nothing else in the
 * application should have to know about them.
 *
 * <p>Only the identity and the outcome are written. A rejected attempt stores the name that was
 * typed, never the password, and the failure message is summarised rather than quoted so a
 * provider's wording cannot leak whether an account exists.</p>
 */
@Component
public class AuthenticationAudit {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationAudit.class);
    private final AuditService audit;

    public AuthenticationAudit(AuditService audit) { this.audit = audit; }

    /** Records a successful form login. Basic-auth API calls are deliberately not audited here. */
    public void recordLogin(Authentication authentication, HttpServletRequest request) {
        Object raw = authentication == null ? null : authentication.getPrincipal();
        if (!(raw instanceof AppUserPrincipal principal)) return;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", "password");
        details.put("role", principal.getRole());
        write(() -> audit.record(AuditEventCommand.success(principal.getCustomerId(), "LOGIN", "SESSION", "Session",
                principal.getId(), summary(principal) + " signed in", null, details))
                .withActor(principal.getEmail()));
    }

    /** Records a rejected attempt without confirming whether the account exists. */
    public void recordFailure(HttpServletRequest request) {
        String attempted = request.getParameter("username");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", "invalid_credentials");
        write(() -> audit.recordFailure(AuditEventCommand.failure(null, "LOGIN_FAILURE", "SESSION", "Session", null,
                "Sign-in was rejected for " + clip(attempted == null ? "(empty)" : attempted), details))
                .withActor(clip(attempted == null ? "unknown" : attempted)));
    }

    /** Called while the session is being torn down, so the row is written before it disappears. */
    public void recordLogout(Authentication authentication, HttpServletRequest request) {
        Object raw = authentication == null ? null : authentication.getPrincipal();
        if (!(raw instanceof AppUserPrincipal principal)) return;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("role", principal.getRole());
        write(() -> audit.record(AuditEventCommand.success(principal.getCustomerId(), "LOGOUT", "SESSION", "Session",
                principal.getId(), summary(principal) + " signed out", null, details))
                .withActor(principal.getEmail()));
    }

    /**
     * A broken audit write must not lock anyone out of their own account, so a failure is reported
     * to the application log and the sign-in continues. Nothing is retried: the missing row is
     * visible as a gap rather than replaced by a guess.
     */
    private void write(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Sign-in audit event could not be recorded: {}", e.getMessage());
        }
    }

    private String summary(AppUserPrincipal principal) {
        return principal.isAdmin() ? "Administrator" : "Customer account";
    }
    private String clip(String value) { return value.length() <= 190 ? value : value.substring(0, 190); }
}
