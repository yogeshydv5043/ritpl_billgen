package com.ritpl.billgen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import com.ritpl.billgen.security.AppUserPrincipal;

@Configuration
public class SecurityConfig {

    /**
     * JSON API. Both roles may call it, but the services derive a {@code TenantScope} from the
     * authenticated principal, so a customer credential can only ever reach that customer's rows.
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().hasAnyRole("ADMIN", "CUSTOMER"))
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(errors -> errors
                    .accessDeniedHandler((request, response, exception) ->
                            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN)))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * Browser application. The URL space is split by role so that a customer can never reach an
     * administrative screen, and an administrator can never reach a customer screen.
     */
    @Bean
    @Order(2)
    SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error", "/error/**", "/plans", "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/portal/**").hasRole("CUSTOMER")
                .requestMatchers("/admin/**", "/dashboard", "/customers/**", "/invoices/**", "/products/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors.accessDeniedPage("/error/403"))
            .formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login")
                    .successHandler(roleSuccessHandler()).failureUrl("/login?error").permitAll())
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout").invalidateHttpSession(true).clearAuthentication(true).deleteCookies("SESSION", "JSESSIONID"))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation(fixation -> fixation.migrateSession()));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    AuthenticationSuccessHandler roleSuccessHandler() {
        return (request, response, authentication) -> {
            Object principal = authentication.getPrincipal();
            if (principal instanceof AppUserPrincipal user && !user.isAdmin()) {
                response.sendRedirect(request.getContextPath() + "/portal");
            } else {
                response.sendRedirect(request.getContextPath() + "/dashboard");
            }
        };
    }
}