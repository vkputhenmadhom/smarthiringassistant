package org.vinod.sha.resumeparser.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.vinod.sha.resumeparser.dto.CandidateProfile;
import org.vinod.sha.resumeparser.security.AuthenticatedUser;
import org.vinod.sha.resumeparser.service.CandidateProfileService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateProfileControllerTest {

    private final CandidateProfileService service = mock(CandidateProfileService.class);
    private final CandidateProfileController controller = new CandidateProfileController(service);

    @Test
    void onlyHrCanListCandidates() {
        assertEquals(403, controller.list(0, 20, null, null, user(7L, "JOB_SEEKER")).getStatusCode().value());
        verify(service, never()).list(anyInt(), anyInt(), any());
    }

    @Test
    void hrCanOpenAnyCandidateAndACandidateOnlyThemselves() {
        when(service.forUser(8L)).thenReturn(Optional.of(profile(8L)));

        assertEquals(200, controller.get(8L, user(1L, "RECRUITER")).getStatusCode().value());
        assertEquals(200, controller.get(8L, user(8L, "JOB_SEEKER")).getStatusCode().value());
        assertEquals(403, controller.get(8L, user(7L, "JOB_SEEKER")).getStatusCode().value());
    }

    @Test
    void aUserWithoutAResumeIsNotFound() {
        when(service.forUser(9L)).thenReturn(Optional.empty());

        assertEquals(404, controller.get(9L, user(1L, "ADMIN")).getStatusCode().value());
    }

    @Test
    void userIdsNarrowTheListForMatchRows() {
        when(service.forUsers(List.of(7L, 8L))).thenReturn(List.of(profile(7L), profile(8L)));

        assertEquals(List.of(profile(7L), profile(8L)), controller.list(0, 20, null, "7, 8,x,7", user(1L, "HIRING_MANAGER")).getBody());
    }

    private static UsernamePasswordAuthenticationToken user(Long id, String role) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(id, "u" + id), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static CandidateProfile profile(Long id) {
        return new CandidateProfile(id, "r-" + id, null, "Name " + id, null, null, null, List.of(), null, List.of(), List.of(),
                "COMPLETED", null, null);
    }
}
