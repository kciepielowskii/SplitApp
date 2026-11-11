package com.splitapp.SplitApp.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithMockAppUserFactory implements WithSecurityContextFactory<WithMockAppUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockAppUser annotation) {
        AppUserDetails principal = new AppUserDetails(annotation.id(), annotation.email(), "hash");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, "hash", principal.getAuthorities()));
        return context;
    }
}
