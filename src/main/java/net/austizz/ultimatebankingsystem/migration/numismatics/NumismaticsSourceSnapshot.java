package net.austizz.ultimatebankingsystem.migration.numismatics;

import java.nio.file.Path;
import java.util.List;

public record NumismaticsSourceSnapshot(
        Path sourcePath,
        String sha256,
        List<NumismaticsAccountRecord> accounts,
        long totalSpurs
) {
    public NumismaticsSourceSnapshot {
        sha256 = sha256 == null ? "" : sha256;
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
    }
}
