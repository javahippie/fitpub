package org.operaton.fitpub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for notifications-related web pages
 */
@Controller
public class NotificationsViewController {

    @GetMapping("/notifications")
    public String notifications() {
        return "notifications";
    }
}
