package org.vinod.sha.gateway.config;

import graphql.ExecutionResult;
import graphql.GraphqlErrorBuilder;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.Parser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Authorizes GraphQL requests per top-level field, for both HTTP and WebSocket transports.
 * <p>
 * The {@code /graphql} endpoints are open at the HTTP layer (login and registration go
 * through them), so this interceptor is the gate: it verifies the bearer token's signature
 * and expiry, rejects anonymous calls to non-public fields, restricts HR fields to HR roles,
 * and exposes the verified authentication to {@code @PreAuthorize} resolvers.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GraphQLAuthorizationInterceptor implements WebGraphQlInterceptor {

    static final Set<String> PUBLIC_FIELDS = Set.of(
            "login", "register", "refreshToken", "jobs", "job",
            "__schema", "__type", "__typename");

    static final Set<String> HR_FIELDS = Set.of(
            "createJob", "updateJob", "deleteJob", "publishJob", "closeJob",
            "candidates", "candidate", "matchesForJob", "triggerMatching", "updateMatchStatus",
            "dashboardMetrics", "advanceScreening", "newMatchForJob");

    /** auth-service {@code UserRole} values allowed to use HR fields. */
    static final Set<String> HR_ROLES = Set.of("ADMIN", "RECRUITER", "HIRING_MANAGER");

    /** Account approval is reserved for administrators. */
    static final Set<String> ADMIN_FIELDS = Set.of("pendingUsers", "approveUser", "rejectUser");

    private final ReactiveJwtDecoder jwtDecoder;
    private final GraphQLContextInterceptor contextInterceptor;

    public GraphQLAuthorizationInterceptor(ReactiveJwtDecoder jwtDecoder, GraphQLContextInterceptor contextInterceptor) {
        this.jwtDecoder = jwtDecoder;
        this.contextInterceptor = contextInterceptor;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        Optional<Set<String>> fields = topLevelFields(request.getDocument());
        if (fields.isEmpty()) {
            // Unparseable documents never execute; let the engine return its syntax error.
            return chain.next(request);
        }
        boolean requiresAuthentication = !PUBLIC_FIELDS.containsAll(fields.get());
        boolean requiresHrRole = fields.get().stream().anyMatch(HR_FIELDS::contains);
        boolean requiresAdminRole = fields.get().stream().anyMatch(ADMIN_FIELDS::contains);

        return verifiedJwt(contextInterceptor.resolveAuthorization(request))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(jwt -> {
                    if (jwt.isEmpty()) {
                        return requiresAuthentication
                                ? Mono.just(deny(request, ErrorType.UNAUTHORIZED, "Authentication required"))
                                : chain.next(request);
                    }
                    String role = jwt.get().getClaimAsString("role");
                    if ((requiresHrRole && !HR_ROLES.contains(role)) || (requiresAdminRole && !"ADMIN".equals(role))) {
                        return Mono.just(deny(request, ErrorType.FORBIDDEN, "Insufficient role for this operation"));
                    }
                    return GatewaySecurityConfig.jwtAuthenticationConverter().convert(jwt.get())
                            .flatMap(authentication -> chain.next(request)
                                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)));
                });
    }

    private Mono<Jwt> verifiedJwt(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Mono.empty();
        }
        return jwtDecoder.decode(authorization.substring(7))
                .onErrorResume(JwtException.class, e -> {
                    log.debug("Rejected GraphQL bearer token: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Collects the top-level field names of every operation in the document, following
     * fragments, or empty if the document cannot be parsed.
     */
    static Optional<Set<String>> topLevelFields(String document) {
        Document parsed;
        try {
            parsed = Parser.parse(document);
        } catch (Exception e) {
            return Optional.empty();
        }
        Map<String, FragmentDefinition> fragments = new HashMap<>();
        parsed.getDefinitionsOfType(FragmentDefinition.class)
                .forEach(fragment -> fragments.put(fragment.getName(), fragment));

        Set<String> fields = new HashSet<>();
        for (OperationDefinition operation : parsed.getDefinitionsOfType(OperationDefinition.class)) {
            collectFields(operation.getSelectionSet(), fragments, new HashSet<>(), fields);
        }
        return Optional.of(fields);
    }

    private static void collectFields(SelectionSet selectionSet, Map<String, FragmentDefinition> fragments,
                                      Set<String> visitedFragments, Set<String> fields) {
        if (selectionSet == null) {
            return;
        }
        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                fields.add(field.getName());
            } else if (selection instanceof InlineFragment inline) {
                collectFields(inline.getSelectionSet(), fragments, visitedFragments, fields);
            } else if (selection instanceof FragmentSpread spread && visitedFragments.add(spread.getName())) {
                FragmentDefinition fragment = fragments.get(spread.getName());
                collectFields(fragment == null ? null : fragment.getSelectionSet(), fragments, visitedFragments, fields);
            }
        }
    }

    private static WebGraphQlResponse deny(WebGraphQlRequest request, ErrorType errorType, String message) {
        ExecutionResult result = ExecutionResult.newExecutionResult()
                .addError(GraphqlErrorBuilder.newError().errorType(errorType).message(message).build())
                .build();
        return new WebGraphQlResponse(new DefaultExecutionGraphQlResponse(request.toExecutionInput(), result));
    }
}
