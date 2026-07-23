package net.austizz.ultimatebankingsystem.bank.safebox.zone;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeExitSnapshot;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafePremiseMode;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SafeBoxZoneRecord(UUID bankId,
                                String premiseId,
                                SafePremiseMode mode,
                                SafeBlockBounds premiseBounds,
                                SafeExitSnapshot exit,
                                List<Area> safeAreas) {
    public SafeBoxZoneRecord {
        Objects.requireNonNull(bankId, "bankId");
        if (premiseId == null || premiseId.isBlank()) {
            throw new IllegalArgumentException("premiseId must not be blank");
        }
        premiseId = premiseId.trim();
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(premiseBounds, "premiseBounds");
        safeAreas = safeAreas == null ? List.of() : List.copyOf(safeAreas);
    }

    public record Area(String id, SafeBlockBounds bounds) {
        public Area {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("safe-area id must not be blank");
            }
            id = id.trim();
            Objects.requireNonNull(bounds, "bounds");
        }
    }
}
