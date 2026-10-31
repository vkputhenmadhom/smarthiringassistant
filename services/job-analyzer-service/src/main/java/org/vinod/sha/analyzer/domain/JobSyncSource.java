package org.vinod.sha.analyzer.domain;

/**
 * Java 21 Sealed Class: Represents job posting source (external portal or internal).
 * Restricts implementations to defined constants only, ensuring type safety.
 */
public sealed interface JobSyncSource permits JobSyncSource.INTERNAL, JobSyncSource.JSEARCH,
                                               JobSyncSource.ADZUNA, JobSyncSource.India {
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
     * Job from one of the India feeds: tech, government, or remote jobs open to India
     */
    record India(String tag, String displayName) implements JobSyncSource {}

    /**
     * Factory method: Parse string tag to JobSyncSource
     */
    static JobSyncSource from(String tag) {
        return switch (tag == null ? "" : tag.toUpperCase()) {
            case "JSEARCH" -> new JSEARCH();
            case "ADZUNA" -> new ADZUNA();
            case "JSEARCH_TECH_IN" -> new India("JSEARCH_TECH_IN", "JSearch (India tech)");
            case "ADZUNA_TECH_IN" -> new India("ADZUNA_TECH_IN", "Adzuna (India tech)");
            case "JSEARCH_GOVT_IN" -> new India("JSEARCH_GOVT_IN", "JSearch (India government)");
            case "ADZUNA_GOVT_IN" -> new India("ADZUNA_GOVT_IN", "Adzuna (India government)");
            case "JSEARCH_REMOTE_IN" -> new India("JSEARCH_REMOTE_IN", "JSearch (remote, open to India)");
            default -> new INTERNAL();
        };
    }
}
