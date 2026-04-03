package org.vinod.sha.gateway.config;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.HashMap;
import java.util.Map;

/**
 * Extracts HTTP Authorization header from the servlet request and makes it
 * available to GraphQL resolvers via @ContextValue(name = "Authorization").
 */
@Component
public class GraphQLContextInterceptor implements WebGraphQlInterceptor {

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String authorizationHeader = request.getHeaders().getFirst("Authorization");
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            request.configureExecutionInput((executionInput, builder) -> {
                Map<Object, Object> contextData = new HashMap<>();
                contextData.put("Authorization", authorizationHeader);
                return builder.graphQLContext(contextData).build();
            });
        }
        return chain.next(request);
    }
}
