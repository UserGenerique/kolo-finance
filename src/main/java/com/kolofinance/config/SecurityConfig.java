package com.kolofinance.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class SecurityConfig {

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
     * Filtre API Key pour les endpoints REST (sauf webhook).
     */
    @Bean
    @Order(1)
    public OncePerRequestFilter apiKeyFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {

                String path = request.getRequestURI();

                // Le webhook WhatsApp est public (vérifié par verify_token + signature)
                if (path.startsWith("/api/webhook")) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // Les autres endpoints nécessitent l'API key
                if (path.startsWith("/api/")) {
                    String key = request.getHeader("X-API-Key");
                    if (key == null || !key.equals(apiKey)) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("{\"error\": \"API key invalide ou manquante\"}");
                        response.setContentType("application/json");
                        return;
                    }
                }

                filterChain.doFilter(request, response);
            }
        };
    }
}
