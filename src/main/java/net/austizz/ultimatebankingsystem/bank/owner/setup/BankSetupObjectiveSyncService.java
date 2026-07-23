package net.austizz.ultimatebankingsystem.bank.owner.setup;

import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.network.BankSetupObjectivesPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcSetupObjectivePayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultSetupPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BankSetupObjectiveSyncService {
    private static final int TOTAL_STEPS = 5;

    private BankSetupObjectiveSyncService() {
    }

    public static BankSetupObjectivesPayload build(MinecraftServer server,
                                                   CentralBank centralBank,
                                                   UUID ownerId) {
        if (server == null || centralBank == null || ownerId == null) {
            return new BankSetupObjectivesPayload(List.of());
        }
        List<BankSetupObjectivesPayload.Project> projects = new ArrayList<>();
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank == null || !ownerId.equals(bank.getBankOwnerId())) {
                continue;
            }
            CompoundTag metadata = centralBank.getBankMetadata().get(bank.getBankId());
            BankSafeSetupPayloadBuilder.Result setup = BankSafeSetupPayloadBuilder.build(
                    server,
                    metadata == null ? new CompoundTag() : metadata
            );
            if (!setup.objective().ready()) {
                projects.add(toProject(bank, setup));
            }
        }
        projects.sort(Comparator.comparing(BankSetupObjectivesPayload.Project::projectName,
                String.CASE_INSENSITIVE_ORDER));
        return new BankSetupObjectivesPayload(projects);
    }

    private static BankSetupObjectivesPayload.Project toProject(Bank bank,
                                                                BankSafeSetupPayloadBuilder.Result setup) {
        OwnerPcSetupObjectivePayload objective = setup.objective();
        OwnerPcVaultSetupPayload bestVault = setup.vaults().stream()
                .min(Comparator.comparingInt(vault -> vault.missingReasons().size()))
                .orElse(null);
        Step step = resolveStep(objective, bestVault);
        return new BankSetupObjectivesPayload.Project(
                bank.getBankId().toString(),
                bank.getBankName(),
                step.number(),
                TOTAL_STEPS,
                step.title(),
                step.detail()
        );
    }

    private static Step resolveStep(OwnerPcSetupObjectivePayload objective,
                                    OwnerPcVaultSetupPayload vault) {
        if (objective.premiseCount() <= 0) {
            return step(1, "Claim the bank premises",
                    "Where: Bank Owner PC > Premises > Claim Premise. Select the bank building first so vault access can be enforced inside its premises.");
        }
        if (objective.vaultCount() <= 0 || vault == null) {
            return step(2, "Add a safe area",
                    "Where: Bank Owner PC > Safe > Claim Safe Area. Select the protected vault room inside the bank premises and save it.");
        }
        if ("MISSING".equalsIgnoreCase(vault.doorStatus())) {
            return step(3, "Install a Bank Vault Door",
                    "Place one complete Bank Vault Door inside the claimed safe area. Removing it temporarily disables every box in this vault without deleting assignments.");
        }
        if ("MISSING".equalsIgnoreCase(vault.rowStatus())) {
            return step(4, "Fill one deposit row",
                    "Place at least one deposit-row block inside the safe area and fill every slot in that row with assignable box doors.");
        }
        String room = vault.viewingRoomStatus() == null ? "" : vault.viewingRoomStatus().toUpperCase(Locale.ROOT);
        if (!room.equals("READY")) {
            return step(5, "Configure a private viewing room",
                    "Where: Bank Owner PC > Safe > Private Viewing Rooms. Claim a room in this premise, then capture its customer, teller, and deposit-box anchors.");
        }
        String fallback = objective.missingSteps().isEmpty()
                ? "Complete one ready safety-deposit vault."
                : objective.missingSteps().getFirst();
        return step(5, "Finish the vault setup", fallback);
    }

    private static Step step(int number, String title, String detail) {
        return new Step(number, title, detail);
    }

    private record Step(int number, String title, String detail) {
    }
}
