package org.operaton.fitpub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for timeline view pages.
 */
@Controller
public class TimelineViewController {

    /**
     * Public timeline page - shows all public activities.
     *
     * @param model the model
     * @return timeline template
     */
    @GetMapping("/timeline")
    public String publicTimeline(Model model) {
        model.addAttribute("pageTitle", "Public Timeline");
        return "timeline/public";
    }

    /**
     * Federated timeline page - shows activities from followed users.
     * Requires authentication.
     *
     * @param model the model
     * @return timeline template
     */
    @GetMapping("/timeline/federated")
    public String federatedTimeline(Model model) {
        model.addAttribute("pageTitle", "Federated Timeline");
        return "timeline/federated";
    }

    /**
     * User timeline page - shows current user's own activities.
     * Requires authentication.
     *
     * @param model the model
     * @return timeline template
     */
    @GetMapping("/timeline/user")
    public String userTimeline(Model model) {
        model.addAttribute("pageTitle", "My Timeline");
        return "timeline/user";
    }
}
