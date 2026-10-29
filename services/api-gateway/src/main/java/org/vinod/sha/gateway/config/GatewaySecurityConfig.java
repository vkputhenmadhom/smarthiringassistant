package org.vinod.sha.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class GatewaySecurityConfig {

    /**
     * Paths reachable without a token. GraphQL endpoints are listed because they are
     * authorized per operation by {@link GraphQLAuthorizationInterceptor}; auth endpoints
     * must stay open so that login, registration, refresh and OAuth2 callbacks work.
     */
    static final String[] PUBLIC_PATHS = {
            "/api/auth/**",
            "/graphql", "/graphql-ws", "/graphiql", "/graphiql/**",
            "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus"
    };

    @Value("${app.security.cors.allowed-origin-patterns:http://localhost:4200,http://localhost:5173}")
    private String allowedOriginPatterns;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        // Notification logs hold recipients' contact details and send-test sends mail
                        .pathMatchers("/api/notifications/**").hasRole("ADMIN")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenConverter(bearerTokenConverterSkippingPublicPaths())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        byte[] key = jwtSecret.getBytes(StandardCharsets.UTF_8);
        MacAlgorithm algorithm = macAlgorithmFor(key);
        return NimbusReactiveJwtDecoder
                .withSecretKey(new SecretKeySpec(key, algorithm.getName()))
                .macAlgorithm(algorithm)
                .build();
    }

    /**
     * Grants two authorities per token: the auth-service role from the {@code role} claim (ROLE_ADMIN,
     * ROLE_JOB_SEEKER, ...; used by the REST rules above) and the matching GraphQL role (ROLE_HR_ADMIN,
     * ROLE_CANDIDATE, ...; used by {@code @PreAuthorize} on the GraphQL resolvers, which speak the
     * schema's {@code Role} enum).
     */
    static Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authServiceRoles = new JwtGrantedAuthoritiesConverter();
        authServiceRoles.setAuthoritiesClaimName("role");
        authServiceRoles.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> granted = new ArrayList<>(authServiceRoles.convert(jwt));
            graphqlRoleFor(jwt.getClaimAsString("role"))
                    .ifPresent(role -> granted.add(new SimpleGrantedAuthority("ROLE_" + role)));
            return granted;
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    /** auth-service UserRole -> GraphQL Role, as the frontends and the {@code me} query present it. */
    static Optional<String> graphqlRoleFor(String authServiceRole) {
        if (authServiceRole == null) {
            return Optional.empty();
        }
        return switch (authServiceRole.toUpperCase(Locale.ROOT)) {
            case "ADMIN", "HIRING_MANAGER" -> Optional.of("HR_ADMIN");
            case "RECRUITER" -> Optional.of("RECRUITER");
            case "JOB_SEEKER" -> Optional.of("CANDIDATE");
            default -> Optional.empty();
        };
    }

    /**
     * auth-service signs with jjwt's {@code Keys.hmacShaKeyFor(secret)}, which picks the
     * HMAC strength from the key length. The gateway must verify with the same algorithm.
     */
    static MacAlgorithm macAlgorithmFor(byte[] key) {
        int bits = key.length * 8;
        if (bits >= 512) {
            return MacAlgorithm.HS512;
        }
        if (bits >= 384) {
            return MacAlgorithm.HS384;
        }
        if (bits >= 256) {
            return MacAlgorithm.HS256;
        }
        throw new IllegalStateException("jwt.secret must be at least 256 bits (32 bytes)");
    }

    /**
     * Ignores bearer tokens on public paths so that an expired access token sent along
     * with a login or refresh call is not rejected before it reaches the auth flow.
     */
    private ServerAuthenticationConverter bearerTokenConverterSkippingPublicPaths() {
        ServerBearerTokenAuthenticationConverter bearer = new ServerBearerTokenAuthenticationConverter();
        ServerWebExchangeMatcher publicPaths = ServerWebExchangeMatchers.pathMatchers(PUBLIC_PATHS);
        return exchange -> publicPaths.matches(exchange)
                .flatMap(match -> match.isMatch() ? Mono.empty() : bearer.convert(exchange));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toList());
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Response-Source", "X-RateLimit-Remaining"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
