package com.example.boilerplate.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A response field computed from others (a full name, an overdue flag, a count): {@link
 * AuditChanges#recordDiff} leaves it out, so a change shows only what was edited.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Derived {}
