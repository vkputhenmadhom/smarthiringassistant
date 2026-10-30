package org.vinod.sha.resumeparser.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.vinod.sha.resumeparser.dto.CandidateProfile;
import org.vinod.sha.resumeparser.security.AuthenticatedUser;
import org.vinod.sha.resumeparser.service.CandidateProfileService;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Candidate profiles for HR (any candidate) and for candidates themselves (their own only). Parsed names,
 * emails and phones are personal data, so every read checks the caller's role from the verified JWT.
 */
@RestController
@RequestMapping("candidates")
public class CandidateProfileController {

    /** auth-service roles that may see every candidate. */
    static final Set<String> HR_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_RECRUITER", "ROLE_HIRING_MANAGER");

    private final CandidateProfileService candidates;

    public CandidateProfileController(CandidateProfileService candidates) {
        this.candidates = candidates;
    }

    /** HR only. {@code userIds} (comma-separated) narrows the list to those users, unpaged. */
    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String search,
                                  @RequestParam(required = false) String userIds,
                                  Authentication authentication) {
        if (!isHr(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (userIds != null) {
            List<Long> ids = Arrays.stream(userIds.split(",")).map(String::trim).filter(s -> s.matches("\\d+"))
                    .map(Long::valueOf).distinct().toList();
            return ResponseEntity.ok(candidates.forUsers(ids));
        }
        Page<CandidateProfile> result = candidates.list(page, size, search);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", result.getContent());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        return ResponseEntity.ok(body);
    }

    /** HR, or the candidate asking about themselves. 404 when the user has no resume yet. */
    @GetMapping("{userId}")
    public ResponseEntity<CandidateProfile> get(@PathVariable Long userId, Authentication authentication) {
        if (!isHr(authentication) && !Objects.equals(callerId(authentication), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return candidates.forUser(userId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    static boolean isHr(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).anyMatch(HR_AUTHORITIES::contains);
    }

    private static Long callerId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user ? user.id() : null;
    }
}
