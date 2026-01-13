package net.javahippie.fitpub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * View controller for analytics pages.
 */
@Controller
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsViewController {

    @GetMapping("")
    public String analytics() {
        return "analytics/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "analytics/dashboard";
    }

    @GetMapping("/personal-records")
    public String personalRecords() {
        return "analytics/personal-records";
    }

    @GetMapping("/achievements")
    public String achievements() {
        return "analytics/achievements";
    }

    @GetMapping("/training-load")
    public String trainingLoad() {
        return "analytics/training-load";
    }

    @GetMapping("/summaries")
    public String summaries() {
        return "analytics/summaries";
    }
}
