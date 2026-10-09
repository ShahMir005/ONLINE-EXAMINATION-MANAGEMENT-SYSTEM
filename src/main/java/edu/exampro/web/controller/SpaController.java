package edu.exampro.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the three SPA shell pages.
 * All application state is managed client-side; these endpoints just
 * return the static HTML shells that bootstrap the JS router.
 *
 * Security: /login is permitAll in SpaSecurityConfig.
 *           /app and /error require an authenticated session.
 */
@Controller
public class SpaController {

    /** SPA login page — accessible before authentication. */
    @GetMapping("/login")
    public String loginPage(org.springframework.ui.Model model) {
        model.addAttribute("isDev", edu.exampro.service.DevSeedService.isDevelopment());
        return "login";
    }

    /** Root path redirects to main SPA application shell. */
    @GetMapping("/")
    public String root() {
        return "redirect:/app";
    }

    /**
     * Main SPA shell — serves app.html for all in-app routes.
     * The client-side hash router handles the sub-routes.
     */
    @GetMapping({"/app", "/app/**"})
    public String appShell() {
        return "app";
    }

    /** Generic error page. */
    @GetMapping("/error")
    public String errorPage() {
        return "error";
    }
}
