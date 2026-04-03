package org.vinod.sha.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vinod.sha.auth.dto.AuthResponse;
import org.vinod.sha.auth.dto.LoginRequest;
import org.vinod.sha.auth.dto.RegisterRequest;
import org.vinod.sha.auth.dto.UserResponse;
import org.vinod.sha.auth.entity.User;
import org.vinod.sha.auth.entity.UserRole;
import org.vinod.sha.auth.repository.UserRepository;
import org.vinod.sha.auth.security.JwtTokenProvider;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserEventPublisher eventPublisher;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       UserEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.eventPublisher = eventPublisher;
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

        // Create new user
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(UserRole.valueOf(request.getRole() != null ? request.getRole() : "JOB_SEEKER"))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getUsername());

        // Publish user registered event
        eventPublisher.publishUserRegisteredEvent(savedUser);

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
                .map(existing -> updateUserFromOAuth2(existing, oauth2User, portalOrigin))
                .orElseGet(() -> createUserFromOAuth2(oauth2User, registrationId, email, portalOrigin));

        user.setLastLogin(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        if (isNewUser) {
            eventPublisher.publishUserRegisteredEvent(savedUser);
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
                .user(UserResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .role(user.getRole().name())
                        .build())
                .build();
    }

    private User createUserFromOAuth2(OAuth2User oauth2User, String registrationId, String email, String portalOrigin) {
        String firstName = extractFirstName(oauth2User);
        String lastName = extractLastName(oauth2User);
        String username = generateUniqueUsername(email, registrationId);

        UserRole assignedRole = isHrPortal(portalOrigin) ? UserRole.ADMIN : UserRole.JOB_SEEKER;

        return User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .firstName(firstName)
                .lastName(lastName)
                .role(assignedRole)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }

    private User updateUserFromOAuth2(User existing, OAuth2User oauth2User, String portalOrigin) {
        String firstName = extractFirstName(oauth2User);
        String lastName = extractLastName(oauth2User);

        if (firstName != null && !firstName.isBlank()) {
            existing.setFirstName(firstName);
        }
        if (lastName != null && !lastName.isBlank()) {
            existing.setLastName(lastName);
        }

        // Upgrade a previously-created JOB_SEEKER to ADMIN when they log in from
        // the HR admin portal (handles accounts created before portal-aware registration).
        if (isHrPortal(portalOrigin) && existing.getRole() == UserRole.JOB_SEEKER) {
            existing.setRole(UserRole.ADMIN);
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

