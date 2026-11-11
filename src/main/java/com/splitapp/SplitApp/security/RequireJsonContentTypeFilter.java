package com.splitapp.SplitApp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Rejects POST requests that are not declared as JSON.
 *
 * <p>CSRF protection is off, and browsers resend cached Basic credentials on their own.
 * A cross-site HTML form (or a no-cors fetch) can only send a POST with a form content
 * type, text/plain or no content type at all. Requiring application/json makes such a
 * request fail, because any other way to send JSON triggers a CORS preflight, which
 * this API does not allow. Other methods (PUT, PATCH, DELETE) cannot be sent by a form.
 */
public class RequireJsonContentTypeFilter extends OncePerRequestFilter {

    private static final String BODY = "{\"type\":\"about:blank\","
            + "\"title\":\"Unsupported Media Type\",\"status\":415,"
            + "\"detail\":\"POST requests must have the Content-Type application/json\"}";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.POST.matches(request.getMethod()) && !hasJsonContentType(request)) {
            response.setStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(BODY);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean hasJsonContentType(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_JSON.equalsTypeAndSubtype(
                    MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }
}
