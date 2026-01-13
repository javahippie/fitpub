package net.javahippie.fitpub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Controller for user profile view pages.
 */
@Controller
public class ProfileViewController {

    /**
     * Current user's profile page.
     * Shows own profile with edit capabilities.
     *
     * @param model the model
     * @return profile template
     */
    @GetMapping("/profile")
    public String myProfile(Model model) {
        model.addAttribute("pageTitle", "My Profile");
        return "profile/view";
    }

    /**
     * Profile edit page.
     * Allows user to edit their profile information.
     *
     * @param model the model
     * @return profile edit template
     */
    @GetMapping("/profile/edit")
    public String editProfile(Model model) {
        model.addAttribute("pageTitle", "Edit Profile");
        return "profile/edit";
    }

    /**
     * Settings page.
     * Allows user to access various settings.
     *
     * @param model the model
     * @return settings template
     */
    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("pageTitle", "Settings");
        return "settings";
    }

    /**
     * Public user profile page by username.
     * Shows public profile of any user.
     *
     * @param username the username
     * @param model the model
     * @return profile template
     */
    @GetMapping("/users/{username}")
    public String userProfile(@PathVariable String username, Model model) {
        model.addAttribute("pageTitle", "Profile - @" + username);
        model.addAttribute("username", username);
        return "profile/public";
    }

    /**
     * User followers page.
     * Shows list of people who follow this user.
     *
     * @param username the username
     * @param model the model
     * @return followers template
     */
    @GetMapping("/profile/{username}/followers")
    public String userFollowers(@PathVariable String username, Model model) {
        model.addAttribute("pageTitle", "Followers - @" + username);
        model.addAttribute("username", username);
        return "profile/followers";
    }

    /**
     * User following page.
     * Shows list of people this user follows.
     *
     * @param username the username
     * @param model the model
     * @return following template
     */
    @GetMapping("/profile/{username}/following")
    public String userFollowing(@PathVariable String username, Model model) {
        model.addAttribute("pageTitle", "Following - @" + username);
        model.addAttribute("username", username);
        return "profile/following";
    }

    /**
     * User discovery page.
     * Allows searching and browsing all users.
     *
     * @param model the model
     * @return discover template
     */
    @GetMapping("/discover")
    public String discoverUsers(Model model) {
        model.addAttribute("pageTitle", "Discover Users");
        return "users/discover";
    }
}
