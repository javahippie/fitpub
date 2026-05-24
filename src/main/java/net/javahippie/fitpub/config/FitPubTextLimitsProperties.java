package net.javahippie.fitpub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable max-length limits for user and activity text fields.
 */
@Data
@ConfigurationProperties(prefix = "fitpub")
public class FitPubTextLimitsProperties {

    private User user = new User();
    private Activity activity = new Activity();

    @Data
    public static class User {
        private Bio bio = new Bio();
    }

    @Data
    public static class Bio {
        private int maxLength = 500;
    }

    @Data
    public static class Activity {
        private Title title = new Title();
        private Description description = new Description();
    }

    @Data
    public static class Title {
        private int maxLength = 200;
    }

    @Data
    public static class Description {
        private int maxLength = 5000;
    }
}
