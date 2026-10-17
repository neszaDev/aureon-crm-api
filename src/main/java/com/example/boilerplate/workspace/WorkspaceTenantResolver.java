package com.example.boilerplate.workspace;

import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * The tenant of {@code @TenantId} entities is the current request's workspace. Without one it is
 * {@link #NONE}, which matches no row, and an insert fails on the workspace foreign key. There is
 * no root tenant: {@code isRoot} keeps its default, false.
 */
@Component
public class WorkspaceTenantResolver
    implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {
  public static final Long NONE = -1L;

  @Override
  public Long resolveCurrentTenantIdentifier() {
    return WorkspaceContext.current().map(Membership::workspaceId).orElse(NONE);
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return true;
  }

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
  }
}
