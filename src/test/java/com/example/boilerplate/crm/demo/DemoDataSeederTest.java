package com.example.boilerplate.crm.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.boilerplate.crm.activity.ActivityService;
import com.example.boilerplate.crm.company.CompanyService;
import com.example.boilerplate.crm.contact.ContactService;
import com.example.boilerplate.crm.deal.DealService;
import com.example.boilerplate.crm.lead.LeadConversionService;
import com.example.boilerplate.crm.lead.LeadService;
import com.example.boilerplate.crm.settings.PipelineStageService;
import com.example.boilerplate.crm.task.TaskService;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserRepository;
import com.example.boilerplate.workspace.WorkspaceMemberRepository;
import com.example.boilerplate.workspace.WorkspaceService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class DemoDataSeederTest {
  @Mock UserRepository users;
  @Mock PasswordEncoder encoder;
  @Mock WorkspaceService workspaces;
  @Mock WorkspaceMemberRepository members;
  @Mock PipelineStageService stages;
  @Mock CompanyService companies;
  @Mock ContactService contacts;
  @Mock LeadService leads;
  @Mock LeadConversionService conversions;
  @Mock DealService deals;
  @Mock TaskService tasks;
  @Mock ActivityService activities;
  @Mock JdbcTemplate jdbc;

  @Test
  void disabledDoesNothing() {
    seeder(new DemoProperties(false, "x")).run(new DefaultApplicationArguments());

    verifyNoInteractions(everything());
  }

  @Test
  void aBadPasswordStopsTheStartWithoutShowingIt() {
    DemoDataSeeder seeder = seeder(new DemoProperties(true, "short"));

    assertThatThrownBy(() -> seeder.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DEMO_PASSWORD")
        .hasMessageNotContaining("short");
    verifyNoInteractions(everything());
  }

  @Test
  void aLoginWithAnotherPasswordIsLeftOutWithAWarning(CapturedOutput output) {
    String password = "configured-demo-secret";
    when(jdbc.queryForList(anyString(), eq(Long.class), eq(DemoDataSeeder.WORKSPACE)))
        .thenReturn(List.of(7L));
    when(users.findByEmail(anyString()))
        .thenAnswer(i -> Optional.of(new User(i.getArgument(0), "hash of " + i.getArgument(0))));
    when(encoder.matches(eq(password), anyString()))
        .thenAnswer(i -> !i.getArgument(1).equals("hash of owner@demo.test"));

    seeder(new DemoProperties(true, password)).run(new DefaultApplicationArguments());

    assertThat(output)
        .contains("Demo login owner@demo.test does not take DEMO_PASSWORD")
        .contains(
            "Demo workspace 7: sign in as admin@demo.test, manager@demo.test, rep@demo.test"
                + " with DEMO_PASSWORD")
        .doesNotContain(password);
  }

  private DemoDataSeeder seeder(DemoProperties properties) {
    return new DemoDataSeeder(
        properties,
        users,
        encoder,
        workspaces,
        members,
        stages,
        companies,
        contacts,
        leads,
        conversions,
        deals,
        tasks,
        activities,
        jdbc);
  }

  private Object[] everything() {
    return new Object[] {
      users,
      encoder,
      workspaces,
      members,
      stages,
      companies,
      contacts,
      leads,
      conversions,
      deals,
      tasks,
      activities,
      jdbc
    };
  }
}
