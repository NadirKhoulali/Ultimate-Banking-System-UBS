package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.List;

public record SafePremiseSnapshot(String id,
                                  String bankId,
                                  SafeBlockBounds bounds,
                                  SafeExitSnapshot exit,
                                  SafePremiseMode mode,
                                  List<SafeAreaSnapshot> safeAreas) {
    public SafePremiseSnapshot {
        id = id == null ? "" : id;
        bankId = bankId == null ? "" : bankId;
        mode = mode == null ? SafePremiseMode.PUBLIC : mode;
        safeAreas = safeAreas == null ? List.of() : List.copyOf(safeAreas);
    }
}
