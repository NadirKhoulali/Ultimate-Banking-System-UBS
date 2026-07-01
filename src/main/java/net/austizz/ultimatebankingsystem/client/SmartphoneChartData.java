package net.austizz.ultimatebankingsystem.client;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

final class SmartphoneChartData {
    private SmartphoneChartData() {
    }

    static ChartSeries buildStatisticsChartSeries(List<SmartphoneClientState.PhoneTransaction> txs,
                                                  int x, int y, int w, int h) {
        int baseline = y + Math.round(h * 0.72F);
        if (txs == null || txs.isEmpty()) {
            return new ChartSeries(new int[]{x, x + w}, new int[]{baseline, baseline}, baseline, x, baseline, false);
        }

        YearMonth month = txs.stream()
                .filter(tx -> tx != null && tx.timestampMillis() > 0L)
                .max(Comparator.comparingLong(SmartphoneClientState.PhoneTransaction::timestampMillis))
                .map(tx -> YearMonth.from(transactionDate(tx)))
                .orElseGet(() -> YearMonth.from(transactionDate(txs.get(0))));
        int daysInMonth = Math.max(1, month.lengthOfMonth());
        int visibleDays = month.equals(YearMonth.now())
                ? clamp(LocalDate.now().getDayOfMonth(), 1, daysInMonth)
                : daysInMonth;

        double[] dailyNet = new double[daysInMonth];
        int lastActivityDay = 1;
        for (SmartphoneClientState.PhoneTransaction tx : txs) {
            if (tx == null) {
                continue;
            }
            LocalDate date = transactionDate(tx);
            if (!YearMonth.from(date).equals(month)) {
                continue;
            }
            int day = clamp(date.getDayOfMonth(), 1, daysInMonth);
            dailyNet[day - 1] += signedMoney(tx);
            lastActivityDay = Math.max(lastActivityDay, day);
        }
        visibleDays = Math.max(2, Math.min(daysInMonth, Math.max(visibleDays, lastActivityDay)));

        double[] cumulative = new double[visibleDays];
        double running = 0.0D;
        double min = 0.0D;
        double max = 0.0D;
        boolean hasMovement = false;
        for (int i = 0; i < visibleDays; i++) {
            running += dailyNet[i];
            cumulative[i] = running;
            min = Math.min(min, running);
            max = Math.max(max, running);
            hasMovement = hasMovement || Math.abs(dailyNet[i]) > 0.0001D;
        }
        if (!hasMovement || Math.abs(max - min) < 0.0001D) {
            return new ChartSeries(new int[]{x, x + w}, new int[]{baseline, baseline}, baseline, x, baseline, false);
        }

        int[] px = new int[visibleDays];
        int[] py = new int[visibleDays];
        int top = y + Math.round(h * 0.08F);
        int bottom = y + h - Math.round(h * 0.12F);
        int zeroY = scaleChartValue(0.0D, min, max, top, bottom);
        int markerIndex = Math.max(0, Math.min(visibleDays - 1, lastActivityDay - 1));
        for (int i = 0; i < visibleDays; i++) {
            px[i] = x + Math.round((i / (float) (visibleDays - 1)) * w);
            py[i] = scaleChartValue(cumulative[i], min, max, top, bottom);
        }
        return new ChartSeries(px, py, zeroY, px[markerIndex], py[markerIndex], true);
    }

    private static int scaleChartValue(double value, double min, double max, int top, int bottom) {
        double range = Math.max(0.0001D, max - min);
        double normalized = (value - min) / range;
        return clamp(bottom - (int) Math.round(normalized * (bottom - top)), top, bottom);
    }

    private static double signedMoney(SmartphoneClientState.PhoneTransaction tx) {
        if (tx == null) {
            return 0.0D;
        }
        double value = parseMoney(tx.amount());
        if ("OUT".equalsIgnoreCase(tx.direction())) {
            return -Math.abs(value);
        }
        if ("IN".equalsIgnoreCase(tx.direction())) {
            return Math.abs(value);
        }
        return value;
    }

    private static LocalDate transactionDate(SmartphoneClientState.PhoneTransaction tx) {
        long millis = tx == null ? 0L : tx.timestampMillis();
        if (millis <= 0L) {
            return LocalDate.now();
        }
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static double parseMoney(String value) {
        String raw = value == null ? "" : value.replace("$", "").replace(",", "").replace("+", "").trim();
        if (raw.isBlank()) {
            return 0.0D;
        }
        double multiplier = 1.0D;
        char last = raw.charAt(raw.length() - 1);
        if (last == 'K' || last == 'k') {
            multiplier = 1_000.0D;
            raw = raw.substring(0, raw.length() - 1).trim();
        } else if (last == 'M' || last == 'm') {
            multiplier = 1_000_000.0D;
            raw = raw.substring(0, raw.length() - 1).trim();
        } else if (last == 'B' || last == 'b') {
            multiplier = 1_000_000_000.0D;
            raw = raw.substring(0, raw.length() - 1).trim();
        }
        try {
            return Double.parseDouble(raw) * multiplier;
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record ChartSeries(int[] xs, int[] ys, int baselineY, int markerX, int markerY, boolean hasData) {
    }
}
