package org.vinod.sha.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vinod.sha.auth.dto.AuthResponse;
import org.vinod.sha.auth.dto.LoginRequest;
import org.vinod.sha.auth.dto.RegisterRequest;
import org.vinod.sha.auth.dto.UserResponse;
import org.vinod.sha.auth.entity.ApprovalStatus;
import org.vinod.sha.auth.entity.User;
import org.vinod.sha.auth.entity.UserRole;
import org.vinod.sha.auth.exception.AccountNotApprovedException;
import org.vinod.sha.auth.repository.UserRepository;
import org.vinod.sha.auth.security.JwtTokenProvider;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserEventPublisher eventPublisher;
    private final Set<String> bootstrapAdminEmails;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       UserEventPublisher eventPublisher,
                       @Value("${app.security.bootstrap-admin-emails:}") String bootstrapAdminEmails) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.eventPublisher = eventPublisher;
        this.bootstrapAdminEmails = Arrays.stream(bootstrapAdminEmails.split(","))
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public AuthResponse register(RegisterRequest request) {
        // Validate input
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Check if user already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Create new user; HR roles wait for an administrator before they can log in
        UserRole role = request.getRole() == null || request.getRole().isBlank()
                ? UserRole.JOB_SEEKER
                : UserRole.valueOf(request.getRole());
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(role)
                .approvalStatus(role.requiresApproval() ? ApprovalStatus.PENDING : ApprovalStatus.APPROVED)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getUsername());

        // Publish user registered event
        eventPublisher.publishUserRegisteredEvent(savedUser);

        if (!savedUser.isApproved()) {
            log.info("Account {} ({}) is awaiting administrator approval", savedUser.getUsername(), role);
            return buildUnapprovedResponse(savedUser);
        }

        // Generate tokens
        String accessToken = tokenProvider.generateToken(savedUser);
        String refreshToken = tokenProvider.generateRefreshToken(savedUser);

        return buildAuthResponse(savedUser, accessToken, refreshToken);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new IllegalArgumentException("User account is disabled");
        }
        requireApproved(user);

        // Update last login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        log.info("User logged in: {}", user.getUsername());

        // Publish user authenticated event
        eventPublisher.publishUserAuthenticatedEvent(user);

        // Generate tokens
        String accessToken = tokenProvider.generateToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    public AuthResponse handleOAuth2Login(OAuth2User oauth2User, String registrationId, String portalOrigin) {
        String email = extractEmail(oauth2User);
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Unable to retrieve email from " + registrationId + " profile");
        }

        boolean isNewUser = !userRepository.existsByEmail(email);
        User user = userRepository.findByEmail(email)
                .map(existing -> updateUserFromOAuth2(existing, oauth2User))
                .orElseGet(() -> createUserFromOAuth2(oauth2User, registrationId, email, portalOrigin));
        applyBootstrapAdmin(user, oauth2User);

        user.setLastLogin(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        if (isNewUser) {
            eventPublisher.publishUserRegisteredEvent(savedUser);
        }
        if (!savedUser.isApproved()) {
            log.info("OAuth2 account {} is {}; no tokens issued", savedUser.getUsername(), savedUser.getApprovalStatus());
            return buildUnapprovedResponse(savedUser);
        }
        eventPublisher.publishUserAuthenticatedEvent(savedUser);

        String accessToken = tokenProvider.generateToken(savedUser);
        String refreshToken = tokenProvider.generateRefreshToken(savedUser);
        return buildAuthResponse(savedUser, accessToken, refreshToken);
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        String username = tokenProvider.getUsernameFromToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        requireApproved(user);

        String newAccessToken = tokenProvider.generateToken(user);

        return buildAuthResponse(user, newAccessToken, refreshToken);
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    public void validateToken(String token) {
        if (!tokenProvider.validateToken(token)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600L)
                .user(UserResponse.from(user))
                .approvalStatus(user.getApprovalStatus().name())
                .build();
    }

    private AuthResponse buildUnapprovedResponse(User user) {
        return AuthResponse.builder()
                .tokenType(null)
                .user(UserResponse.from(user))
                .approvalStatus(user.getApprovalStatus().name())
                .build();
    }

    private void requireApproved(User user) {
        if (!user.isApproved()) {
            throw new AccountNotApprovedException(user.getApprovalStatus());
        }
    }

    /**
     * Makes the configured bootstrap administrators ADMIN and approved. Only trusted for
     * emails the OAuth2 provider has verified, so nobody can claim one by registering it.
     */
    private void applyBootstrapAdmin(User user, OAuth2User oauth2User) {
        boolean emailVerified = Boolean.TRUE.equals(oauth2User.getAttributes().get("email_verified"));
        if (emailVerified && bootstrapAdminEmails.contains(user.getEmail().toLowerCase(Locale.ROOT))) {
            user.setRole(UserRole.ADMIN);
            user.setApprovalStatus(ApprovalStatus.APPROVED);
        }
    }

    private User createUserFromOAuth2(OAuth2User oauth2User, String registrationId, String email, String portalOrigin) {
        String firstName = extractFirstName(oauth2User);
        String lastName = extractLastName(oauth2User);
        String username = generateUniqueUsername(email, registrationId);

        // HR portal sign-ups start as a pending recruiter; an administrator picks the final role.
        UserRole assignedRole = isHrPortal(portalOrigin) ? UserRole.RECRUITER : UserRole.JOB_SEEKER;

        return User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .firstName(firstName)
                .lastName(lastName)
                .role(assignedRole)
                .approvalStatus(assignedRole.requiresApproval() ? ApprovalStatus.PENDING : ApprovalStatus.APPROVED)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }

    private User updateUserFromOAuth2(User existing, OAuth2User oauth2User) {
        String firstName = extractFirstName(oauth2User);
        String lastName = extractLastName(oauth2User);

        if (firstName != null && !firstName.isBlank()) {
            existing.setFirstName(firstName);
        }
        if (lastName != null && !lastName.isBlank()) {
            existing.setLastName(lastName);
        }

        return existing;
    }

    private boolean isHrPortal(String portalOrigin) {
        return portalOrigin != null && "hr-admin".equals(portalOrigin.trim().toLowerCase(Locale.ROOT));
    }

    private String extractEmail(OAuth2User user) {
        Object email = user.getAttributes().get("email");
        if (email instanceof String value && !value.isBlank()) {
            return value;
        }
        return null;
    }

    private String extractFirstName(OAuth2User user) {
        Map<String, Object> attrs = user.getAttributes();
        Object first = attrs.get("given_name");
        if (first instanceof String value && !value.isBlank()) {
            return value;
        }
        Object name = attrs.get("name");
        if (name instanceof String value && !value.isBlank()) {
            String[] parts = value.trim().split("\\s+", 2);
            return parts[0];
        }
        return "OAuth";
    }

    private String extractLastName(OAuth2User user) {
        Map<String, Object> attrs = user.getAttributes();
        Object last = attrs.get("family_name");
        if (last instanceof String value && !value.isBlank()) {
            return value;
        }
        Object name = attrs.get("name");
        if (name instanceof String value && !value.isBlank()) {
            String[] parts = value.trim().split("\\s+", 2);
            return parts.length > 1 ? parts[1] : "User";
        }
        return "User";
    }

    private String generateUniqueUsername(String email, String registrationId) {
        String localPart = email.split("@")[0].replaceAll("[^a-zA-Z0-9._-]", "");
        String base = (localPart + "_" + registrationId).toLowerCase();
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }
}

