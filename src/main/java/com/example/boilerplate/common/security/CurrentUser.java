package com.example.boilerplate.common.security;

import org.springframework.security.core.Authentication;

/**
 * The principal JwtAuthenticationFilter sets: the token's user, checked against the database for
 * this request. Look the caller up by {@link #id()}; the email can change under a session.
 */
public record CurrentUser(long id, String email) {

  /** The signed-in caller's user id (an authenticated route only). */
  public static long id(Authentication authentication) {
    return ((CurrentUser) authentication.getPrincipal()).id();
  }
}
