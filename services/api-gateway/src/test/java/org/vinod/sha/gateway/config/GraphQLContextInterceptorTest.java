package org.vinod.sha.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpCookie;
import org.springframework.util.LinkedMultiValueMap;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GraphQLContextInterceptorTest {

    private final GraphQLContextInterceptor interceptor = new GraphQLContextInterceptor();

    @Test
    void extractsAuthorizationFromConnectionInitPayload() {
        Map<String, Object> payload = Map.of("authorization", "Bearer token-123");
        assertEquals("Bearer token-123", interceptor.extractAuthorization(payload));
    }

    @Test
    void extractsAuthorizationFromNestedHeadersPayload() {
        Map<String, Object> payload = Map.of("headers", Map.of("Authorization", "Bearer nested-token"));
        assertEquals("Bearer nested-token", interceptor.extractAuthorization(payload));
    }

    @Test
    void storesAuthorizationInWebSocketSessionAttributesDuringConnectionInit() {
        TestWebSocketSessionInfo sessionInfo = new TestWebSocketSessionInfo();

        interceptor.handleConnectionInitialization(sessionInfo, Map.of("authToken", "Bearer ws-token")).block();

        assertEquals("Bearer ws-token", sessionInfo.getAttributes().get(GraphQLContextInterceptor.AUTHORIZATION_CONTEXT_KEY));
    }

    @Test
    void resolvesAuthorizationFromWebSocketSessionAttributesWhenHeaderMissing() {
        TestWebSocketSessionInfo sessionInfo = new TestWebSocketSessionInfo();
        sessionInfo.getAttributes().put(GraphQLContextInterceptor.AUTHORIZATION_CONTEXT_KEY, "Bearer session-token");

        WebSocketGraphQlRequest request = new WebSocketGraphQlRequest(
                URI.create("ws://localhost/graphql-ws"),
                new HttpHeaders(),
                new LinkedMultiValueMap<String, HttpCookie>(),
                sessionInfo.getRemoteAddress(),
                Map.of(),
                Map.of("query", "subscription { newNotification(userId: \"42\") { id } }"),
                "req-1",
                Locale.ENGLISH,
                sessionInfo
        );

        assertEquals("Bearer session-token", interceptor.resolveAuthorization(request));
    }

    @Test
    void returnsNullWhenNoAuthorizationPresent() {
        assertNull(interceptor.extractAuthorization(Map.of()));
    }

    static class TestWebSocketSessionInfo implements WebSocketSessionInfo {
        private final Map<String, Object> attributes = new HashMap<>();
        private final HttpHeaders headers = new HttpHeaders();

        @Override
        public String getId() {
            return "session-1";
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public URI getUri() {
            return URI.create("ws://localhost/graphql-ws");
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Mono<Principal> getPrincipal() {
            return Mono.empty();
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }
    }
}

