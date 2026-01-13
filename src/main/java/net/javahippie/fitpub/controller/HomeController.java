package net.javahippie.fitpub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for home page and general public pages
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/timeline";
    }

    @GetMapping("/heatmap")
    public String heatmap() {
        return "heatmap";
    }

    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }
}
