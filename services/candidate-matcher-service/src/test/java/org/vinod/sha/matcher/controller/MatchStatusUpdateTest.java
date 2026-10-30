package org.vinod.sha.matcher.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.vinod.sha.matcher.dto.MatchResponse;
import org.vinod.sha.matcher.dto.MatchUpdateRequest;
import org.vinod.sha.matcher.entity.CandidateMatch;
import org.vinod.sha.matcher.entity.MatchStatus;
import org.vinod.sha.matcher.service.CandidateMatcherService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchStatusUpdateTest {

    private final CandidateMatcherService service = mock(CandidateMatcherService.class);
    private final CandidateMatcherController controller = new CandidateMatcherController(service);

    @Test
    void shortlistingAMatchReturnsItWithTheNewStatusAndSkills() {
        CandidateMatch shortlisted = match(MatchStatus.SHORTLISTED);
        when(service.findMatch("m-1")).thenReturn(Optional.of(match(MatchStatus.MATCHED)));
        when(service.updateMatchStatus("m-1", MatchStatus.SHORTLISTED, null)).thenReturn(shortlisted);

        ResponseEntity<?> response = controller.updateMatchStatus("m-1", new MatchUpdateRequest("shortlisted", null));

        assertEquals(200, response.getStatusCode().value());
        MatchResponse body = (MatchResponse) response.getBody();
        assertEquals("SHORTLISTED", body.getStatus());
        assertEquals(List.of("Java"), body.getMatchedSkills());
        assertEquals(List.of("Kubernetes"), body.getMissingSkills());
    }

    @Test
    void anUnknownStatusIsABadRequestNotAServerError() {
        ResponseEntity<?> response = controller.updateMatchStatus("m-1", new MatchUpdateRequest("ACCEPTED", null));

        assertEquals(400, response.getStatusCode().value());
        assertTrue(String.valueOf(response.getBody()).contains("SHORTLISTED"));
        verify(service, never()).updateMatchStatus(any(), any(), any());
    }

    @Test
    void aMissingMatchIsNotFound() {
        when(service.findMatch("gone")).thenReturn(Optional.empty());

        assertEquals(404, controller.updateMatchStatus("gone", new MatchUpdateRequest("REJECTED", null)).getStatusCode().value());
        verify(service, never()).updateMatchStatus(any(), any(), any());
    }

    @Test
    void bestScoresIgnoreJunkIdsAndAskOncePerCandidate() {
        when(service.bestScores(List.of(7L, 8L))).thenReturn(java.util.Map.of(7L, 82.0));

        assertEquals(java.util.Map.of(7L, 82.0), controller.bestScores("7, 8,abc,7").getBody());
    }

    private static CandidateMatch match(MatchStatus status) {
        return CandidateMatch.builder().matchId("m-1").candidateId(7L).jobId("job-1").overallScore(82.0)
                .skillMatchPercentage(50.0).experienceMatchPercentage(100.0).locationMatchPercentage(100.0)
                .status(status).matchedSkills(List.of("Java")).missingSkills(List.of("Kubernetes"))
                .createdAt(LocalDateTime.of(2026, 10, 29, 12, 0)).build();
    }
}
