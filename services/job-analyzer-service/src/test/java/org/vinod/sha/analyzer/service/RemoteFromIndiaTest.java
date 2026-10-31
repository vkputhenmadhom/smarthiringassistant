package org.vinod.sha.analyzer.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cases taken from real JSearch remote results. */
class RemoteFromIndiaTest {

    @Test
    void openWorldwideOrToIndiaIsKept() {
        assertTrue(RemoteFromIndia.isOpenToIndia("Backend Security Engineer – Remote (Worldwide)", "Join our distributed team."));
        assertTrue(RemoteFromIndia.isOpenToIndia("Data Engineer", "Fully remote. We hire anywhere in the world."));
        assertTrue(RemoteFromIndia.isOpenToIndia("Staff Engineer", "Candidates in India or elsewhere in APAC are welcome."));
    }

    @Test
    void usOnlyIsSkipped() {
        assertFalse(RemoteFromIndia.isOpenToIndia("Principal Software Engineer / Remote USA", "Work from anywhere."));
        assertFalse(RemoteFromIndia.isOpenToIndia("Engineer", "Worldwide team. Must be authorized to work in the United States."));
        assertFalse(RemoteFromIndia.isOpenToIndia("Engineer", "Global company; US citizenship required."));
        assertFalse(RemoteFromIndia.isOpenToIndia("Engineer", "Work from anywhere in the US."));
        assertFalse(RemoteFromIndia.isOpenToIndia("Site Reliability Engineer", "REMOTE (US Citizenship required), worldwide product"));
        assertFalse(RemoteFromIndia.isOpenToIndia("Engineer", "Remote worldwide, but you must reside in Canada."));
    }

    @Test
    void sayingNothingAboutWhereIsSkipped() {
        assertFalse(RemoteFromIndia.isOpenToIndia("Senior .NET Software Engineer", "Remote role building AI tools."));
    }

    @Test
    void lowercaseUsIsJustAWord() {
        assertTrue(RemoteFromIndia.isOpenToIndia("Engineer", "Join us from anywhere in the world; let us know your time zone."));
    }
}
