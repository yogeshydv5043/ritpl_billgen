package com.ritpl.billgen.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The screen a customer sees is not covered by a service test: a page can pass every assertion about
 * its model and still render as unstyled HTML. These checks read the templates and the two stylesheets
 * as they are written, and refuse the four ways this application has actually gone wrong while growing:
 *
 * <ol><li>a markup class invented in a page and never added to the sheet, so the element renders with
 * browser defaults;</li>
 * <li>a confirmation asked for with the browser dialog instead of the shared one, which is unstyled,
 * blocking, and can be silenced for the whole origin;</li>
 * <li>a confirmation that asks the shared dialog to appear on a page that never mounted it, so the
 * submit is swallowed;</li>
 * <li>a control with nothing to name it, which a screen reader reads as "edit, unlabelled".</li></ol>
 *
 * <p>Nothing here needs a database: the templates and sheets are read from the working tree, and the
 * test steps aside if they are not there (a packaged jar has no source directory to walk).</p>
 */
class UiConsistencyTest {

    /**
     * Classes that exist as a handle rather than as a style of their own. Each one is here because the
     * look it needs is already supplied by another selector, so the page stays readable and the rule
     * below stays strict for everything else.
     */
    private static final Map<String, String> STYLED_ELSEWHERE = Map.of(
            "invoice-lines", "the mount point the line editor adds rows to; each row is styled by .invoice-line",
            "line-description", "the field inside a line row; .invoice-line input already sizes and skins it",
            "line-product-id", "a hidden input that carries the catalogue id",
            "plan-template-features", "used together with plan-features, which supplies the whole look",
            "tab-button", "the tab bar styles .tabs button, which is what these elements are",
            "template-design-form", "used together with panel form-panel, which supplies the padding");

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern CLASS_ATTRIBUTE = Pattern.compile("(?:th:)?class(?:append)?=\"([^\"]*)\"");
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{[^}]*}");
    private static final Pattern CLASS_TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern SELECTOR_PREFIX = Pattern.compile("\\.([A-Za-z][A-Za-z0-9_-]*)");
    private static final Pattern CONTROL = Pattern.compile("<(input|select|textarea)\\b[^>]*>");
    private static final Pattern LABELLED_BY = Pattern.compile("(?:th:)?for=\"([^\"]*)\"");

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    private static List<Path> pages = List.of();
    private static Set<String> styled = Set.of();

    @BeforeAll
    static void readTheSheets() throws IOException {
        assumeTrue(Files.isDirectory(TEMPLATES), "no template sources next to a packaged build");
        pages = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(TEMPLATES)) {
            walk.filter(p -> p.toString().endsWith(".html")).sorted().forEach(p -> pages.add(p));
        }
        Set<String> names = new LinkedHashSet<>();
        try (Stream<Path> sheets = Files.list(Path.of("src/main/resources/static/css"))) {
            for (Path sheet : (Iterable<Path>) sheets.sorted()::iterator) {
                names.addAll(selectors(Files.readString(sheet)));
            }
        }
        // A page may carry its own <style> block, and those rules count just as much.
        for (Path page : pages) {
            Matcher style = Pattern.compile("<style[^>]*>(.*?)</style>", Pattern.DOTALL).matcher(Files.readString(page));
            while (style.find()) names.addAll(selectors(style.group(1)));
        }
        styled = names;
    }

    /**
     * The class names on the left of a brace, which is the only place a class can be given a look. The
     * declarations are walked past rather than cut away, so a rule that opens a media query is still
     * read as a rule and the selector next to it is not mistaken for an undefined one.
     */
    private static Set<String> selectors(String css) {
        Set<String> found = new LinkedHashSet<>();
        String source = COMMENTS.matcher(css).replaceAll(" ");
        StringBuilder head = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                String selector = head.toString().trim();
                head.setLength(0);
                if (selector.isEmpty() || selector.startsWith("@")) continue;
                Matcher m = SELECTOR_PREFIX.matcher(selector);
                while (m.find()) found.add(m.group(1));
            } else if (c == '}' || c == ';') {
                head.setLength(0);
            } else {
                head.append(c);
            }
        }
        return found;
    }

    /** A page is read without its comments: prose about an attribute is not the attribute. */
    private static String markup(Path page) {
        return HTML_COMMENT.matcher(read(page)).replaceAll(" ");
    }

    @Test
    void everyClassTheTemplatesReachForIsDefinedSomewhere() {
        Map<String, List<String>> missing = new LinkedHashMap<>();
        for (Path page : pages) {
            Matcher attribute = CLASS_ATTRIBUTE.matcher(markup(page));
            while (attribute.find()) {
                String value = attribute.group(1);
                // A class put together at run time ('status-' + the value) is only ever complete in the
                // browser, so a fragment still holding its hyphen is left alone: everything spelled out
                // in the page has to be a real selector.
                Matcher tokens = CLASS_TOKEN.matcher(EXPRESSION.matcher(value).replaceAll(" "));
                while (tokens.find()) {
                    String name = tokens.group();
                    if (name.endsWith("-") || styled.contains(name) || STYLED_ELSEWHERE.containsKey(name)) continue;
                    missing.computeIfAbsent(name, k -> new ArrayList<>()).add(shortName(page));
                }
            }
        }
        assertTrue(missing.isEmpty(), () -> "used but never styled: " + missing);
    }

    @Test
    void noPageAsksTheBrowserForAConfirmation() {
        for (Path page : pages) {
            String html = markup(page);
            assertFalse(html.contains("return confirm("), () ->
                    shortName(page) + " opens the browser dialog; the app has one of its own in "
                            + "fragments/components :: confirmHost, which is styled, focusable and cannot be silenced");
        }
    }

    @Test
    void everyFormThatWantsADialogMountsIt() {
        for (Path page : pages) {
            String html = markup(page);
            if (!html.contains("data-confirm-")) continue;
            assertTrue(html.contains(":: confirmHost"), () -> shortName(page)
                    + " asks for confirmation but never mounts the dialog, so the action would silently do nothing");
        }
    }

    /**
     * A control is named by a label pointing at it, by an aria label, or by sitting inside a label of its
     * own - which is how a checkbox reads in this application. Hidden inputs need no name.
     */
    @Test
    void everyControlHasAName() {
        List<String> nameless = new ArrayList<>();
        for (Path page : pages) {
            String html = markup(page);
            Set<String> labelled = new LinkedHashSet<>();
            Matcher forAttribute = LABELLED_BY.matcher(html);
            while (forAttribute.find()) labelled.add(spoken(forAttribute.group(1)));
            Matcher control = CONTROL.matcher(html);
            while (control.find()) {
                String tag = control.group(0);
                if (tag.contains("type=\"hidden\"") || tag.contains("aria-label")) continue;
                Set<String> ids = new LinkedHashSet<>();
                Matcher own = Pattern.compile("(?:th:)?id=\"([^\"]+)\"").matcher(tag);
                while (own.find()) ids.add(spoken(own.group(1)));
                Matcher bound = Pattern.compile("th:field=\"\\*\\{(\\w+)\\}\"").matcher(tag);
                while (bound.find()) ids.add(bound.group(1));
                if (ids.stream().anyMatch(labelled::contains)) continue;
                // A control written inside its own label needs no "for" at all, which is how every
                // tick box in this application is built.
                if (html.lastIndexOf("<label", control.start()) > html.lastIndexOf("</label", control.start())) continue;
                nameless.add(shortName(page) + ": " + tag.replaceAll("\\s+", " "));
            }
        }
        assertTrue(nameless.isEmpty(), () -> "controls no screen reader can name:\n  " + String.join("\n  ", nameless));
    }

    /**
     * An id or a "for" that Thymeleaf assembles ('tpl-' + the row id) is the same name on both sides of
     * the pair once the expression is set aside, so both are compared with the expression blanked out.
     */
    private static String spoken(String value) {
        return EXPRESSION.matcher(value).replaceAll(" ").replaceAll("\\s+", " ").replace("'", "").trim();
    }

    private static String read(Path page) {
        try {
            return Files.readString(page);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String shortName(Path page) {
        return TEMPLATES.relativize(page).toString().replace(".html", "");
    }
}
