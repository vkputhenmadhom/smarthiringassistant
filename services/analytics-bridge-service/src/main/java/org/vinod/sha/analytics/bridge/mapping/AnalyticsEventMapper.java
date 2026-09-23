package org.vinod.sha.analytics.bridge.mapping;

import org.springframework.stereotype.Component;
import org.vinod.shared.analytics.events.AnalyticsEvent;
import org.vinod.shared.analytics.events.AnalyticsEventFactory;
import org.vinod.shared.analytics.events.AnalyticsEventType;
import org.vinod.shared.events.CandidateMatchedEvent;
import org.vinod.shared.events.ScreeningCompletedEvent;

@Component
public class AnalyticsEventMapper {

    public AnalyticsEvent fromCandidateMatched(CandidateMatchedEvent event) {
        AnalyticsEvent analyticsEvent = AnalyticsEventFactory.baseEvent(
                AnalyticsEventType.CANDIDATE_MATCHED,
                "candidate-matcher-service"
        );
        analyticsEvent.setCandidateId(stringValue(event.getCandidateId()));
        analyticsEvent.setJobId(event.getJobId());
        analyticsEvent.setStatus(event.getStatus());
        analyticsEvent.setCorrelationId(event.getMatchId());
        analyticsEvent.setAttributes(AnalyticsEventFactory.attrs(
                "matchScore", event.getMatchScore(),
                "skillMatchPercentage", event.getSkillMatchPercentage(),
                "experienceMatchPercentage", event.getExperienceMatchPercentage(),
                "matchedAt", event.getMatchedAt()
        ));
        return analyticsEvent;
    }

    public AnalyticsEvent fromScreeningCompleted(ScreeningCompletedEvent event) {
        AnalyticsEvent analyticsEvent = AnalyticsEventFactory.baseEvent(
                AnalyticsEventType.SCREENING_COMPLETED,
                "screening-bot-service"
        );
        analyticsEvent.setCandidateId(stringValue(event.getCandidateId()));
        analyticsEvent.setJobId(event.getJobId());
        analyticsEvent.setSessionId(event.getSessionId());
        analyticsEvent.setCorrelationId(event.getSessionId());
        analyticsEvent.setStatus("COMPLETED");
        analyticsEvent.setStage("final");
        analyticsEvent.setAttributes(AnalyticsEventFactory.attrs(
                "decision", event.getDecision(),
                "finalScore", event.getFinalScore(),
                "failureReasons", event.getFailureReasons(),
                "completedAt", event.getCompletedAt()
        ));
        return analyticsEvent;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
