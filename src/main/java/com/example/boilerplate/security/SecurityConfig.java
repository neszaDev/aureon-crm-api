package com.example.boilerplate.security;

import com.example.boilerplate.common.exception.ApiError;
import com.example.boilerplate.common.web.RequestPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.RequestRejectedHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * A URL the firewall rejects ({@code //}, {@code ;x=1}, an encoded {@code ..}) is a 400 with the
   * usual body. Without this the rejection reached the container's error dispatch: a 401.
   */
  @Bean
  RequestRejectedHandler requestRejectedHandler(ObjectMapper json) {
    return (request, response, e) -> {
      HttpStatus status = HttpStatus.BAD_REQUEST;
      response.setStatus(status.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      json.writeValue(
          response.getOutputStream(),
          new ApiError(
              Instant.now(),
              status.value(),
              status.getReasonPhrase(),
              null,
              "Malformed request",
              RequestPaths.safePath(request),
              Map.of()));
    };
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
                    // Invitation links: preview and sign-up need the link's 256-bit token, not an
                    // account. Accepting as an existing account stays authenticated.
                    .requestMatchers(HttpMethod.GET, "/api/v1/invitations/*")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/invitations/*/register")
                    .permitAll()
                    .requestMatchers("/api/v1/users/me", "/api/v1/users/me/password")
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
