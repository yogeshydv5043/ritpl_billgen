package com.ritpl.billgen.service;

import java.util.Locale;
import com.ritpl.billgen.security.TenantScope;

/**
 * The checks every configuration screen needs, in one place: an administrator-only gate, field length
 * and format rules that match the database column, and the trim-to-null habit that keeps "I removed
 * this" from being stored as a single space.
 *
 * <p>These are the rules a browser also enforces, restated here because the browser is not the
 * authority: a request that skips the form must be refused for the same reason the field was marked
 * invalid. Each message names the field and the expectation, because it is shown to the person who has
 * to fix it.</p>
 */
final class ConfigurationRules {

    private ConfigurationRules() { }

    static void requireAdmin(TenantScope scope) {
        if (scope == null || !scope.isAdminScope()) {
            throw new org.springframework.security.access.AccessDeniedException("Administrator access is required.");
        }
    }

    static String required(String value, int max, String label) {
        String trimmed = optional(value, max, label);
        if (trimmed == null) throw new IllegalArgumentException(label + " is required.");
        return trimmed;
    }

    static String optional(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(label + " must be " + max + " characters or fewer.");
        }
        return trimmed;
    }

    /** A link an administrator promises is reachable: http(s) only. */
    static String url(String value, int max, String label) {
        String trimmed = optional(value, max, label);
        if (trimmed == null) return null;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException(label + " must start with http:// or https://");
        }
        return trimmed;
    }

    static String email(String value, int max, String label) {
        String trimmed = optional(value, max, label);
        if (trimmed == null) return null;
        int at = trimmed.indexOf('@');
        if (at < 1 || trimmed.indexOf('.', at) < 0) {
            throw new IllegalArgumentException(label + " must be a valid email address.");
        }
        return trimmed;
    }

    /** {@code #rrggbb}, because that is what a CSS property can use and what the column can hold. */
    static String color(String value, String label) {
        String trimmed = optional(value, 7, label);
        if (trimmed == null) return null;
        if (!trimmed.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException(label + " must be a hex colour such as #7357f4.");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    static int bounded(Integer value, int fallback, int min, int max, String label) {
        if (value == null) return fallback;
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    /** A server name, not a URL: {@code smtp.example.com}, never {@code https://smtp.example.com:587}. */
    static String host(String value, String label) {
        String trimmed = optional(value, 255, label);
        if (trimmed == null) return null;
        if (trimmed.contains("://") || trimmed.matches(".*[\\s/,].*")) {
            throw new IllegalArgumentException(label + " must be a server name such as smtp.example.com, without a URL.");
        }
        return trimmed;
    }

    /** An image reference: an absolute http(s) URL or a path inside this application. */
    static String image(String value, int max, String label) {
        String trimmed = optional(value, max, label);
        if (trimmed == null) return null;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/")) return trimmed;
        if (lower.startsWith("http://") || lower.startsWith("https://")) return trimmed;
        throw new IllegalArgumentException(label + " must be an http(s) URL or a path starting with /.");
    }
}
