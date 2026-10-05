package edu.exampro.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Validates that the authenticated user possesses the specific role required by the login portal.
 * If there is a role mismatch, it invalidates the session and redirects with an error parameter.
 */
public class RoleCheckingAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private final String requiredRole; // e.g. "ROLE_ADMIN", "ROLE_TEACHER", "ROLE_STUDENT"
    private final String targetDashboardUrl;
    private final String mismatchRedirectUrl;

    public RoleCheckingAuthenticationSuccessHandler(String requiredRole, String targetDashboardUrl, String mismatchRedirectUrl) {
        this.requiredRole = requiredRole;
        this.targetDashboardUrl = targetDashboardUrl;
        this.mismatchRedirectUrl = mismatchRedirectUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        boolean hasRole = authentication.getAuthorities().stream()
            .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(requiredRole));

        if (!hasRole) {
            // Reject authentication because account role does not match this login portal
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect(request.getContextPath() + mismatchRedirectUrl);
            return;
        }

        response.sendRedirect(request.getContextPath() + targetDashboardUrl);
    }
}
