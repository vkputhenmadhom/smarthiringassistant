package org.vinod.sha.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.vinod.sha.auth.dto.AuthResponse;
import org.vinod.sha.auth.dto.LoginRequest;
import org.vinod.sha.auth.dto.RegisterRequest;
import org.vinod.sha.auth.entity.ApprovalStatus;
import org.vinod.sha.auth.entity.User;
import org.vinod.sha.auth.entity.UserRole;
import org.vinod.sha.auth.exception.AccountNotApprovedException;
import org.vinod.sha.auth.repository.UserRepository;
import org.vinod.sha.auth.security.JwtTokenProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceApprovalTest {

    private static final String BOOTSTRAP_ADMIN = "owner@example.com";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
    private final UserEventPublisher eventPublisher = mock(UserEventPublisher.class);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenProvider, eventPublisher,
                " Owner@Example.com , ");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(tokenProvider.generateToken(any(User.class))).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(any(User.class))).thenReturn("refresh-token");
    }

    @Test
    void candidateRegistrationIsApprovedAndReceivesTokens() {
        AuthResponse response = authService.register(registerRequest("JOB_SEEKER"));

        assertEquals("APPROVED", response.getApprovalStatus());
        assertEquals("access-token", response.getAccessToken());
    }

    @Test
    void hrRegistrationIsPendingAndReceivesNoTokens() {
        AuthResponse response = authService.register(registerRequest("ADMIN"));

        assertEquals("PENDING", response.getApprovalStatus());
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        verify(tokenProvider, never()).generateToken(any(User.class));
    }

    @Test
    void pendingAccountCannotLogIn() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(UserRole.RECRUITER, ApprovalStatus.PENDING)));

        AccountNotApprovedException e = assertThrows(AccountNotApprovedException.class,
                () -> authService.login(new LoginRequest("alice", "password")));

        assertEquals(ApprovalStatus.PENDING, e.getStatus());
    }

    @Test
    void rejectedAccountCannotRefreshAnOldToken() {
        when(tokenProvider.validateToken("old-refresh")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("old-refresh")).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(UserRole.RECRUITER, ApprovalStatus.REJECTED)));

        AccountNotApprovedException e = assertThrows(AccountNotApprovedException.class,
                () -> authService.refreshToken("old-refresh"));

        assertEquals(ApprovalStatus.REJECTED, e.getStatus());
    }

    @Test
    void newHrPortalOAuthUserIsPendingRecruiter() {
        when(userRepository.findByEmail("new.hr@example.com")).thenReturn(Optional.empty());

        AuthResponse response = authService.handleOAuth2Login(oauthUser("new.hr@example.com", true), "google", "hr-admin");

        assertEquals("PENDING", response.getApprovalStatus());
        assertEquals("RECRUITER", response.getUser().getRole());
        assertNull(response.getAccessToken());
    }

    @Test
    void existingCandidateUsingHrPortalIsNotPromoted() {
        User candidate = user(UserRole.JOB_SEEKER, ApprovalStatus.APPROVED);
        when(userRepository.existsByEmail(candidate.getEmail())).thenReturn(true);
        when(userRepository.findByEmail(candidate.getEmail())).thenReturn(Optional.of(candidate));

        AuthResponse response = authService.handleOAuth2Login(oauthUser(candidate.getEmail(), true), "google", "hr-admin");

        assertEquals("JOB_SEEKER", response.getUser().getRole());
    }

    @Test
    void bootstrapAdminWithVerifiedEmailIsApprovedAdmin() {
        when(userRepository.findByEmail(BOOTSTRAP_ADMIN)).thenReturn(Optional.empty());

        AuthResponse response = authService.handleOAuth2Login(oauthUser(BOOTSTRAP_ADMIN, true), "google", "hr-admin");

        assertEquals("ADMIN", response.getUser().getRole());
        assertEquals("APPROVED", response.getApprovalStatus());
        assertNotNull(response.getAccessToken());
    }

    @Test
    void bootstrapEmailIsIgnoredWhenProviderHasNotVerifiedIt() {
        when(userRepository.findByEmail(BOOTSTRAP_ADMIN)).thenReturn(Optional.empty());

        AuthResponse response = authService.handleOAuth2Login(oauthUser(BOOTSTRAP_ADMIN, false), "google", "hr-admin");

        assertEquals("PENDING", response.getApprovalStatus());
        assertEquals("RECRUITER", response.getUser().getRole());
    }

    private static RegisterRequest registerRequest(String role) {
        return RegisterRequest.builder()
                .username("alice").email("alice@example.com")
                .password("password123").confirmPassword("password123")
                .firstName("Alice").lastName("Smith")
                .role(role)
                .build();
    }

    private static User user(UserRole role, ApprovalStatus status) {
        return User.builder()
                .id(7L).username("alice").email("alice@example.com").password("hashed")
                .firstName("Alice").lastName("Smith")
                .role(role).approvalStatus(status)
                .build();
    }

    private static OAuth2User oauthUser(String email, boolean emailVerified) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-123");
        attributes.put("email", email);
        attributes.put("email_verified", emailVerified);
        attributes.put("given_name", "Pat");
        attributes.put("family_name", "Lee");
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")), attributes, "sub");
    }
}
