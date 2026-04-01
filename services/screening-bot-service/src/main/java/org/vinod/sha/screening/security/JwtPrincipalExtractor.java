package org.vinod.sha.screening.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Optional;

@Component
public class JwtPrincipalExtractor {

	@Value("${jwt.secret}")
	private String jwtSecret;

	public Optional<JwtPrincipal> extractFromAuthorizationHeader(String authorizationHeader) {
		String token = extractBearerToken(authorizationHeader);
		if (!StringUtils.hasText(token)) {
			return Optional.empty();
		}

		try {
			Claims claims = Jwts.parser()
					.verifyWith((SecretKey) getSigningKey())
					.build()
					.parseSignedClaims(token)
					.getPayload();

			Long userId = claims.get("id", Long.class);
			String username = claims.getSubject();
			String role = claims.get("role", String.class);
			if (userId == null || !StringUtils.hasText(username)) {
				return Optional.empty();
			}
			return Optional.of(new JwtPrincipal(userId, username, role));
		} catch (JwtException | IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	private String extractBearerToken(String header) {
		if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
			return header.substring(7);
		}
		return null;
	}

	private Key getSigningKey() {
		return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
	}
}

