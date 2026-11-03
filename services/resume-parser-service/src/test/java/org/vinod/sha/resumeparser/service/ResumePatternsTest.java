package org.vinod.sha.resumeparser.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** The email and experience patterns read user-supplied text, so they must stay fast on hostile input. */
class ResumePatternsTest {

    @Test
    void findsEmailAddresses() {
        assertEquals("asha.menon+jobs@mail.example.co.in",
                ResumeParserService.extractEmail("Asha Menon | asha.menon+jobs@mail.example.co.in | +91 90000 00000"));
        assertEquals("", ResumeParserService.extractEmail("no address here @ all"));
    }

    @Test
    void findsYearsOfExperience() {
        assertEquals(12.0, ResumeParserService.extractTotalExperience("Backend lead with 12 years of experience in Java."));
        assertEquals(0.0, ResumeParserService.extractTotalExperience("Founded 2001; 250 years experience combined"),
                "three-digit numbers are not someone's years");
    }

    @Test
    void longRepeatedInputIsScannedQuickly() {
        String percents = "%".repeat(200_000);
        String digits = "9".repeat(200_000);
        String spaces = "1" + " ".repeat(200_000) + "x";
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertEquals("", ResumeParserService.extractEmail(percents));
            assertEquals(0.0, ResumeParserService.extractTotalExperience(digits));
            assertEquals(0.0, ResumeParserService.extractTotalExperience(spaces));
        });
    }
}
