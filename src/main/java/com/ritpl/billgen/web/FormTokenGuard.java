package com.ritpl.billgen.web;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * One-time tokens for forms where a repeated submission would create a second record - issuing an
 * invoice is the case that matters here.
 *
 * <p>The form handler puts a token in the page, and the POST is accepted only if that exact value is
 * still unspent in this session. A double-click, a browser that replays a POST on back-navigation, or
 * a scripted retry therefore cannot produce a second invoice: the first request spends the token and
 * the rest are refused with a message that tells the customer to look at their list instead of
 * retrying. This is a session-level guard, not a uniqueness constraint on the data, and it is
 * deliberately paired with the POST-redirect-GET flow rather than replacing it.</p>
 */
@Component
public class FormTokenGuard {
    private static final String SESSION_KEY = "billgen.form-tokens";
    /** A session that keeps opening forms must not grow without bound. */
    private static final int TOKENS_PER_FORM = 12;

    /** A value to embed in the form. Issuing does not invalidate earlier tokens for the same form. */
    public String issue(HttpServletRequest request, String purpose) {
        HttpSession session = request.getSession(true);
        String token = UUID.randomUUID().toString();
        Map<String, Deque<String>> issued = issued(session);
        issued.computeIfAbsent(purpose, key -> new ArrayDeque<>()).addLast(token);
        Deque<String> forThisForm = issued.get(purpose);
        while (forThisForm.size() > TOKENS_PER_FORM) forThisForm.removeFirst();
        // Written back explicitly: with a Redis-backed session, mutating the stored object in place is
        // not enough for the change to be saved.
        session.setAttribute(SESSION_KEY, issued);
        return token;
    }

    /**
     * True when {@code token} was handed to this session for this form and has not been used. The token
     * is spent whether or not the submission then succeeds, so a retry after a validation error comes
     * from a freshly rendered form with a fresh token.
     */
    public boolean consume(HttpServletRequest request, String purpose, String token) {
        if (token == null || token.isBlank()) return false;
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        Map<String, Deque<String>> issued = issued(session);
        Deque<String> forThisForm = issued.get(purpose);
        if (forThisForm == null || !forThisForm.remove(token.trim())) return false;
        session.setAttribute(SESSION_KEY, issued);
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Deque<String>> issued(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof Map<?, ?> map && !map.isEmpty()) return (Map<String, Deque<String>>) map;
        Map<String, Deque<String>> fresh = new HashMap<>();
        session.setAttribute(SESSION_KEY, fresh);
        return fresh;
    }
}
