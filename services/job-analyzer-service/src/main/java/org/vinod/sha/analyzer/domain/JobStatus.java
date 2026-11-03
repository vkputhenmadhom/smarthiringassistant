package org.vinod.sha.analyzer.domain;

/**
 * Java 21 Sealed Class: Represents job posting status.
 * Restricts implementations to defined constants only, ensuring type safety.
 */
public sealed interface JobStatus permits JobStatus.DRAFT, JobStatus.OPEN, JobStatus.CLOSED, JobStatus.ANALYZED {
    String value();

    /**
     * Job analysis in progress; not yet posted
     */
    record DRAFT(String value) implements JobStatus {
        public DRAFT() {
            this("DRAFT");
        }
    }

    /**
     * Job posting is active and accepting applications
     */
    record OPEN(String value) implements JobStatus {
        public OPEN() {
            this("OPEN");
        }
    }

    /**
     * Job posting has closed; no longer accepting applications
     */
    record CLOSED(String value) implements JobStatus {
        public CLOSED() {
            this("CLOSED");
        }
    }

    /**
     * Job analysis completed; ready for posting
     */
    record ANALYZED(String value) implements JobStatus {
        public ANALYZED() {
            this("ANALYZED");
        }
    }

    /**
     * Factory method: Parse string to JobStatus
     */
    static JobStatus from(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "OPEN" -> new OPEN();
            case "CLOSED" -> new CLOSED();
            case "ANALYZED" -> new ANALYZED();
            default -> new DRAFT();
        };
    }
}
