package net.austizz.ultimatebankingsystem.migration.numismatics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NumismaticsMigrationReport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private NumismaticsMigrationReport() {
    }

    public static Path write(MinecraftServer server, NumismaticsMigrationSavedData journal) throws IOException {
        if (journal.migrationId() == null) throw new IOException("Migration ID is unavailable.");
        Path root = server.getWorldPath(LevelResource.ROOT).resolve("ubs-migrations")
                .resolve("numismatics").resolve(journal.migrationId().toString()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("migrationId", journal.migrationId().toString());
        report.put("generatedAt", Instant.now().toString());
        report.put("phase", journal.phase().name());
        report.put("sourceKind", journal.sourceKind().name());
        report.put("sourceSha256", journal.sourceHash());
        report.put("centsPerSpur", journal.options().centsPerSpur());
        report.put("scope", journal.options().scope().name());
        report.put("sourceAccounts", journal.sourceAccountCount());
        report.put("sourceAccountSpurs", journal.sourceAccountSpurs());
        report.put("physicalCoinItems", journal.preflight().coinItems());
        report.put("physicalSpurs", journal.preflight().physicalSpurs());
        report.put("boundCards", journal.preflight().boundBankCards());
        report.put("blankCards", journal.preflight().blankBankCards());
        report.put("idCardsNotConverted", journal.preflight().idCards());
        report.put("accountMappings", journal.accountMappings());
        report.put("completedPlayerFiles", journal.completedPlayerFiles());
        report.put("completedChunks", journal.completedChunks());
        report.put("recoveryItems", journal.recoveryItems().size());
        report.put("warnings", journal.preflight().warnings());
        report.put("audit", journal.auditEntries());
        Path json = root.resolve("migration-report.json");
        Files.writeString(json, GSON.toJson(report), StandardCharsets.UTF_8);

        StringBuilder csv = new StringBuilder("source_account_id,ubs_account_id\n");
        journal.accountMappings().forEach((source, target) -> csv.append(source).append(',').append(target).append('\n'));
        Files.writeString(root.resolve("account-mappings.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("REMOVE-NUMISMATICS.txt"),
                "Migration completed. Stop the server, remove Create: Numismatics, then restart.\n"
                        + "Keep this directory and its verified backup until the converted world has been checked.\n",
                StandardCharsets.UTF_8);
        return json;
    }
}
