package com.example.boilerplate.security;

import com.example.boilerplate.auth.JwtService;
import com.example.boilerplate.common.security.CurrentUser;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A valid signature is not enough: the token's user ({@code uid}) must still exist, still have the
 * email in {@code sub}, and not have ended its sessions after the token was issued. Otherwise the
 * request stays anonymous (401 on a protected route). The role comes from the database.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  private final UserRepository users;

  public JwtAuthenticationFilter(JwtService jwt, UserRepository users) {
    this.jwt = jwt;
    this.users = users;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String h = req.getHeader("Authorization");
    if (h != null && h.startsWith("Bearer "))
      try {
        Claims claims = jwt.parse(h.substring(7));
        Long uid = claims.get(JwtService.USER_ID, Long.class);
        User user = uid == null ? null : users.findById(uid).orElse(null);
        if (user != null && current(user, claims)) {
          var auth =
              new UsernamePasswordAuthenticationToken(
                  new CurrentUser(user.getId(), user.getEmail()),
                  null,
                  List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      } catch (JwtException ignored) {
      }
    chain.doFilter(req, res);
  }

  private static boolean current(User user, Claims claims) {
    var issuedAt = claims.getIssuedAt();
    var validAfter = user.getSessionsValidAfter();
    return user.getEmail().equals(claims.getSubject())
        && issuedAt != null
        && (validAfter == null || !issuedAt.toInstant().isBefore(validAfter));
  }
}
