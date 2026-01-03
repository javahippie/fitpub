package org.operaton.fitpub.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom authentication entry point that handles unauthenticated requests.
 * - Redirects to /login for HTML page requests (browser navigation)
 * - Returns 403 Forbidden for API requests (AJAX, fetch calls)
 */
@Component
@Slf4j
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        String requestUri = request.getRequestURI();
        String accept = request.getHeader("Accept");

        log.debug("Unauthenticated request to {} with Accept: {}", requestUri, accept);

        // API requests should get 403 Forbidden
        if (requestUri.startsWith("/api/")) {
            log.debug("API request - returning 403 Forbidden");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
            return;
        }

        // Check if this is a JSON/API request based on Accept header
        if (accept != null && (accept.contains("application/json") ||
                               accept.contains("application/activity+json") ||
                               accept.contains("application/ld+json"))) {
            log.debug("JSON API request - returning 403 Forbidden");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
            return;
        }

        // HTML page requests should redirect to login
        log.debug("HTML page request - redirecting to /login");
        String redirectUrl = "/login?redirect=" + requestUri;
        response.sendRedirect(redirectUrl);
    }
}
