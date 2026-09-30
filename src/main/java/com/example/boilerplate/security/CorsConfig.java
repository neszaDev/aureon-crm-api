package com.example.boilerplate.security;

import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.web.cors.*;

@Configuration
public class CorsConfig {
  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(properties.allowedOrigins());
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
    var s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return s;
  }
}
