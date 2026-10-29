package org.vinod.sha.gateway.config;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphQLAuthorizationInterceptorTest {

    // 62 bytes (496 bits): the same length class as the local .env secret, which jjwt signs with HS384.
    private static final String SECRET = "test-secret-0123456789-0123456789-0123456789-0123456789-abcdef";
    private static final String OTHER_SECRET = "attacker-secret-0123456789-0123456789-0123456789-0123456789-xyz";

    private final GraphQLAuthorizationInterceptor interceptor =
            new GraphQLAuthorizationInterceptor(decoderFor(SECRET), new GraphQLContextInterceptor());

    @Test
    void allowsPublicLoginWithoutToken() {
        Outcome outcome = run("mutation { login(username: \"a\", password: \"b\") { token } }", null);

        assertTrue(outcome.reachedResolvers());
    }

    @Test
    void allowsPublicLoginEvenWithExpiredToken() {
        Outcome outcome = run("mutation { login(username: \"a\", password: \"b\") { token } }",
                token(SECRET, "JOB_SEEKER", -60_000));

        assertTrue(outcome.reachedResolvers());
    }

    @Test
    void rejectsProtectedFieldWithoutToken() {
        Outcome outcome = run("{ myCandidateDashboard { totalApplications } }", null);

        assertEquals(ErrorType.UNAUTHORIZED, outcome.errorType());
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        Outcome outcome = run("{ me { id } }", token(OTHER_SECRET, "ADMIN", 60_000));

        assertEquals(ErrorType.UNAUTHORIZED, outcome.errorType());
    }

    @Test
    void rejectsExpiredToken() {
        Outcome outcome = run("{ me { id } }", token(SECRET, "JOB_SEEKER", -60_000));

        assertEquals(ErrorType.UNAUTHORIZED, outcome.errorType());
    }

    @Test
    void acceptsAuthServiceTokenAndExposesRoleToMethodSecurity() {
        Outcome outcome = run("{ me { id } }", token(SECRET, "JOB_SEEKER", 60_000));

        assertTrue(outcome.reachedResolvers());
        assertTrue(outcome.authorities().contains("ROLE_JOB_SEEKER"));
    }

    @Test
    void forbidsCandidateFromHrField() {
        Outcome outcome = run("mutation { deleteJob(id: \"1\") }", token(SECRET, "JOB_SEEKER", 60_000));

        assertEquals(ErrorType.FORBIDDEN, outcome.errorType());
    }

    @Test
    void forbidsHrFieldHiddenInFragmentAlongsidePublicField() {
        String document = "query { jobs { totalCount } ...F } fragment F on Query { candidates { totalCount } }";

        Outcome outcome = run(document, token(SECRET, "JOB_SEEKER", 60_000));

        assertEquals(ErrorType.FORBIDDEN, outcome.errorType());
    }

    @Test
    void allowsRecruiterToUseHrField() {
        Outcome outcome = run("mutation { deleteJob(id: \"1\") }", token(SECRET, "RECRUITER", 60_000));

        assertTrue(outcome.reachedResolvers());
    }

    @Test
    void forbidsRecruiterFromAccountApproval() {
        Outcome outcome = run("{ pendingUsers { id } }", token(SECRET, "RECRUITER", 60_000));

        assertEquals(ErrorType.FORBIDDEN, outcome.errorType());
    }

    @Test
    void allowsAdminToApproveAccounts() {
        Outcome outcome = run("mutation { approveUser(id: \"7\") { id } }", token(SECRET, "ADMIN", 60_000));

        assertTrue(outcome.reachedResolvers());
    }

    @Test
    void selectsHmacStrengthLikeJjwt() {
        assertEquals(MacAlgorithm.HS256, GatewaySecurityConfig.macAlgorithmFor(new byte[32]));
        assertEquals(MacAlgorithm.HS384, GatewaySecurityConfig.macAlgorithmFor(new byte[62]));
        assertEquals(MacAlgorithm.HS512, GatewaySecurityConfig.macAlgorithmFor(new byte[64]));
        assertThrows(IllegalStateException.class, () -> GatewaySecurityConfig.macAlgorithmFor(new byte[31]));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private record Outcome(boolean reachedResolvers, ErrorType errorType, Set<String> authorities) {
    }

    private Outcome run(String document, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        WebGraphQlRequest request = new WebGraphQlRequest(
                URI.create("http://localhost/graphql"),
                headers,
                new LinkedMultiValueMap<String, HttpCookie>(),
                new InetSocketAddress("127.0.0.1", 12345),
                Map.of(),
                Map.of("query", document),
                "req-1",
                Locale.ENGLISH);

        WebGraphQlInterceptor.Chain chain = next -> ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication().getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .defaultIfEmpty(Set.of())
                .map(authorities -> respond(next, Map.of("authorities", authorities)));

        WebGraphQlResponse response = interceptor.intercept(request, chain).block();
        List<GraphQLError> errors = response.getExecutionResult().getErrors();
        if (!errors.isEmpty()) {
            return new Outcome(false, (ErrorType) errors.get(0).getErrorType(), Set.of());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getExecutionResult().getData();
        @SuppressWarnings("unchecked")
        Set<String> authorities = (Set<String>) data.get("authorities");
        return new Outcome(true, null, authorities);
    }

    private static WebGraphQlResponse respond(WebGraphQlRequest request, Map<String, Object> data) {
        ExecutionResult result = ExecutionResult.newExecutionResult().data(data).build();
        return new WebGraphQlResponse(new DefaultExecutionGraphQlResponse(request.toExecutionInput(), result));
    }

    /** Mirrors auth-service JwtTokenProvider: jjwt picks the HMAC algorithm from the key length. */
    private static String token(String secret, String role, long expiresInMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject("user")
                .claims(Map.of("id", 42L, "username", "user", "role", role))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiresInMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static ReactiveJwtDecoder decoderFor(String secret) {
        GatewaySecurityConfig config = new GatewaySecurityConfig();
        ReflectionTestUtils.setField(config, "jwtSecret", secret);
        return config.jwtDecoder();
    }
}
