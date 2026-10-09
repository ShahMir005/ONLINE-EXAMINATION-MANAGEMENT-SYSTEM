package edu.exampro.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forces the deferred {@link CsrfToken} to be loaded on every request so that
 * the {@code CookieCsrfTokenRepository} writes the {@code XSRF-TOKEN} cookie
 * to the response.
 *
 * <p>Spring Security 6 introduced <em>deferred</em> CSRF token loading: the
 * cookie is only written when the token is actually accessed.  For a SPA that
 * reads the cookie on every page load we need the cookie to be present on
 * every response — including GET requests that never validate a CSRF token.
 * This filter calls {@code token.getToken()} which is enough to trigger the
 * write.
 *
 * <p>Register this filter <strong>after</strong> Spring Security's
 * {@code BasicAuthenticationFilter} (i.e. after the security filter chain has
 * resolved the authentication) so the repository has had a chance to set up the
 * token supplier.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        // Touching the token forces the deferred supplier to resolve and the
        // CookieCsrfTokenRepository to write the Set-Cookie header.
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
