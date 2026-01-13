package net.javahippie.fitpub.security;

import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateFormatTest {

    @Test
    public void testRFC1123DateFormat() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        // Old format (broken)
        DateTimeFormatter oldFormatter = DateTimeFormatter.RFC_1123_DATE_TIME;
        String oldDate = now.format(oldFormatter);
        System.out.println("OLD RFC 1123 Date (broken): " + oldDate);

        // New format (correct)
        DateTimeFormatter newFormatter = DateTimeFormatter.ofPattern(
            "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
            java.util.Locale.US
        );
        String newDate = now.format(newFormatter);
        System.out.println("NEW RFC 1123 Date (correct): " + newDate);

        // Mastodon expects format like: "Mon, 02 Dec 2024 20:48:33 GMT"
        // Note the zero-padded day "02" not "2"
    }
}
