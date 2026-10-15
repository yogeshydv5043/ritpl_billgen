package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The sanitizer is the only thing between an administrator's rich text and a visitor's browser, so most of
 * these cases are about what must not survive: a script, an event handler, a URL scheme that runs code.
 * What must survive is the short list of tags a policy is actually written with - a sanitizer that ate
 * every paragraph would pass none of these tests and still be wrong.
 */
class RichTextSanitizerTest {

    @Test
    void keepsTheMarkupAPolicyIsWrittenWith() {
        String html = "<h2>Refunds</h2><p>We refund <strong>within 7 days</strong>.</p>"
                + "<ul><li>Monthly plans</li></ul><blockquote>Note</blockquote>"
                + "<p>Contact <a href=\"mailto:support@example.com\">support</a>.</p>";
        String clean = RichTextSanitizer.sanitize(html);
        assertTrue(clean.startsWith("<h2>Refunds</h2><p>"));
        assertTrue(clean.contains("<ul><li>Monthly plans</li></ul>"));
        assertTrue(clean.contains("<blockquote>Note</blockquote>"));
        assertTrue(clean.contains("href=\"mailto:support@example.com\""));
        assertFalse(clean.contains("<h1"), "a policy cannot change the page's own heading level");
    }

    @Test
    void dropsScriptsTogetherWithTheirContents() {
        String clean = RichTextSanitizer.sanitize("<p>Read this</p><script>alert(1)</script>");
        assertEquals("<p>Read this</p>", clean);
        assertFalse(clean.contains("alert"), "the payload is gone rather than merely disabled");
        assertFalse(RichTextSanitizer.sanitize("<style>body{display:none}</style><p>ok</p>").contains("display"));
        assertFalse(RichTextSanitizer.sanitize("<iframe src=\"https://evil.test\"></iframe>ok").contains("evil"));
    }

    @Test
    void removesAttributesRatherThanJudgingThem() {
        assertEquals("<p>text</p>", RichTextSanitizer.sanitize("<p onclick=\"steal()\" style=\"color:red\">text</p>"));
        assertEquals("x", RichTextSanitizer.sanitize("<img src=x onerror=alert(1)>x"));
        assertEquals("<a href=\"/a\">go</a>", RichTextSanitizer.sanitize("<a href=\"/a\" target=\"_blank\">go</a>")
                .replace(" rel=\"noopener noreferrer\"", ""), "only the link target is kept");
    }

    @Test
    void refusesLinkSchemesThatRunCodeOrReadTheMachine() {
        assertEquals("<a>click</a>", RichTextSanitizer.sanitize("<a href=\"javascript:alert(1)\">click</a>"));
        assertEquals("<a>x</a>", RichTextSanitizer.sanitize("<a href=\"data:text/html;base64,PHNjcmlwdD4=\">x</a>"));
        assertEquals("<a>x</a>", RichTextSanitizer.sanitize("<a href=\"vbscript:msgbox(1)\">x</a>"));
        assertEquals("<a>x</a>", RichTextSanitizer.sanitize("<a>x</a>"), "a link with no target is a link to nowhere");
    }

    @Test
    void keepsTargetsADocumentCanExplain() {
        assertTrue(RichTextSanitizer.sanitize("<a href=\"/docs/x.pdf\">x</a>").contains("href=\"/docs/x.pdf\""));
        assertTrue(RichTextSanitizer.sanitize("<a href=\"#terms\">x</a>").contains("href=\"#terms\""));
        assertTrue(RichTextSanitizer.sanitize("<a href=\"https://example.com/a\">x</a>")
                .contains("rel=\"noopener noreferrer\""), "an outgoing link is not allowed to reach back");
    }

    @Test
    void treatsBrokenMarkupAsTextRatherThanAsStructure() {
        assertEquals("<p>a &lt; b</p>", RichTextSanitizer.sanitize("<p>a < b</p>"));
        assertEquals("<p>1 &lt; 2 &amp; 3 &gt; 2</p>", RichTextSanitizer.sanitize("<p>1 < 2 & 3 > 2</p>"));
        assertEquals("", RichTextSanitizer.sanitize("<div>"));
        assertEquals("", RichTextSanitizer.sanitize(null));
        assertEquals("", RichTextSanitizer.sanitize("   "));
    }

    @Test
    void alreadyEscapedTextIsNotDecodedIntoMarkup() {
        assertEquals("<p>a &amp;amp; b &amp;lt;script&amp;gt;</p>",
                RichTextSanitizer.sanitize("<p>a &amp; b &lt;script&gt;</p>"));
        assertEquals("<p>&amp;lt;img src=x onerror=alert(1)&amp;gt;</p>",
                RichTextSanitizer.sanitize("<p>&lt;img src=x onerror=alert(1)&gt;</p>"),
                "escaped markup stays a sentence about markup, and never becomes a tag");
    }

    @Test
    void unknownTagsLoseTheTagAndKeepTheWords() {
        assertEquals("saletext", RichTextSanitizer.sanitize("<marquee>sale</marquee><section>text</section>"));
        assertEquals("<p>deep</p>", RichTextSanitizer.sanitize("<p><unknown>deep</unknown></p>"));
    }

    @Test
    void voidAndSelfClosingElementsCloseThemselves() {
        assertEquals("<p>a<br>b</p>", RichTextSanitizer.sanitize("<p>a<br>b</p>"));
        assertEquals("<p>a<br>b</p>", RichTextSanitizer.sanitize("<p>a<br/>b</p>"));
        assertEquals("<p>a<br>b</p>", RichTextSanitizer.sanitize("<p>a<br />b</p>"));
    }
}
