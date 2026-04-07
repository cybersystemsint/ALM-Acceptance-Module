package com.zain.almksazain.utlities;

import org.springframework.scheduling.support.CronExpression;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class CronUtils {

    private CronUtils() { /* utility */ }

    public static boolean isValidCron(String expr) {
        if (expr == null || expr.isBlank()) return false;
        try {
            CronExpression.parse(expr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public static boolean isValidTimezone(String tz) {
        if (tz == null || tz.isBlank()) return false;
        try {
            ZoneId.of(tz); // accepts IANA ids and offsets like "+03:00"
            return true;
        } catch (DateTimeException e) {
            // allow "UTC+03:00" by normalizing to "+03:00"
            if (tz.toUpperCase(Locale.ROOT).startsWith("UTC") && tz.length() > 3) {
                try {
                    ZoneId.of(tz.substring(3)); // "UTC+03:00" -> "+03:00"
                    return true;
                } catch (DateTimeException ex) {
                    // invalid offset after normalization
                }
            }
            return false;
        }
    }

    /**
     * - "UTC+03:00" => "+03:00"
     */
    public static ZoneId normalizeZone(String tz) {
        if (tz == null || tz.isBlank()) return ZoneId.of("UTC");
        if (tz.toUpperCase(Locale.ROOT).startsWith("UTC") && tz.length() > 3) {
            tz = tz.substring(3); // "UTC+03:00" -> "+03:00"
        }
        return ZoneId.of(tz);
    }

    public static String timeToCronExpression(String time) {
        if (time == null || time.isBlank()) return null;

        // Try parse with seconds first, then without seconds
        try {
            DateTimeFormatter withSeconds = DateTimeFormatter.ofPattern("HH:mm:ss");
            java.time.LocalTime t = java.time.LocalTime.parse(time, withSeconds);
            int sec = t.getSecond();
            int min = t.getMinute();
            int hour = t.getHour();
            return String.format("%d %d %d * * *", sec, min, hour);
        } catch (DateTimeParseException ignored) { }

        try {
            DateTimeFormatter withoutSeconds = DateTimeFormatter.ofPattern("HH:mm");
            java.time.LocalTime t = java.time.LocalTime.parse(time, withoutSeconds);
            int sec = 0;
            int min = t.getMinute();
            int hour = t.getHour();
            return String.format("%d %d %d * * *", sec, min, hour);
        } catch (DateTimeParseException ignored) { }

        return null;
    }

    public static boolean isValidTimeString(String time) {
        return timeToCronExpression(time) != null;
    }
}