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

    /** Spring 6-field cron expression (sec min hour day month weekday). Default: every 6 hours */
    private String cron = "0 0 */6 * * *";

    /** Max total jobs to import per sync run (across all sources & keywords) */
    private int maxPerSync = 50;

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

    // ── India (Adzuna /in/ + JSearch location=India) ──────────────────────────
    //
    // India government jobs are NOT on NCS (no public API).
    // The sources below DO include PSU / central-govt listings:
    //   • Adzuna India  (/in/)  – aggregates Naukri, LinkedIn India, Indeed.co.in
    //   • JSearch India         – aggregates LinkedIn India, Indeed.co.in
    //
    // Typical government/PSU employers found: DRDO, ISRO, NTPC, BHEL, ONGC,
    //   Indian Railways, IBPS, SBI, HAL, BEL, NLC India, SAIL, GAIL …
    // ─────────────────────────────────────────────────────────────────────────

    @Data
    public static class IndiaConfig {
        /** Set to true to activate India-specific sync (reuses the same API keys) */
        private boolean enabled = false;

        /** Max jobs to import per India sync run */
        private int maxPerSync = 30;

        /**
         * Keywords tuned for India government / PSU / central-government jobs.
         * Works with both Adzuna India and JSearch India.
         */
        private List<String> keywords = List.of(
                "government engineer India",
                "PSU jobs India",
                "DRDO scientist",
                "ISRO engineer",
                "NTPC engineer",
                "BHEL engineer",
                "Indian Railways engineer",
                "IBPS bank jobs",
                "central government jobs"
        );
    }
}

