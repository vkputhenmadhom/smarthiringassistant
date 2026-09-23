package org.vinod.shared.analytics.events;

public enum AnalyticsEventType {
    CANDIDATE_REGISTERED("candidate.registered"),
    CANDIDATE_LOGIN("candidate.login"),
    RESUME_UPLOADED("resume.uploaded"),
    RESUME_PARSED("resume.parsed"),
    RESUME_PARSE_FAILED("resume.parse.failed"),
    JOB_PUBLISHED("job.published"),
    CANDIDATE_MATCHED("candidate.matched"),
    SCREENING_STARTED("screening.started"),
    SCREENING_STAGE_COMPLETED("screening.stage.completed"),
    SCREENING_COMPLETED("screening.completed"),
    INTERVIEW_SCHEDULED("interview.scheduled"),
    NOTIFICATION_SENT("notification.sent");

    private final String value;

    AnalyticsEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
