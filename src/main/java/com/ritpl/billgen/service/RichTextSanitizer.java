package com.ritpl.billgen.service;

import java.util.Locale;
import java.util.Set;

/**
 * Reduces administrator-authored HTML to the small set of markup a policy page or an answer is written
 * with, so the browser can be handed the result with {@code th:utext} and nothing in it can run.
 *
 * <p>The approach is an allow-list, not an escape list: unknown and dangerous elements lose their tag,
 * everything keeps its text, and attributes are dropped rather than inspected - except a link's
 * {@code href}, which survives only when its scheme is one a document should use. A script or a style
 * block loses its contents too, because a policy that renders as "undefined is not a function" is
 * worse than one that renders as plain paragraphs.</p>
 */
public final class RichTextSanitizer {

    /** The tags a policy or FAQ is realistically written with. */
    private static final Set<String> ALLOWED = Set.of("p", "br", "ul", "ol", "li", "strong", "b", "em", "i",
            "h2", "h3", "h4", "blockquote", "a");
    /** Whose contents are dropped along with the tag. */
    private static final Set<String> DROP_CONTENT = Set.of("script", "style", "iframe", "object", "embed",
            "template", "noscript", "svg", "form", "math");
    private static final Set<String> VOID = Set.of("br");

    private RichTextSanitizer() { }

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) return "";
        StringBuilder out = new StringBuilder(html.length() + 32);
        int i = 0;
        String dropping = null;
        while (i < html.length()) {
            char c = html.charAt(i);
            if (c != '<') {
                if (dropping == null) out.append(escapeText(c));
                i++;
                continue;
            }
            int close = html.indexOf('>', i);
            if (close < 0) {                                    // a stray '<' with no tag after it
                if (dropping == null) out.append("&lt;");
                break;
            }
            String raw = html.substring(i + 1, close).trim();
            boolean isClosing = raw.startsWith("/");
            String body = isClosing ? raw.substring(1).trim() : raw;
            // "<br/>" has no space before the slash, so the marker is taken off the name rather than
            // leaving a name that is not a name - which would have turned the tag into visible text.
            boolean selfClosing = !isClosing && body.endsWith("/");
            if (selfClosing) body = body.substring(0, body.length() - 1).trim();
            int space = body.indexOf(' ');
            String tag = (space < 0 ? body : body.substring(0, space)).toLowerCase(Locale.ROOT).trim();
            // A name that is not a name at all ("<!", "<?>", "3div") is somebody writing less-than in a
            // sentence, so only the character is escaped and scanning continues from the next one.
            if (!tag.matches("[a-z][a-z0-9]*")) {
                if (dropping == null) out.append("&lt;");
                i++;
                continue;
            }
            if (dropping != null) {
                if (dropping.equals(tag) && isClosing) dropping = null;
                i = close + 1;
                continue;
            }
            if (DROP_CONTENT.contains(tag)) {
                if (!isClosing) dropping = tag;
                i = close + 1;
                continue;
            }
            if (!ALLOWED.contains(tag)) {
                i = close + 1;                                   // keep the words, lose the tag
                continue;
            }
            if (isClosing) {
                out.append("</").append(tag).append(">");
            } else if (VOID.contains(tag) || selfClosing) {
                out.append('<').append(tag).append(">");
            } else {
                String href = tag.equals("a") ? safeHref(body) : null;
                out.append('<').append(tag);
                if (href != null) out.append(" href=\"").append(escapeAttribute(href)).append('\"')
                        .append(" rel=\"noopener noreferrer\"");
                out.append('>');
            }
            i = close + 1;
        }
        return out.toString();
    }

    /** An http(s), mailto or in-document target only; anything else means the link is dropped. */
    private static String safeHref(String attributeBody) {
        int start = attributeBody.toLowerCase(Locale.ROOT).indexOf("href");
        if (start < 0) return null;
        int equals = attributeBody.indexOf('=', start);
        if (equals < 0) return null;
        String value = attributeBody.substring(equals + 1).trim();
        if (value.startsWith("\"") || value.startsWith("'")) {
            char quote = value.charAt(0);
            int end = value.indexOf(quote, 1);
            value = end < 0 ? value.substring(1) : value.substring(1, end);
        } else {
            int space = value.indexOf(' ');
            if (space > 0) value = value.substring(0, space);
        }
        String candidate = value.trim();
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("vbscript:") || lower.startsWith("data:")) return null;
        if (candidate.startsWith("#") || candidate.startsWith("/")) return candidate;
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:") ? candidate : null;
    }

    private static String escapeText(char c) {
        return switch (c) {
            case '&' -> "&amp;";
            case '<' -> "&lt;";
            case '>' -> "&gt;";
            default -> String.valueOf(c);
        };
    }

    private static String escapeAttribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
