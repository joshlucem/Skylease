/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {

    private static final long MINUTE = 60;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long MONTH = 30 * DAY;
    private static final long YEAR = 365 * DAY;

    // cap for input such as 999999999y
    public static final long MAX_SECONDS = 100 * YEAR;

    // "mo" before "m"
    private static final Pattern WHOLE = Pattern.compile("(?:\\d{1,9}(?:mo|s|m|h|d|y))+");
    private static final Pattern PART = Pattern.compile("(\\d{1,9})(mo|s|m|h|d|y)");
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private TimeUtil() {
    }

    public static long parseTime(String input) {
        if (input == null) {
            return 0;
        }
        String text = input.toLowerCase().trim();
        if (!WHOLE.matcher(text).matches()) {
            return 0;
        }

        long total = 0;
        Matcher matcher = PART.matcher(text);
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            // clamp as we go: many terms could otherwise overflow the sum
            total = Math.min(total + value * unitSeconds(matcher.group(2)), MAX_SECONDS);
        }
        return Math.min(total, MAX_SECONDS);
    }

    private static long unitSeconds(String unit) {
        return switch (unit) {
            case "s" -> 1;
            case "m" -> MINUTE;
            case "h" -> HOUR;
            case "d" -> DAY;
            case "mo" -> MONTH;
            default -> YEAR;
        };
    }

    public static String formatTime(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }

        long[] sizes = {YEAR, MONTH, DAY, HOUR, MINUTE, 1};
        String[] labels = {"y", "mo", "d", "h", "m", "s"};

        StringBuilder result = new StringBuilder();
        long remaining = seconds;
        for (int i = 0; i < sizes.length; i++) {
            long amount = remaining / sizes[i];
            remaining %= sizes[i];
            if (amount > 0) {
                if (result.length() > 0) {
                    result.append(' ');
                }
                result.append(amount).append(labels[i]);
            }
        }
        return result.toString();
    }

    public static String formatTimestamp(long epochMillis) {
        return DATE_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }
}
