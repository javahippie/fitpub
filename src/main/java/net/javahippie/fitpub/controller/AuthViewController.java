package net.javahippie.fitpub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Controller for authentication-related web pages
 */
@Controller
public class AuthViewController {

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String register() {
        return "auth/register";
    }

    @PostMapping("/logout")
    public String logout() {
        // Logout is handled client-side (removing JWT token)
        // Redirect to home page
        return "redirect:/";
    }
}
