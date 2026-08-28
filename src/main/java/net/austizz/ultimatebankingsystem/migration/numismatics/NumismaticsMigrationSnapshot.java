package net.austizz.ultimatebankingsystem.migration.numismatics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.ModList;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record NumismaticsMigrationSnapshot(
        UUID sessionToken,
        String migrationId,
        String phase,
        String sourceKind,
        String sourcePath,
        String sourceHash,
        int centsPerSpur,
        String scope,
        boolean convertCards,
        boolean allowUnsafeRemoval,
        int sourceAccountCount,
        long sourceAccountSpurs,
        String sourceAccountValue,
        long physicalCoinItems,
        long physicalSpurs,
        String physicalValue,
        long boundCards,
        long blankCards,
        long idCards,
        int candidateChunks,
        int candidatePlayerFiles,
        List<String> warnings,
        List<String> blockers,
        String status,
        String failure,
        String feedback,
        boolean feedbackError,
        int progressCurrent,
        int progressTotal,
        boolean maintenanceLocked,
        boolean authoritativeScan,
        boolean sourceConsumed,
        String backupDirectory,
        int recoveryItems,
        boolean numismaticsLoaded
) {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public NumismaticsMigrationSnapshot {
        migrationId = safe(migrationId); phase = safe(phase); sourceKind = safe(sourceKind);
        sourcePath = safe(sourcePath); sourceHash = safe(sourceHash); scope = safe(scope);
        sourceAccountValue = safe(sourceAccountValue); physicalValue = safe(physicalValue);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        status = safe(status); failure = safe(failure); feedback = safe(feedback);
        backupDirectory = safe(backupDirectory);
    }

    public static NumismaticsMigrationSnapshot from(NumismaticsMigrationSavedData data, UUID token) {
        return from(data, token, "", false);
    }

    public static NumismaticsMigrationSnapshot from(NumismaticsMigrationSavedData data, UUID token,
                                                     String feedback, boolean feedbackError) {
        NumismaticsPreflightResult preflight = data.preflight();
        int rate = data.options().centsPerSpur();
        return new NumismaticsMigrationSnapshot(
                token,
                data.migrationId() == null ? "" : data.migrationId().toString(),
                data.phase().name(),
                data.sourceKind().name(),
                data.sourcePath(),
                data.sourceHash(),
                rate,
                data.options().scope().name(),
                data.options().convertBankCards(),
                data.options().allowUnsafeAccountOnlyRemoval(),
                data.sourceAccountCount(),
                data.sourceAccountSpurs(),
                dollars(data.sourceAccountSpurs(), rate),
                preflight.coinItems(),
                preflight.physicalSpurs(),
                dollars(preflight.physicalSpurs(), rate),
                preflight.boundBankCards(),
                preflight.blankBankCards(),
                preflight.idCards(),
                preflight.candidateChunks().size(),
                preflight.candidatePlayerFiles().size(),
                preflight.warnings(),
                preflight.blockers(),
                data.statusMessage(),
                data.failureMessage(),
                feedback,
                feedbackError,
                data.progressCurrent(),
                data.progressTotal(),
                data.maintenanceLocked(),
                data.authoritativeScanComplete(),
                data.sourceBalancesConsumed(),
                data.backupDirectory(),
                data.recoveryItems().size(),
                ModList.get().isLoaded("numismatics")
        );
    }

    public String toJson() { return GSON.toJson(this); }
    public static NumismaticsMigrationSnapshot fromJson(String json) { return GSON.fromJson(json, NumismaticsMigrationSnapshot.class); }

    private static String dollars(long spurs, int centsPerSpur) {
        try {
            return BigDecimal.valueOf(Math.multiplyExact(spurs, (long) centsPerSpur), 2).toPlainString();
        } catch (ArithmeticException overflow) {
            return "OVERFLOW";
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
