package com.ritpl.billgen.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The small templating language behind dynamic notifications. Administrators may only use
 * placeholders this class knows about, so an activation can never ship a message full of tokens
 * that would be delivered literally to a customer.
 */
public final class NotificationPlaceholders {
    /** Every placeholder the application can resolve. */
    public static final Set<String> ALLOWED = Set.of("companyName", "customerName", "contactName", "planName",
            "planCode", "expiryDate", "daysRemaining", "invoiceLimit", "invoiceUsed", "remainingLimit",
            "auditLimit", "auditUsed", "accountStatus", "today", "jobName", "adminContact");
    /** Placeholders shown in the editor, in the order an administrator is likely to need them. */
    public static final List<String> SUGGESTED = List.of("companyName", "customerName", "planName", "expiryDate",
            "daysRemaining", "remainingLimit", "invoiceLimit", "invoiceUsed", "auditLimit", "accountStatus", "today");
    public static final String PLACEHOLDER = "{{remainingLimit}}";

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]{0,39})\\s*\\}\\}");
    private static final Pattern LOOSE = Pattern.compile("\\{\\{\\s*([^{}]{0,60}?)\\s*\\}\\}");

    private NotificationPlaceholders() { }

    public static Set<String> tokens(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null) return found;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) found.add(m.group(1));
        return found;
    }

    /** Placeholders that are not on the allow-list, or that are malformed, e.g. {@code {{ planName }. */
    public static Set<String> invalid(String text) {
        Set<String> problems = new LinkedHashSet<>();
        if (text == null) return problems;
        Matcher loose = LOOSE.matcher(text);
        while (loose.find()) {
            String inner = loose.group(1).trim();
            if (!inner.matches("[A-Za-z][A-Za-z0-9_]{0,39}") || !ALLOWED.contains(inner)) {
                problems.add("{{" + inner + "}}");
            }
        }
        // Any unbalanced braces would be delivered to the customer as-is.
        int open = text.indexOf("{{");
        if (open >= 0 && text.indexOf("}}", open) < 0) problems.add("unclosed {{");
        return problems;
    }

    public static String render(String text, Map<String, String> values) {
        if (text == null || text.isBlank()) return "";
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values == null ? null : values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? "—" : value));
        }
        m.appendTail(out);
        return out.toString();
    }
}
