package com.example.boilerplate.workspace;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WorkspaceWebConfig implements WebMvcConfigurer {
  private final WorkspaceMemberRepository members;

  WorkspaceWebConfig(WorkspaceMemberRepository members) {
    this.members = members;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new WorkspaceAccessInterceptor(members))
        .addPathPatterns("/api/v1/workspaces/*", "/api/v1/workspaces/*/**");
  }
}
