package org.vinod.sha.analyzer.service;

import org.junit.jupiter.api.Test;
import org.vinod.sha.analyzer.config.JobSyncProperties.Feed;
import org.vinod.sha.analyzer.entity.JobAnalysis;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the shipped job-feeds.yml; the remote cases come from real JSearch results. */
class JobFeedsTest {

    private final JobFeeds feeds = ConfiguredFeeds.load();
    private final Feed remote = feeds.find("remote").orElseThrow();

    @Test
    void feedsAreTabsInConfigOrder() {
        assertEquals(List.of("tech", "remote", "govt", "posted"), feeds.all().stream().map(Feed::getKey).toList());
        assertEquals("ADZUNA_TECH_IN", JobFeeds.sourceTag("adzuna", feeds.find("TECH").orElseThrow()));
        assertEquals("JSEARCH_IN", JobFeeds.externalIdPrefix("jsearch", remote), "shared by feeds so a posting is imported once");
    }

    @Test
    void eachJobIsInOneFeed() {
        Feed tech = feeds.find("tech").orElseThrow();
        Feed posted = feeds.find("posted").orElseThrow();

        assertTrue(JobFeeds.includes(tech, JobAnalysis.builder().source("JSEARCH_TECH_IN").build()));
        assertFalse(JobFeeds.includes(tech, JobAnalysis.builder().source("ADZUNA_GOVT_IN").build()));
        assertTrue(JobFeeds.includes(posted, JobAnalysis.builder().build()));
        assertFalse(JobFeeds.includes(posted, JobAnalysis.builder().source("JSEARCH_TECH_IN").build()));
    }

    @Test
    void remoteKeepsJobsOpenWorldwideOrToIndia() {
        assertTrue(feeds.accepts(remote, "Backend Security Engineer – Remote (Worldwide)", "Join our distributed team."));
        assertTrue(feeds.accepts(remote, "Data Engineer", "Fully remote. We hire anywhere in the world."));
        assertTrue(feeds.accepts(remote, "Staff Engineer", "Candidates in India or elsewhere in APAC are welcome."));
        assertTrue(feeds.accepts(remote, "Engineer", "Join us from anywhere in the world; let us know your time zone."));
    }

    @Test
    void remoteDropsUsOnlyAndOtherRegionOnlyJobs() {
        assertFalse(feeds.accepts(remote, "Principal Software Engineer / Remote USA", "Work from anywhere."));
        assertFalse(feeds.accepts(remote, "Engineer", "Worldwide team. Must be authorized to work in the United States."));
        assertFalse(feeds.accepts(remote, "Engineer", "Global company; US citizenship required."));
        assertFalse(feeds.accepts(remote, "Engineer", "Work from anywhere in the US."));
        assertFalse(feeds.accepts(remote, "Site Reliability Engineer", "REMOTE (US Citizenship required), worldwide product"));
        assertFalse(feeds.accepts(remote, "Engineer", "Remote worldwide, but you must reside in Canada."));
        assertFalse(feeds.accepts(remote, "Senior .NET Software Engineer", "Remote role building AI tools."), "says nothing about where");
    }

    @Test
    void govtKeepsGovernmentPostingsNotGovernanceRoles() {
        Feed govt = feeds.find("govt").orElseThrow();

        assertTrue(feeds.accepts(govt, "Government Job:- DRDO Scientist C/D/E/F Recruitment", ""));
        assertTrue(feeds.accepts(govt, "Probationary Officer", "IBPS PO recruitment 2026"));
        assertFalse(feeds.accepts(govt, "Data Governance Architect", "Own data governance for our clients"));
        assertFalse(feeds.accepts(govt, "Deck hand", "Set sail with our hal team"), "acronyms are case-sensitive");
    }

    @Test
    void feedsWithoutRulesKeepEverything() {
        assertTrue(feeds.accepts(feeds.find("tech").orElseThrow(), "Clerk", "Remote USA only"));
    }
}
