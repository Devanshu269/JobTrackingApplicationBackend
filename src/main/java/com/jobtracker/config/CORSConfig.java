package com.jobtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CORSConfig {

    /**
     * One or more origins, comma-separated.
     *
     * <p>A list rather than a single value because Vercel gives every preview deployment its own
     * hostname — with one fixed origin, any branch preview fails CORS against this API. Also lets
     * a deployed backend keep accepting {@code http://localhost:5173} while you develop against it.
     *
     * <p>Each entry must be scheme + host only: no path, no trailing slash. {@code "*"} is not an
     * option here because credentials are enabled, and the two are mutually exclusive by spec.
     */
    @Value("${cors.allowed-origin}")
    private String allowedOrigin;


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigin.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                // A trailing slash makes the origin never match — the browser sends none.
                .map(o -> o.endsWith("/") ? o.substring(0, o.length() - 1) : o)
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
