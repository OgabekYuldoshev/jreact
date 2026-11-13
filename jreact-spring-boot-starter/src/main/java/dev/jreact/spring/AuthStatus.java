package dev.jreact.spring;

import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;

/** Spring Security facts, kept apart from {@link RpcController} so that the starter works without Spring Security on the classpath. */
final class AuthStatus {
    private AuthStatus() {}

    /** Spring Security refused ({@code @PreAuthorize}...) and nobody is signed in: that is a 401 (sign in), not a 403 (not allowed). */
    static boolean deniedAnonymous(Throwable e) {
        return e instanceof org.springframework.security.access.AccessDeniedException && isAnonymous();
    }

    /** True when nobody is signed in (no authentication, or the anonymous one). */
    static boolean isAnonymous() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || new AuthenticationTrustResolverImpl().isAnonymous(authentication);
    }
}
