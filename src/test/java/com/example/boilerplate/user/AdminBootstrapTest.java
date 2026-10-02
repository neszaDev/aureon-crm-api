package com.example.boilerplate.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {
  static final String PASSWORD = "a-long-admin-password";

  @Mock UserRepository users;
  @Mock PasswordEncoder encoder;

  @Test
  void doesNothingWithoutAnEmail() {
    bootstrap(null, null).run(null);
    bootstrap(" ", null).run(null);
    verifyNoInteractions(users, encoder);
  }

  @Test
  void createsTheAdminWhenMissing() {
    when(users.findByEmail("admin@example.com")).thenReturn(Optional.empty());
    when(encoder.encode(PASSWORD)).thenReturn("encoded");
    when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

    bootstrap("Admin@Example.com", PASSWORD).run(null);

    var saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
    assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
    assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded");
  }

  @Test
  void neverPromotesOrChangesAnExistingAccount() {
    User existing = new User("admin@example.com", "someone-elses-hash");
    when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(existing));

    bootstrap("admin@example.com", PASSWORD).run(null);

    assertThat(existing.getRole()).isEqualTo(Role.USER);
    assertThat(existing.getPasswordHash()).isEqualTo("someone-elses-hash");
    verify(users, never()).save(any());
  }

  @Test
  void rejectsAShortPasswordWithoutEchoingIt() {
    assertThatThrownBy(() -> bootstrap("admin@example.com", "short-pw").run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN_PASSWORD")
        .hasMessageNotContaining("short-pw");
    assertThatThrownBy(() -> bootstrap("admin@example.com", null).run(null))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(users);
  }

  private AdminBootstrap bootstrap(String email, String password) {
    return new AdminBootstrap(new AdminProperties(email, password), users, encoder);
  }
}
