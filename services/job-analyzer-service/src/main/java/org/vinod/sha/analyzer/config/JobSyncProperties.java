package org.vinod.sha.analyzer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * External job-board sync settings.
 *
 * Supported sources
 * ─────────────────
 *  JSearch (RapidAPI)  – aggregates LinkedIn, Indeed, Glassdoor, ZipRecruiter
 *    Sign up at https://rapidapi.com/letscrape-6bRBa3QguO5/api/jsearch
 *    Set JSEARCH_API_KEY env-var (or job-sync.jsearch.api-key in yaml)
 *
 *  Adzuna              – free job API (register at https://developer.adzuna.com)
 *    Set ADZUNA_APP_ID + ADZUNA_APP_KEY env-vars
 */
@Data
@ConfigurationProperties(prefix = "job-sync")
public class JobSyncProperties {

    /** Master switch – set to true (or env JOB_SYNC_ENABLED=true) to activate scheduled sync */
    private boolean enabled = false;

    /** Spring 6-field cron expression (sec min hour day month weekday). Default: daily at 00:30 UTC (06:00 IST) */
    private String cron = "0 30 0 * * *";

    /** Max total jobs to import per sync run (across all sources & keywords) */
    private int maxPerSync = 50;

    /**
     * Runs the primary (US/global) import. Turn off (env JOB_SYNC_GLOBAL_ENABLED=false) to import only the
     * India sources while the app serves the Indian market.
     */
    private boolean globalEnabled = true;
    /** Primary two-letter ISO country code (used as default for US/global searches) */
    private String country = "us";

    /** Search keywords for the primary (US/global) search */
    private List<String> keywords = List.of(
            "software engineer",
            "platform engineer",
            "backend engineer",
            "java developer",
            "devops engineer"
    );

    private JSearchConfig jsearch = new JSearchConfig();
    private AdzunaConfig  adzuna  = new AdzunaConfig();


    // ── JSearch (RapidAPI) ────────────────────────────────────────────────────

    @Data
    public static class JSearchConfig {
        private String apiKey  = "";
        private String baseUrl = "https://jsearch.p.rapidapi.com";
        /** posted within: all | today | 3days | week | month */
        private String datePosted = "week";
        /** Number of result pages to fetch per keyword (1 page ≈ 10 jobs) */
        private int numPages = 1;
    }

    // ── Adzuna ────────────────────────────────────────────────────────────────

    @Data
    public static class AdzunaConfig {
        private String appId  = "";
        private String appKey = "";
        private String baseUrl = "https://api.adzuna.com/v1/api";
        private int resultsPerPage = 20;
    }

    // ── Job feeds ───────────────────────────────────────────────────────────────
    // The feeds candidates browse (one portal tab each) are configured in job-feeds.yml, not in code.
    // docker-compose mounts that file, so editing it and restarting the service applies a change.

    /** Imports the configured feeds; env JOB_SYNC_INDIA_ENABLED */
    private boolean feedsEnabled = false;

    /**
     * Imports made before feeds existed are tagged only PROVIDER_MARKET (e.g. JSEARCH_IN); they move to this
     * feed at startup. Blank leaves them as they are.
     */
    private String legacyFeed = "";

    /** In order: the portal shows the tabs in this order, and a posting found by two feeds goes to the first. */
    private List<Feed> feeds = List.of();

    @Data
    public static class Feed {
        /** Short id, used in the source tag (PROVIDER_KEY_MARKET) and the portal URL; letters only */
        private String key;
        /** Portal tab title */
        private String label;
        /** Shown when the tab has no jobs */
        private String emptyMessage = "No jobs yet.";
        /** Job market the feed belongs to (see job-market.region); its imports are tagged with it */
        private String market = "IN";
        /** The tab lists jobs posted in the app rather than imports; such a feed has no sources */
        private boolean postedInApp = false;
        /** Max new jobs per run, across the feed's sources */
        private int maxPerSync = 30;
        /** Imported postings older than this many days are closed (boards remove ads); null keeps them open */
        private Integer maxAgeDays;
        private List<FeedSource> sources = List.of();
        /** Keep a job only if its title or description matches one of these (regex, case-insensitive); empty keeps all */
        private List<String> requireAny = List.of();
        /** Drop a job whose title or description matches any of these (regex, case-insensitive) */
        private List<String> rejectAny = List.of();
    }

    @Data
    public static class FeedSource {
        /** adzuna or jsearch */
        private String provider;
        /** Two-letter country: Adzuna's country path (required); JSearch's country filter (optional) */
        private String country;
        /** One API request per keyword per run; JSearch's free plan is 200 requests a month */
        private List<String> keywords = List.of();
        /** Extra query parameters passed as they are, e.g. category: it-jobs (Adzuna), work_from_home: true (JSearch) */
        private Map<String, String> params = Map.of();
    }
}

