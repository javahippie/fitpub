package org.operaton.fitpub.config;

import lombok.RequiredArgsConstructor;
import org.operaton.fitpub.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the application.
 * Configures JWT-based authentication for REST API and public access for ActivityPub endpoints.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    /**
     * Configures the security filter chain.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for REST API
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - Static resources
                .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll()

                // Public endpoints - Error pages
                .requestMatchers("/error").permitAll()

                // Public endpoints - Web UI pages
                .requestMatchers("/", "/login", "/register", "/timeline", "/timeline/**", "/activities", "/activities/**").permitAll()
                .requestMatchers("/profile", "/profile/**", "/settings").permitAll() // Auth checked client-side

                // Public endpoints - ActivityPub federation
                .requestMatchers("/.well-known/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/users/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/users/*/inbox").permitAll()

                // Public endpoints - Authentication API
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/users/register").permitAll()

                // Public endpoints - Timeline API (read-only)
                .requestMatchers(HttpMethod.GET, "/api/timeline/public").permitAll()

                // Public endpoints - Activity track data (for public activities)
                .requestMatchers(HttpMethod.GET, "/api/activities/*/track").permitAll()

                // Public endpoints - User's public activities
                .requestMatchers(HttpMethod.GET, "/api/activities/user/*").permitAll()

                // Debug endpoints (dev only)
                .requestMatchers("/api/debug/**").permitAll()

                // Public endpoints - Likes and Comments (GET only)
                .requestMatchers(HttpMethod.GET, "/api/activities/*/likes").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/activities/*/comments").permitAll()

                // Protected endpoints - Likes and Comments (POST/DELETE)
                .requestMatchers(HttpMethod.POST, "/api/activities/*/likes").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/activities/*/likes").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/activities/*/comments").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/activities/*/comments/*").authenticated()

                // Protected endpoints - Activities API
                .requestMatchers("/api/activities/**").authenticated()

                // Protected endpoints - Timeline API (user-specific)
                .requestMatchers("/api/timeline/**").authenticated()

                // User API endpoints
                .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/users/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/users/{username}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/id/*").permitAll()

                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configures the password encoder.
     * Uses BCrypt with default strength (10 rounds).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures the authentication provider.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Exposes the authentication manager bean.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Configures CORS for frontend access.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:8080"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
