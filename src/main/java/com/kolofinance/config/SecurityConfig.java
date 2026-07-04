package com.kolofinance.config;
import com.kolofinance.dto.AuthPrincipal;
import com.kolofinance.model.enums.Role;
import com.kolofinance.service.AuthService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Pattern ORG_PATH = Pattern.compile("^/api/organizations/(\\d+)(/.*)?$");

    private final AuthService authService;

    @Value("${app.api-key}")
    private String apiKey;

    /**
     * CORS ouvert pour le MVP.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE");
            }
        };
    }

    /**
     * Filtre API Key/Bearer token pour les endpoints REST (sauf webhook/auth login).
     */
    @Bean
    @Order(1)
    public OncePerRequestFilter apiAuthFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {

                String path = request.getRequestURI();

                // Le webhook WhatsApp est public (vérifié par verify_token + signature)
                if (path.startsWith("/api/webhook") || path.equals("/api/auth/login")) {
                    filterChain.doFilter(request, response);
                    return;
                }
                // Les autres endpoints nécessitent l'API key de secours ou une session Bearer.
                if (path.startsWith("/api/")) {
                    String key = request.getHeader("X-API-Key");
                    if (key != null && key.equals(apiKey)) {
                        filterChain.doFilter(request, response);
                        return;
                    }

                    String token = bearerToken(request);
                    Long orgId = requestedOrgId(path);
                    Optional<AuthPrincipal> principalOpt = authService.resolve(token, orgId);
                    if (principalOpt.isEmpty()) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("{\"error\": \"Session invalide ou expirée\"}");
                        response.setContentType("application/json");
                        return;
                    }

                    AuthPrincipal principal = principalOpt.get();
                    if (path.startsWith("/api/platform") && !principal.isPlatformAdmin()) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("{\"error\": \"Accès super admin requis\"}");
                        response.setContentType("application/json");
                        return;
                    }

                    if (orgId != null && !principal.isPlatformAdmin() && principal.getMembership() == null) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("{\"error\": \"Accès interdit à cette organisation\"}");
                        response.setContentType("application/json");
                        return;
                    }

                    if (orgId != null && principal.getRole() == Role.AGENT && !isAgentAllowedPath(path, request.getMethod())) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("{\"error\": \"Accès agent limité aux affectations de fonds\"}");
                        response.setContentType("application/json");
                        return;
                    }

                    request.setAttribute("authPrincipal", principal);
                }

                filterChain.doFilter(request, response);
            }
        };
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private Long requestedOrgId(String path) {
        Matcher matcher = ORG_PATH.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        return Long.valueOf(matcher.group(1));
    }

    private boolean isAgentAllowedPath(String path, String method) {
        boolean isGet = "GET".equalsIgnoreCase(method);
        boolean isPost = "POST".equalsIgnoreCase(method);
        return (isGet && path.matches("^/api/organizations/\\d+/(expenses|drafts|users|funds)$"))
                || (isGet && path.matches("^/api/organizations/\\d+/funds/pending-receipts$"))
                || (isPost && path.matches("^/api/organizations/\\d+/funds/\\d+/(accept|reject)-receipt$"));
    }
}
