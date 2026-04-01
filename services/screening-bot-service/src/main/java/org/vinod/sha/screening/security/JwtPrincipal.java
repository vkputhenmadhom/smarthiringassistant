package org.vinod.sha.screening.security;

public record JwtPrincipal(Long userId, String username, String role) {
}

