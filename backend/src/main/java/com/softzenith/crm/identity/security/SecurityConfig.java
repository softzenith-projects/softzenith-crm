package com.softzenith.crm.identity.security;

import com.softzenith.crm.identity.StaffDirectory;
import com.softzenith.crm.shared.web.ProblemResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless API security. Staff authenticate with a JWT from our phone + code sign-in, Supabase Auth or (dev) the dev
 * login, verified by {@link StaffTokenDecoders}. Tenant, role and permissions come from our own tables, per request.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    static final String MEMBERSHIPS_PATH = "/api/v1/me/memberships";
    private static final String[] PUBLIC_PATHS =
            {"/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                    "/api/v1/public/**", "/api/v1/webhooks/**", "/api/v1/dev/**", "/api/v1/auth/**"};

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();

    /**
     * Its own filter chain, with no {@code oauth2ResourceServer} configured: a stray/expired Authorization
     * header from a signed-in staff browser tab must never 401 a public or dev-login request. Spring's
     * resource-server filter authenticates any presented bearer token before {@code permitAll()} is even
     * evaluated, so these paths have to avoid that filter entirely rather than just being allow-listed in it.
     */
    @Bean
    @Order(1)
    SecurityFilterChain publicSecurity(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(PUBLIC_PATHS)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurity(HttpSecurity http, StaffDirectory directory) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, e) -> {
                            // Expired / forged / wrong-issuer tokens: the reason is only in the log, never in the response.
                            log.info("401 {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
                            bearer.commence(request, response, e);
                        }))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> {
                            log.info("401 {} {}: no token", request.getMethod(), request.getRequestURI());
                            bearer.commence(request, response, ex);
                        })
                        .accessDeniedHandler((request, response, denied) -> {
                            log.warn("403 {} {}: {}", request.getMethod(), request.getRequestURI(), denied.getMessage());
                            ProblemResponses.write(response, HttpStatus.FORBIDDEN, "You do not have permission for this action");
                        }))
                .addFilterAfter(new StaffContextFilter(directory), BearerTokenAuthenticationFilter.class)
                .build();
    }
}
