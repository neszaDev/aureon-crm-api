package com.example.boilerplate.security;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwt,
      ApiAuthenticationEntryPoint entryPoint,
      ApiAccessDeniedHandler accessDenied)
      throws Exception {
    return http.csrf(c -> c.disable())
        .cors(c -> {})
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/**",
                        // /** also covers the liveness/readiness probe groups.
                        "/actuator/health/**",
                        "/actuator/info",
                        // Public for scrapers; restrict at the network/ingress level in prod.
                        "/actuator/prometheus",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/api/v1/users/me")
                    .authenticated()
                    // User management; UserAdminService re-checks the role in the database.
                    .requestMatchers("/api/v1/users", "/api/v1/users/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied))
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
