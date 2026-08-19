package com.realestate.config;

import com.realestate.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Core Spring Security configuration.
 *
 * Key decisions:
 *  - Stateless sessions (JWT — no server-side session storage)
 *  - CSRF disabled (safe for stateless REST APIs with JWT)
 *  - Public routes: auth endpoints, GET property search, Swagger
 *  - Protected routes: everything else requires a valid JWT
 *  - Admin routes: require ROLE_ADMIN (enforced via @PreAuthorize too)
 *
 * @EnableMethodSecurity enables @PreAuthorize on controller methods,
 * giving us fine-grained per-method role checks on top of URL rules.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Use our CorsConfig bean
            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            // Disable CSRF — not needed for stateless JWT APIs
            .csrf(AbstractHttpConfigurer::disable)

            // No sessions — every request must carry a JWT
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Route-level access rules ──────────────────────────
            .authorizeHttpRequests(auth -> auth

                // Auth endpoints — always public
                .requestMatchers("/auth/**").permitAll()

                // Swagger UI — public in dev, blocked in prod via app config
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()

                // Actuator health check — public (used by load balancers)
                .requestMatchers("/actuator/health").permitAll()

                // Public property browsing — anyone can search and view
                .requestMatchers("/uploads/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/properties/**").permitAll()
                // Inquiries are public — allow guest submissions
                .requestMatchers(HttpMethod.POST, "/properties/*/inquiries").permitAll()
                // Site-visit bookings — guests may also book without an account
                .requestMatchers(HttpMethod.POST, "/properties/*/site-visits").permitAll()
                // Reporting a listing — guests too; an account wall would mean the
                // listings most worth flagging get flagged least. Rate-limited instead.
                .requestMatchers(HttpMethod.POST, "/properties/*/reports").permitAll()
                .requestMatchers(HttpMethod.GET, "/search/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/cities/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/localities/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/agents/**").permitAll()
                // Public seller/agent profile — carries no phone and no email (see UserDtos)
                .requestMatchers(HttpMethod.GET, "/users/*/public").permitAll()

                // Admin routes — only ADMIN role
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // Everything else requires a valid JWT
                .anyRequest().authenticated()
            )

            // Register our JWT filter before Spring's default auth filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // Custom 401 response (instead of Spring's default redirect to /login)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"status\":401,\"error\":\"Unauthorized\"," +
                        "\"message\":\"Authentication required. Please provide a valid token.\"}"
                    );
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"status\":403,\"error\":\"Forbidden\"," +
                        "\"message\":\"You don't have permission to access this resource.\"}"
                    );
                })
            );

        return http.build();
    }

    // ── Beans ───────────────────────────────────────────────────

    /**
     * Wires UserDetailsService + BCrypt password encoder together.
     * Spring Security uses this during login to validate credentials.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposed as a bean so AuthService can call authenticate() directly.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt with strength 12 — strong enough for production,
     * fast enough to not noticeably slow down login.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
