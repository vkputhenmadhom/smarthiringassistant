package org.vinod.sha.gateway.config;

import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Extracts Authorization from HTTP requests and GraphQL WebSocket connection_init
 * payloads and makes it available to resolvers via
 * `@ContextValue(name = "Authorization")`.
 */
@Component
public class GraphQLContextInterceptor implements WebSocketGraphQlInterceptor {

    static final String AUTHORIZATION_CONTEXT_KEY = "Authorization";

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String authorizationHeader = resolveAuthorization(request);
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            request.configureExecutionInput((executionInput, builder) -> {
                Map<Object, Object> contextData = new HashMap<>();
                contextData.put(AUTHORIZATION_CONTEXT_KEY, authorizationHeader);
                return builder.graphQLContext(contextData).build();
            });
        }
        return chain.next(request);
    }

    @Override
    public Mono<Object> handleConnectionInitialization(WebSocketSessionInfo sessionInfo,
                                                       Map<String, Object> connectionInitPayload) {
        String authorization = extractAuthorization(connectionInitPayload);
        if (authorization == null || authorization.isBlank()) {
            authorization = sessionInfo.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        }
        if (authorization != null && !authorization.isBlank()) {
            sessionInfo.getAttributes().put(AUTHORIZATION_CONTEXT_KEY, authorization);
        }
        return Mono.empty();
    }

    String resolveAuthorization(WebGraphQlRequest request) {
        String authorizationHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            return authorizationHeader;
        }
        if (request instanceof WebSocketGraphQlRequest webSocketRequest) {
            Object sessionAuthorization = webSocketRequest.getSessionInfo().getAttributes().get(AUTHORIZATION_CONTEXT_KEY);
            if (sessionAuthorization != null) {
                return String.valueOf(sessionAuthorization);
            }
        }
        return null;
    }

    String extractAuthorization(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        Object direct = firstNonBlank(
                payload.get(AUTHORIZATION_CONTEXT_KEY),
                payload.get(HttpHeaders.AUTHORIZATION),
                payload.get("authorization"),
                payload.get("authToken")
        );
        if (direct != null) {
            return String.valueOf(direct);
        }
        Object headers = payload.get("headers");
        if (headers instanceof Map<?, ?> headerMap) {
            Object nested = firstNonBlank(
                    headerMap.get(AUTHORIZATION_CONTEXT_KEY),
                    headerMap.get(HttpHeaders.AUTHORIZATION),
                    headerMap.get("authorization"),
                    headerMap.get("authToken")
            );
            if (nested != null) {
                return String.valueOf(nested);
            }
        }
        return null;
    }

    private Object firstNonBlank(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate != null && !String.valueOf(candidate).isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
