package org.vinod.sha.resumeparser.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResumeNameExtractionTest {

    @Test
    void skipsATaglineThatPdfExtractionPutsFirst() {
        String text = """
                Relocated from US (Dallas, TX) | Open to Kochi / Trivandrum / Bengaluru / Remote
                Vinod Puthenmadhom
                vinod@example.com | +91 98765 43210
                """;
        assertEquals("Vinod Puthenmadhom", ResumeParserService.extractFullName(text));
    }

    @Test
    void acceptsInitialsAndCapitals() {
        assertEquals("Jane A. Doe", ResumeParserService.extractFullName("Jane A. Doe\nSenior Engineer"));
        assertEquals("RAVI KUMAR", ResumeParserService.extractFullName("\n  RAVI   KUMAR \nPune"));
    }

    @Test
    void skipsHeadingsTitlesAndContactLines() {
        assertEquals("Asha Menon", ResumeParserService.extractFullName(
                "Curriculum Vitae\nSenior Software Engineer\nasha@example.com\n+91 99999 88888\nAsha Menon"));
    }

    @Test
    void blankWhenNoLineLooksLikeAName() {
        assertEquals("", ResumeParserService.extractFullName("Java, Spring, AWS\n10+ years | Remote"));
    }
}
