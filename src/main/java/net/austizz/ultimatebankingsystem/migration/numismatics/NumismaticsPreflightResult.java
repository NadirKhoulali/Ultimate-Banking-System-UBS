package net.austizz.ultimatebankingsystem.migration.numismatics;

import java.util.List;

public record NumismaticsPreflightResult(
        long coinItems,
        long physicalSpurs,
        long boundBankCards,
        long blankBankCards,
        long idCards,
        List<ChunkRef> candidateChunks,
        List<String> candidatePlayerFiles,
        List<String> affectedFiles,
        List<String> warnings,
        List<String> blockers
) {
    public NumismaticsPreflightResult {
        candidateChunks = candidateChunks == null ? List.of() : List.copyOf(candidateChunks);
        candidatePlayerFiles = candidatePlayerFiles == null ? List.of() : List.copyOf(candidatePlayerFiles);
        affectedFiles = affectedFiles == null ? List.of() : List.copyOf(affectedFiles);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public static NumismaticsPreflightResult empty() {
        return new NumismaticsPreflightResult(0, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public record ChunkRef(String dimension, int x, int z) {
        public ChunkRef {
            dimension = dimension == null ? "" : dimension.trim();
        }
    }
}
