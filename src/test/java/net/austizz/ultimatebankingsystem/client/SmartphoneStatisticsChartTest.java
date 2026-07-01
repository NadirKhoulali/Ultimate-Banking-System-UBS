package net.austizz.ultimatebankingsystem.client;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartphoneStatisticsChartTest {
    @Test
    void chartSeriesUsesRealTransactionDatesAndSignedAmounts() {
        UUID accountId = UUID.randomUUID();
        List<SmartphoneClientState.PhoneTransaction> transactions = List.of(
                tx(accountId, LocalDate.of(2025, 1, 3), "+$100", "IN"),
                tx(accountId, LocalDate.of(2025, 1, 14), "$40", "OUT"),
                tx(accountId, LocalDate.of(2025, 1, 27), "+$25", "IN")
        );

        SmartphoneChartData.ChartSeries series = SmartphoneChartData.buildStatisticsChartSeries(
                transactions, 10, 20, 310, 180);

        assertTrue(series.hasData());
        assertEquals(31, series.xs().length);
        assertEquals(series.xs()[26], series.markerX());
        assertEquals(series.ys()[26], series.markerY());

        int day3Y = series.ys()[2];
        int day14Y = series.ys()[13];
        int day27Y = series.ys()[26];
        assertNotEquals(day3Y, day14Y);
        assertNotEquals(day14Y, day27Y);
        assertTrue(day3Y < day14Y, "Outgoing money should lower the visual balance line.");
        assertTrue(day27Y < day14Y, "Later income should raise the visual balance line again.");
    }

    private static SmartphoneClientState.PhoneTransaction tx(UUID accountId, LocalDate date, String amount, String direction) {
        long millis = date.atTime(12, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return new SmartphoneClientState.PhoneTransaction(accountId, UUID.randomUUID(), amount,
                "Test transaction", date.toString(), millis, direction, "", null, null);
    }
}
