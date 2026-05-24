package net.javahippie.fitpub.controller;

import lombok.RequiredArgsConstructor;
import net.javahippie.fitpub.service.TextValidationService;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for serving activity-related HTML pages
 */
@Controller
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivitiesViewController {

    private final TextValidationService textValidationService;

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
    public String uploadActivity(Model model) {
        model.addAttribute("activityTitleMaxLength", textValidationService.getActivityTitleMaxLength());
        model.addAttribute("activityDescriptionMaxLength", textValidationService.getActivityDescriptionMaxLength());
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
    public String editActivity(@PathVariable String id, Model model) {
        // The activity data will be loaded via JavaScript API calls
        model.addAttribute("activityTitleMaxLength", textValidationService.getActivityTitleMaxLength());
        model.addAttribute("activityDescriptionMaxLength", textValidationService.getActivityDescriptionMaxLength());
        return "activities/edit";
    }
}
