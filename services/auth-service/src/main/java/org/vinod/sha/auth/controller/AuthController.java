package org.vinod.sha.auth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.vinod.sha.auth.dto.AuthResponse;
import org.vinod.sha.auth.dto.LoginRequest;
import org.vinod.sha.auth.dto.RegisterRequest;
import org.vinod.sha.auth.service.AuthService;

@Slf4j
@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        log.info("Register request for username: {}", request.getUsername());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        log.info("Login request for username: {}", request.getUsername());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("Authorization") String bearerToken) {
        String token = bearerToken.substring(7); // Remove "Bearer " prefix
        log.info("Refresh token request");
        AuthResponse response = authService.refreshToken(token);
        return ResponseEntity.ok(response);
    }

    @GetMapping("validate")
    public ResponseEntity<String> validateToken(@RequestHeader("Authorization") String bearerToken) {
        try {
            String token = bearerToken.substring(7); // Remove "Bearer " prefix
            authService.validateToken(token);
            return ResponseEntity.ok("Token is valid");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
    }

    @GetMapping("me")
    public ResponseEntity<String> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }
        return ResponseEntity.ok("User: " + authentication.getName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("Error in auth controller", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
    }
}

