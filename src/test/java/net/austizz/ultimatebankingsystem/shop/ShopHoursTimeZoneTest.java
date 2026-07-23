package net.austizz.ultimatebankingsystem.shop;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopHoursTimeZoneTest {
    private static final long JULY_REFERENCE = Instant.parse("2026-07-22T12:00:00Z").toEpochMilli();

    @Test
    void convertsServerHoursIntoViewerLocalTime() {
        List<ShopHoursTimeZone.DisplayWindow> result = ShopHoursTimeZone.convertWeek(
                List.of(new ShopHoursTimeZone.ScheduleWindow("MON", 9 * 60, 21 * 60)),
                ZoneId.of("Europe/Amsterdam"),
                ZoneId.of("America/New_York"),
                JULY_REFERENCE);

        assertEquals(1, result.size());
        assertEquals("MON", result.getFirst().sourceDayKey());
        assertEquals("MON", result.getFirst().displayDayKey());
        assertEquals(3 * 60, result.getFirst().openMinute());
        assertEquals(15 * 60, result.getFirst().closeMinute());
    }

    @Test
    void shiftsTheDisplayedWeekdayAcrossTheDateLine() {
        List<ShopHoursTimeZone.DisplayWindow> result = ShopHoursTimeZone.convertWeek(
                List.of(new ShopHoursTimeZone.ScheduleWindow("MON", 20 * 60, 22 * 60)),
                ZoneId.of("America/Los_Angeles"),
                ZoneId.of("Asia/Tokyo"),
                JULY_REFERENCE);

        assertEquals("MON", result.getFirst().sourceDayKey());
        assertEquals("TUE", result.getFirst().displayDayKey());
        assertEquals(12 * 60, result.getFirst().openMinute());
        assertEquals(14 * 60, result.getFirst().closeMinute());
    }

    @Test
    void keepsOvernightAndAllDayWindowsIntact() {
        List<ShopHoursTimeZone.DisplayWindow> result = ShopHoursTimeZone.convertWeek(
                List.of(
                        new ShopHoursTimeZone.ScheduleWindow("MON", 22 * 60, 2 * 60),
                        new ShopHoursTimeZone.ScheduleWindow("TUE", 0, 0)
                ),
                ZoneId.of("UTC"),
                ZoneId.of("Asia/Tokyo"),
                JULY_REFERENCE);

        ShopHoursTimeZone.DisplayWindow overnight = result.stream()
                .filter(window -> "MON".equals(window.sourceDayKey()))
                .findFirst().orElseThrow();
        ShopHoursTimeZone.DisplayWindow allDay = result.stream()
                .filter(window -> "TUE".equals(window.sourceDayKey()))
                .findFirst().orElseThrow();
        assertEquals("TUE", overnight.displayDayKey());
        assertEquals(7 * 60, overnight.openMinute());
        assertEquals(11 * 60, overnight.closeMinute());
        assertEquals(allDay.openMinute(), allDay.closeMinute());
    }

    @Test
    void describesTheActualZoneAndCurrentOffset() {
        String description = ShopHoursTimeZone.describeZone(
                ZoneId.of("Europe/Amsterdam"), JULY_REFERENCE);

        assertTrue(description.startsWith("Europe/Amsterdam ("));
        assertTrue(description.contains("UTC+02:00"));
    }
}
