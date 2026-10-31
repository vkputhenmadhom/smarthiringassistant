package org.vinod.sha.analyzer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

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

    /** India-specific sync configuration (government + private sector) */
    private IndiaConfig india = new IndiaConfig();

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

    // ── India (Adzuna /in/ + JSearch country=in) and remote jobs open to India ──
    //
    // Three feeds, each with its own source tag (all ending in _IN, so all are in the India market):
    //   tech    ADZUNA_TECH_IN / JSEARCH_TECH_IN – software jobs in India
    //   govt    ADZUNA_GOVT_IN                   – government / PSU jobs in India
    //   remote  JSEARCH_REMOTE_IN                – remote jobs anywhere that can be done from India
    //
    // JSearch's free plan allows 200 requests a month, so it gets a few searches per run; Adzuna
    // (250 a day) gets the rest. With one run a day that is about 4 JSearch and 12 Adzuna requests.
    // ─────────────────────────────────────────────────────────────────────────

    @Data
    public static class IndiaConfig {
        /** Set to true to activate the India and remote imports (reuses the same API keys) */
        private boolean enabled = false;

        /** Max new jobs per feed per run */
        private int maxPerSync = 30;

        /** Software job searches on Adzuna India (restricted to its IT category) */
        private List<String> keywords = List.of(
                "software engineer",
                "java developer",
                "python developer",
                "full stack developer",
                "frontend developer",
                "devops engineer",
                "data engineer"
        );

        /** Software job searches on JSearch India; keep this short, each one is a request from the monthly quota */
        private List<String> jsearchKeywords = List.of(
                "software engineer",
                "software developer"
        );

        /** Government / PSU job searches on Adzuna India */
        private List<String> govtKeywords = List.of(
                "government jobs",
                "PSU recruitment",
                "DRDO ISRO scientist",
                "bank PO clerk recruitment",
                "railway recruitment"
        );

        private RemoteConfig remote = new RemoteConfig();
    }

    /** Remote jobs from anywhere that say they are open worldwide, to India or APAC (JSearch, remote only). */
    @Data
    public static class RemoteConfig {
        private boolean enabled = true;

        /** Max new jobs per run */
        private int maxPerSync = 20;

        /** Each keyword is one JSearch request */
        private List<String> keywords = List.of(
                "remote software engineer worldwide",
                "remote developer APAC India"
        );
    }
}

