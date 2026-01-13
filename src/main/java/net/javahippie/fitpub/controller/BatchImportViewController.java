package net.javahippie.fitpub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * View controller for batch import pages.
 * Serves Thymeleaf templates for the batch import UI.
 */
@Controller
public class BatchImportViewController {

    /**
     * Displays the batch upload page where users can upload ZIP files
     * containing multiple activity files for batch processing.
     *
     * GET /batch-upload
     *
     * @return the batch upload view template
     */
    @GetMapping("/batch-upload")
    public String batchUploadPage() {
        return "activities/batch-upload";
    }
}
