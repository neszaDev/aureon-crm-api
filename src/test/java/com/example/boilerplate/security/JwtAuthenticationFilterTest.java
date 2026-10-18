package com.example.boilerplate.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.boilerplate.auth.AuthProperties;
import com.example.boilerplate.auth.JwtService;
import com.example.boilerplate.common.security.CurrentUser;
import com.example.boilerplate.user.Role;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtAuthenticationFilterTest {
  private static final String SECRET = "0123456789abcdef0123456789abcdef";
  private final UserRepository users = mock(UserRepository.class);
  private final JwtAuthenticationFilter filter =
      new JwtAuthenticationFilter(
          new JwtService(
              new AuthProperties(
                  new AuthProperties.Jwt(SECRET, Duration.ofMinutes(15)),
                  Duration.ofDays(30),
                  true)),
          users);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void aCurrentTokenAuthenticatesItsUserWithTheRoleOfTheDatabase() throws Exception {
    User user = user(7L, "me@example.com");
    Instant issued = Instant.now();
    // The token still says ADMIN; the database says USER.
    Authentication auth = filter(token(7L, "me@example.com", "ADMIN", issued));

    assertThat(auth.getPrincipal()).isEqualTo(new CurrentUser(7L, "me@example.com"));
    assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    assertThat(user.getRole()).isEqualTo(Role.USER);
  }

  @Test
  void aTokenWithoutAUserIdStaysAnonymous() throws Exception {
    user(7L, "me@example.com");
    assertThat(filter(token(null, "me@example.com", "USER", Instant.now()))).isNull();
  }

  @Test
  void aTokenOfAUserWhoIsGoneStaysAnonymous() throws Exception {
    when(users.findById(7L)).thenReturn(Optional.empty());
    assertThat(filter(token(7L, "me@example.com", "USER", Instant.now()))).isNull();
  }

  @Test
  void aTokenWhoseEmailIsNoLongerItsUsersStaysAnonymous() throws Exception {
    user(7L, "renamed@example.com");
    assertThat(filter(token(7L, "me@example.com", "USER", Instant.now()))).isNull();
  }

  @Test
  void aTokenIssuedBeforeTheUsersSessionsEndedStaysAnonymous() throws Exception {
    User user = user(7L, "me@example.com");
    user.changePasswordHash("new-hash");
    Instant ended = user.getSessionsValidAfter();

    assertThat(filter(token(7L, "me@example.com", "USER", ended.minusSeconds(1)))).isNull();
    // The same second counts as after: the change's own new token is issued in it.
    assertThat(filter(token(7L, "me@example.com", "USER", ended))).isNotNull();
  }

  @Test
  void aTokenWithAUserIdThatIsNotANumberStaysAnonymous() throws Exception {
    String token =
        Jwts.builder()
            .subject("me@example.com")
            .claim(JwtService.USER_ID, "seven")
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    assertThat(filter(token)).isNull();
  }

  private User user(long id, String email) {
    User user = new User(email, "hash");
    ReflectionTestUtils.setField(user, "id", id);
    when(users.findById(id)).thenReturn(Optional.of(user));
    return user;
  }

  private static String token(Long uid, String email, String role, Instant issued) {
    return Jwts.builder()
        .subject(email)
        .claim(JwtService.USER_ID, uid)
        .claim("role", role)
        .issuedAt(Date.from(issued))
        .expiration(Date.from(Instant.now().plusSeconds(60)))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
        .compact();
  }

  private Authentication filter(String token) throws Exception {
    var req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer " + token);
    filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
    return SecurityContextHolder.getContext().getAuthentication();
  }
}
