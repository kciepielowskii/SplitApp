package com.splitapp.splitapp.security;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Runs a test as a logged-in user whose principal is an {@link AppUserDetails}. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@WithSecurityContext(factory = WithMockAppUserFactory.class)
public @interface WithMockAppUser {
    long id() default 1L;

    String email() default "user@example.com";
}
