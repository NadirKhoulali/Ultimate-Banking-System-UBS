package net.austizz.ultimatebankingsystem.shop;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Converts recurring server-local shop windows into a viewer's current local week. */
public final class ShopHoursTimeZone {
    private static final DateTimeFormatter ZONE_ABBREVIATION =
            DateTimeFormatter.ofPattern("z", Locale.ROOT);

    private ShopHoursTimeZone() {
    }

    public record ScheduleWindow(String sourceDayKey, int openMinute, int closeMinute) {
        public ScheduleWindow {
            sourceDayKey = normalizeDayKey(sourceDayKey);
            openMinute = normalizeMinute(openMinute);
            closeMinute = normalizeMinute(closeMinute);
        }
    }

    public record DisplayWindow(String sourceDayKey,
                                String displayDayKey,
                                int openMinute,
                                int closeMinute,
                                boolean currentDay) {
    }

    public static List<DisplayWindow> convertWeek(List<ScheduleWindow> schedule,
                                                   ZoneId serverZone,
                                                   ZoneId displayZone,
                                                   long referenceEpochMillis) {
        if (schedule == null || schedule.isEmpty()) {
            return List.of();
        }
        ZoneId source = serverZone == null ? ZoneId.of("UTC") : serverZone;
        ZoneId target = displayZone == null ? source : displayZone;
        Instant reference = Instant.ofEpochMilli(Math.max(0L, referenceEpochMillis));
        ZonedDateTime serverNow = reference.atZone(source);
        LocalDate serverMonday = serverNow.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        DayOfWeek targetCurrentDay = reference.atZone(target).getDayOfWeek();

        List<DisplayWindow> converted = new ArrayList<>(schedule.size());
        for (ScheduleWindow window : schedule) {
            int sourceIndex = dayIndex(window.sourceDayKey());
            if (sourceIndex < 0) {
                continue;
            }
            LocalDate sourceDate = serverMonday.plusDays(sourceIndex);
            ZonedDateTime sourceOpen = atMinute(sourceDate, window.openMinute(), source);
            LocalDate closeDate = sourceDate;
            if (window.openMinute() == window.closeMinute()
                    || window.closeMinute() < window.openMinute()) {
                closeDate = closeDate.plusDays(1L);
            }
            ZonedDateTime sourceClose = atMinute(closeDate, window.closeMinute(), source);
            ZonedDateTime targetOpen = sourceOpen.withZoneSameInstant(target);
            ZonedDateTime targetClose = sourceClose.withZoneSameInstant(target);
            converted.add(new DisplayWindow(
                    window.sourceDayKey(),
                    dayKey(targetOpen.getDayOfWeek()),
                    minuteOfDay(targetOpen),
                    minuteOfDay(targetClose),
                    targetOpen.getDayOfWeek() == targetCurrentDay
            ));
        }
        converted.sort(Comparator
                .comparingInt((DisplayWindow window) -> dayIndex(window.displayDayKey()))
                .thenComparingInt(DisplayWindow::openMinute));
        return List.copyOf(converted);
    }

    public static ZoneId parseZone(String rawZoneId, ZoneId fallback) {
        ZoneId safeFallback = fallback == null ? ZoneId.of("UTC") : fallback;
        if (rawZoneId == null || rawZoneId.isBlank()) {
            return safeFallback;
        }
        try {
            return ZoneId.of(rawZoneId.trim());
        } catch (RuntimeException ignored) {
            return safeFallback;
        }
    }

    public static String describeZone(ZoneId zone, long epochMillis) {
        ZoneId safeZone = zone == null ? ZoneId.of("UTC") : zone;
        ZonedDateTime time = Instant.ofEpochMilli(Math.max(0L, epochMillis)).atZone(safeZone);
        String offset = time.getOffset().getId();
        if ("Z".equals(offset)) {
            offset = "+00:00";
        }
        return safeZone.getId() + " (" + ZONE_ABBREVIATION.format(time) + ", UTC" + offset + ")";
    }

    public static String normalizeDayKey(String rawDay) {
        if (rawDay == null || rawDay.isBlank()) {
            return "";
        }
        return switch (rawDay.trim().toUpperCase(Locale.ROOT).replace(".", "")) {
            case "MON", "MONDAY" -> "MON";
            case "TUE", "TUES", "TUESDAY" -> "TUE";
            case "WED", "WEDNESDAY" -> "WED";
            case "THU", "THUR", "THURS", "THURSDAY" -> "THU";
            case "FRI", "FRIDAY" -> "FRI";
            case "SAT", "SATURDAY" -> "SAT";
            case "SUN", "SUNDAY" -> "SUN";
            default -> "";
        };
    }

    private static ZonedDateTime atMinute(LocalDate date, int minute, ZoneId zone) {
        return date.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(zone);
    }

    private static int minuteOfDay(ZonedDateTime time) {
        return (time.getHour() * 60) + time.getMinute();
    }

    private static int normalizeMinute(int minute) {
        return Math.floorMod(minute, 1440);
    }

    private static int dayIndex(String dayKey) {
        return switch (normalizeDayKey(dayKey)) {
            case "MON" -> 0;
            case "TUE" -> 1;
            case "WED" -> 2;
            case "THU" -> 3;
            case "FRI" -> 4;
            case "SAT" -> 5;
            case "SUN" -> 6;
            default -> -1;
        };
    }

    private static String dayKey(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }
}
