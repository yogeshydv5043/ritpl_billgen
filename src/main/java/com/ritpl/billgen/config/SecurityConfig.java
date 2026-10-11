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
    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().hasAnyRole("ADMIN", "FINANCE"))
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error", "/plans", "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/portal", "/portal/**").hasRole("CUSTOMER")
                .requestMatchers("/admin/plans", "/admin/plans/**").hasRole("ADMIN")
                .requestMatchers("/dashboard", "/customers/**", "/invoices/**", "/products/**").hasAnyRole("ADMIN", "FINANCE")
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated())
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
            if (principal instanceof AppUserPrincipal user && "CUSTOMER".equals(user.getRole())) {
                response.sendRedirect(request.getContextPath() + "/portal");
            } else {
                response.sendRedirect(request.getContextPath() + "/dashboard");
            }
        };
    }
}
