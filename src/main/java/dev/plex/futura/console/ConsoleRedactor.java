package dev.plex.futura.console;

import java.util.regex.Pattern;

/*
 * The ConsoleRedactor class was intentionally created to allow configuration of redacting as needed,
 * but at the moment, this class serves to redact IP addresses and the configuration to redact things
 * will be introduced in a later version.
 */
public class ConsoleRedactor {

    private static final String IPV4_PATTERN = "(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)" + "(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)){3}";

    private static final Pattern IPV4_WITH_PORT = Pattern.compile("(?<![\\d.])" + IPV4_PATTERN + ":\\d{1,5}" + "(?!\\d)");

    private static final Pattern IPV4 = Pattern.compile("(?<![\\d.])" + IPV4_PATTERN + "(?![\\d.])");

    public static String redact(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = IPV4_WITH_PORT.matcher(input).replaceAll("[IP REDACTED]");
        return IPV4.matcher(result).replaceAll("[IP REDACTED]");
    }
}
