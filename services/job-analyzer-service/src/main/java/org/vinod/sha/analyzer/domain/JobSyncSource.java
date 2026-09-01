package org.vinod.sha.analyzer.domain;

/**
 * Java 21 Sealed Class: Represents job posting source (external portal or internal).
 * Restricts implementations to defined constants only, ensuring type safety.
 */
public sealed interface JobSyncSource permits JobSyncSource.INTERNAL, JobSyncSource.JSEARCH, 
                                               JobSyncSource.ADZUNA, JobSyncSource.JSEARCH_IN,
                                               JobSyncSource.ADZUNA_IN {
    String tag();
    String displayName();

    /**
     * Job created internally via API
     */
    record INTERNAL(String tag, String displayName) implements JobSyncSource {
        public INTERNAL() {
            this("INTERNAL", "Internal Portal");
        }
    }

    /**
     * Job from JSearch (RapidAPI aggregator: LinkedIn, Indeed, Glassdoor, ZipRecruiter)
     */
    record JSEARCH(String tag, String displayName) implements JobSyncSource {
        public JSEARCH() {
            this("JSEARCH", "JSearch (Global)");
        }
    }

    /**
     * Job from Adzuna (free API, covers US/EU)
     */
    record ADZUNA(String tag, String displayName) implements JobSyncSource {
        public ADZUNA() {
            this("ADZUNA", "Adzuna (US)");
        }
    }

    /**
     * Job from JSearch India search
     */
    record JSEARCH_IN(String tag, String displayName) implements JobSyncSource {
        public JSEARCH_IN() {
            this("JSEARCH_IN", "JSearch (India)");
        }
    }

    /**
     * Job from Adzuna India API
     */
    record ADZUNA_IN(String tag, String displayName) implements JobSyncSource {
        public ADZUNA_IN() {
            this("ADZUNA_IN", "Adzuna (India)");
        }
    }

    /**
     * Factory method: Parse string tag to JobSyncSource
     */
    static JobSyncSource from(String tag) {
        return switch (tag == null ? "" : tag.toUpperCase()) {
            case "JSEARCH" -> new JSEARCH();
            case "ADZUNA" -> new ADZUNA();
            case "JSEARCH_IN" -> new JSEARCH_IN();
            case "ADZUNA_IN" -> new ADZUNA_IN();
            default -> new INTERNAL();
        };
    }
}
