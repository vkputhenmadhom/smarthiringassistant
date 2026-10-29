package org.vinod.sha.resumeparser.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-0123456789-0123456789-0123456789-0123456789-abcdef";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void principalCarriesTheUserIdFromTheToken() throws Exception {
        Authentication authentication = authenticate(token(Map.of("id", 42L, "role", "JOB_SEEKER")));

        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        assertEquals(42L, user.id());
        assertEquals("jane", authentication.getName());
    }

    @Test
    void tokenWithoutIdLeavesTheIdEmpty() throws Exception {
        Authentication authentication = authenticate(token(Map.of("role", "JOB_SEEKER")));

        assertNull(((AuthenticatedUser) authentication.getPrincipal()).id());
    }

    private Authentication authenticate(String token) throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        new JwtAuthenticationFilter(provider).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /** Signed the way auth-service's JwtTokenProvider signs tokens. */
    private static String token(Map<String, Object> claims) {
        Date now = new Date();
        return Jwts.builder().subject("jane").claims(claims).issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
