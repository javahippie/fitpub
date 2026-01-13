package net.javahippie.fitpub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for serving activity-related HTML pages
 */
@Controller
@RequestMapping("/activities")
public class ActivitiesViewController {

    /**
     * Show activities list page
     */
    @GetMapping
    public String listActivities() {
        return "activities/list";
    }

    /**
     * Show activity upload page
     */
    @GetMapping("/upload")
    public String uploadActivity() {
        return "activities/upload";
    }

    /**
     * Show activity detail page
     */
    @GetMapping("/{id}")
    public String viewActivity(@PathVariable String id) {
        // The activity data will be loaded via JavaScript API calls
        return "activities/detail";
    }

    /**
     * Show activity edit page
     */
    @GetMapping("/{id}/edit")
    public String editActivity(@PathVariable String id) {
        // The activity data will be loaded via JavaScript API calls
        return "activities/edit";
    }
}
