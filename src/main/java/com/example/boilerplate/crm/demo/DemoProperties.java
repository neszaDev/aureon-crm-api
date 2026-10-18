package com.example.boilerplate.crm.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.demo.*}: whether {@link DemoDataSeeder} seeds the demo workspace (local and dev
 * profiles only) and the password of its four logins.
 */
@ConfigurationProperties("app.demo")
public record DemoProperties(boolean enabled, String password) {}
