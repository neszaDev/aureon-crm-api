package com.example.boilerplate.crm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.boilerplate.common.exception.FieldValidationException;
import com.example.boilerplate.support.WorkspaceTestSupport;
import com.example.boilerplate.workspace.Membership;
import com.example.boilerplate.workspace.WorkspaceContext;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A link in a request body must name a row of the caller's workspace. */
class RecordLinksIT extends WorkspaceTestSupport {
  @Autowired RecordLinks links;

  record Tenant(Membership member, long company, long source, long contact, long lead, long deal) {}

  private Tenant tenant() throws Exception {
    String email = uniqueEmail();
    String token = tokenFor(email);
    long w = createWorkspace(token, "Links");
    long company =
        body(send(token, post("/api/v1/workspaces/{w}/companies", w), Map.of("name", "Acme")))
            .get("id")
            .asLong();
    long source =
        body(send(token, get("/api/v1/workspaces/{w}/lead-sources", w))).get(0).get("id").asLong();
    long contact =
        body(send(token, post("/api/v1/workspaces/{w}/contacts", w), Map.of("firstName", "Ada")))
            .get("id")
            .asLong();
    long lead =
        body(send(token, post("/api/v1/workspaces/{w}/leads", w), Map.of("firstName", "Grace")))
            .get("id")
            .asLong();
    long deal =
        body(send(
                token,
                post("/api/v1/workspaces/{w}/deals", w),
                Map.of("name", "Renewal", "amount", "1")))
            .get("id")
            .asLong();
    return new Tenant(
        members.findMembership(w, email).orElseThrow(), company, source, contact, lead, deal);
  }

  @Test
  void linksOfTheWorkspacePassAndOthersAre400OnTheirField() throws Exception {
    Tenant a = tenant();
    Tenant b = tenant();

    passes(a, () -> links.company(a.company()));
    passes(a, () -> links.source(a.source()));
    passes(a, () -> links.contact(a.contact()));
    passes(a, () -> links.lead(a.lead()));
    passes(a, () -> links.deal(a.deal()));
    passes(a, () -> links.company(null));
    passes(a, () -> links.source(null));
    passes(a, () -> links.contact(null));
    passes(a, () -> links.lead(null));
    passes(a, () -> links.deal(null));

    rejected(a, "companyId", () -> links.company(b.company()));
    rejected(a, "sourceId", () -> links.source(b.source()));
    rejected(a, "contactId", () -> links.contact(b.contact()));
    rejected(a, "leadId", () -> links.lead(b.lead()));
    rejected(a, "dealId", () -> links.deal(b.deal()));
    rejected(a, "companyId", () -> links.company(Long.MAX_VALUE));
    rejected(a, "leadId", () -> links.lead(Long.MAX_VALUE));
    rejected(a, "dealId", () -> links.deal(Long.MAX_VALUE));
  }

  private static void passes(Tenant t, Runnable check) {
    assertThatCode(asCallable(t, check)).doesNotThrowAnyException();
  }

  private static void rejected(Tenant t, String field, Runnable check) {
    assertThatThrownBy(asCallable(t, check))
        .isInstanceOfSatisfying(
            FieldValidationException.class,
            e -> {
              assertThat(e.field()).isEqualTo(field);
              assertThat(e.getMessage()).isEqualTo("is not found in this workspace");
            });
  }

  private static ThrowingCallable asCallable(Tenant t, Runnable check) {
    return () ->
        WorkspaceContext.runAs(
            t.member(),
            () -> {
              check.run();
              return null;
            });
  }
}
