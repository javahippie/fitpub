package net.javahippie.fitpub.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/**
 * JWT authentication filter that validates JWT tokens on each request.
 * Extracts the token from the Authorization header and authenticates the user.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (jwt != null && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsername(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Set authentication for user: {}", username);
            }
        } catch (Exception e) {
            log.error("Could not set user authentication in security context", e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT token from cookies or Authorization header.
     * Priority: 1) Cookie, 2) Authorization header
     *
     * @param request the HTTP request
     * @return the JWT token or null if not found
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        // First, try to get JWT from cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String tokenFromCookie = Arrays.stream(cookies)
                    .filter(cookie -> "JWT_TOKEN".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);

            if (tokenFromCookie != null && !tokenFromCookie.isEmpty()) {
                log.debug("JWT token found in cookie");
                return tokenFromCookie;
            }
        }

        // Fallback to Authorization header (for API clients)
        String bearerToken = request.getHeader("Authorization");
        String tokenFromHeader = tokenProvider.resolveToken(bearerToken);
        if (tokenFromHeader != null) {
            log.debug("JWT token found in Authorization header");
        }
        return tokenFromHeader;
    }
}
