package com.ticket.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.dto.ErrorResponse;
import com.ticket.security.JwtValidator;
import com.ticket.security.Role;
import com.ticket.security.TokenPrincipal;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;


@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    private enum Access { PUBLIC, AUTHENTICATED, ADMIN }

    private record Rule(Set<HttpMethod> methods, String pattern, Access access) {
        boolean matches(HttpMethod method, String path, PathMatcher matcher) {
            boolean methodMatches = methods.isEmpty() || methods.contains(method);
            return methodMatches && matcher.match(pattern, path);
        }
    }

    private static final List<Rule> RULES = List.of(

            new Rule(Set.of(HttpMethod.OPTIONS), "/**", Access.PUBLIC),


            new Rule(Set.of(HttpMethod.POST), "/api/users/register", Access.PUBLIC),
            new Rule(Set.of(HttpMethod.POST), "/api/users/login", Access.PUBLIC),
            new Rule(Set.of(HttpMethod.POST), "/api/users/refresh", Access.PUBLIC),


            new Rule(Set.of(HttpMethod.GET), "/ping", Access.PUBLIC),
            new Rule(Set.of(HttpMethod.GET), "/api/events/**", Access.PUBLIC),
            new Rule(Set.of(HttpMethod.GET), "/api/bookings/events/*/seat-status", Access.PUBLIC),
            new Rule(Set.of(HttpMethod.POST), "/api/ai/chat", Access.AUTHENTICATED),
            new Rule(Set.of(HttpMethod.GET), "/api/ai/search", Access.PUBLIC),
            new Rule(Set.of(HttpMethod.GET), "/api/ai/health", Access.PUBLIC),


            new Rule(Set.of(HttpMethod.GET), "/api/ai/stats", Access.ADMIN),
            new Rule(Set.of(HttpMethod.GET), "/api/ai/diagnostics", Access.ADMIN),
            new Rule(Set.of(), "/api/events/**", Access.ADMIN),
            new Rule(Set.of(), "/api/analytics/**", Access.ADMIN),
            new Rule(Set.of(HttpMethod.PUT), "/api/users/*/role", Access.ADMIN)
    );

    private final PathMatcher pathMatcher = new AntPathMatcher();
    private final JwtValidator jwtValidator;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtValidator jwtValidator, ObjectMapper objectMapper) {
        this.jwtValidator = jwtValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String rawPath = request.getURI().getRawPath();
        if (isSuspiciousPath(rawPath)) {
            return reject(exchange, HttpStatus.BAD_REQUEST, "BAD_PATH", "Недопустимый путь запроса");
        }

        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        Access required = resolveAccess(method, path);

        Optional<TokenPrincipal> principal = extractBearerToken(request)
                .flatMap(jwtValidator::parseAccessToken);

        if (required != Access.PUBLIC) {
            if (principal.isEmpty()) {
                return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Требуется действующий access-токен");
            }
            if (required == Access.ADMIN && principal.get().role() != Role.ADMIN) {
                return reject(exchange, HttpStatus.FORBIDDEN, "ADMIN_REQUIRED",
                        "Только администратор может выполнять это действие");
            }
        }

       ServerHttpRequest.Builder builder = request.mutate().headers(headers -> {
            headers.remove(HEADER_USER_ID);
            headers.remove(HEADER_USER_ROLE);
        });

        principal.ifPresent(p -> {
            builder.header(HEADER_USER_ID, p.userId());
            builder.header(HEADER_USER_ROLE, p.role().name());
        });

        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private Access resolveAccess(HttpMethod method, String path) {
        for (Rule rule : RULES) {
            if (rule.matches(method, path, pathMatcher)) {
                return rule.access();
            }
        }
        return Access.AUTHENTICATED;
    }

    private boolean isSuspiciousPath(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        String lower = rawPath.toLowerCase(Locale.ROOT);
        return lower.contains("..") || lower.contains("//") || lower.contains("%2e") || lower.contains("%2f");
    }

    private Optional<String> extractBearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.empty();
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String errorCode, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .time(LocalDateTime.now())
                .build();

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(error);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            byte[] fallback = "{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(fallback)));
        }
    }
}