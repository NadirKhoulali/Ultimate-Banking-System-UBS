package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record OwnerPcBankDataPayload(
        UUID bankId,
        String bankName,
        String status,
        String ownerName,
        String ownershipModel,
        String color,
        String motto,
        String reserve,
        String deposits,
        String reserveRatio,
        String minReserve,
        String accountsCount,
        String dailyCap,
        String dailyUsed,
        String dailyRemaining,
        String singleLimit,
        String dailyPlayerLimit,
        String dailyBankLimit,
        String tellerLimit,
        String cardIssueFee,
        String cardReplacementFee,
        String federalFundsRate,
        String bankLevel,
        String bankLevelDerived,
        String bankLevelManual,
        String bankLevelProgressRatio,
        String bankLevelNextDepositTarget,
        String bankLevelNextAccountTarget,
        List<String> bankLevelRoadmap,
        boolean ownerView,
        List<String> roles,
        List<String> shares,
        List<String> cofounders,
        List<String> employees,
        List<String> loanProducts,
        List<String> interbankOffers,
        List<String> interbankLoans,
        List<String> accountRoster,
        List<String> certificateSchedule,
        String safeAreaCount,
        String safeRowCapacity,
        String safeClaimedRowUnits,
        String safeTotalBoxSlots,
        String safeAssignedBoxes,
        String safeFreeBoxes,
        String safeLockedBoxes,
        String safeEscrowCases,
        String safePolicyMode,
        String safePolicyAmount,
        String safeRentPeriodTicks,
        String safeOverdueTicks,
        List<String> safeAreaSummaries,
        List<String> safeBoxAssignments,
        List<String> safeLockedQueue,
        List<OwnerPcPlayerEmployeePayload> playerEmployees,
        List<OwnerPcBankTellerPayload> bankTellers,
        List<OwnerPcVaultSetupPayload> vaultSetups,
        OwnerPcSetupObjectivePayload safeSetupObjective,
        List<OwnerPcPremisePayload> premises,
        int viewingRoomCapacity,
        int bankTellerCapacity,
        List<OwnerPcViewingRoomPayload> viewingRooms,
        List<OwnerPcSafeAccessLogPayload> safeAccessLogs,
        OwnerPcSafeAlarmPayload safeAlarm,
        List<OwnerPcVaultStorageClaimPayload> vaultStorageClaims
) implements CustomPacketPayload {

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        buf.writeLong(uuid.getMostSignificantBits());
                        buf.writeLong(uuid.getLeastSignificantBits());
                    },
                    buf -> new UUID(buf.readLong(), buf.readLong())
            );

    public static final Type<OwnerPcBankDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "owner_pc_bank_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcBankDataPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        UUID_CODEC.encode(buf, payload.bankId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.bankName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.status());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.ownerName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.ownershipModel());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.color());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.motto());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.reserve());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.deposits());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.reserveRatio());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.minReserve());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.accountsCount());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dailyCap());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dailyUsed());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dailyRemaining());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.singleLimit());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dailyPlayerLimit());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dailyBankLimit());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.tellerLimit());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.cardIssueFee());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.cardReplacementFee());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.federalFundsRate());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.bankLevel());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.bankLevelDerived());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.bankLevelManual());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.bankLevelProgressRatio());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.bankLevelNextDepositTarget());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.bankLevelNextAccountTarget());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).encode(buf, payload.bankLevelRoadmap());
                        ByteBufCodecs.BOOL.encode(buf, payload.ownerView());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.roles());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.shares());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.cofounders());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.employees());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.loanProducts());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.interbankOffers());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).encode(buf, payload.interbankLoans());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).encode(buf, payload.accountRoster());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).encode(buf, payload.certificateSchedule());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safeAreaCount());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safeRowCapacity());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safeClaimedRowUnits());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safeTotalBoxSlots());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safeAssignedBoxes());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safeFreeBoxes());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safeLockedBoxes());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safeEscrowCases());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safePolicyMode());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safePolicyAmount());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safeRentPeriodTicks());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.safeOverdueTicks());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).encode(buf, payload.safeAreaSummaries());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).encode(buf, payload.safeBoxAssignments());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).encode(buf, payload.safeLockedQueue());
                        OwnerPcPlayerEmployeePayload.STREAM_CODEC.apply(ByteBufCodecs.list(256))
                                .encode(buf, payload.playerEmployees());
                        OwnerPcBankTellerPayload.STREAM_CODEC.apply(ByteBufCodecs.list(256))
                                .encode(buf, payload.bankTellers());
                        OwnerPcVaultSetupPayload.STREAM_CODEC.apply(ByteBufCodecs.list(512))
                                .encode(buf, payload.vaultSetups());
                        OwnerPcSetupObjectivePayload.STREAM_CODEC.encode(buf, payload.safeSetupObjective());
                        OwnerPcPremisePayload.STREAM_CODEC.apply(ByteBufCodecs.list(256))
                                .encode(buf, payload.premises());
                        buf.writeVarInt(payload.viewingRoomCapacity());
                        buf.writeVarInt(payload.bankTellerCapacity());
                        OwnerPcViewingRoomPayload.STREAM_CODEC.apply(ByteBufCodecs.list(256))
                                .encode(buf, payload.viewingRooms());
                        OwnerPcSafeAccessLogPayload.STREAM_CODEC.apply(ByteBufCodecs.list(256))
                                .encode(buf, payload.safeAccessLogs());
                        OwnerPcSafeAlarmPayload.STREAM_CODEC.encode(buf, payload.safeAlarm());
                        OwnerPcVaultStorageClaimPayload.STREAM_CODEC.apply(ByteBufCodecs.list(128))
                                .encode(buf, payload.vaultStorageClaims());
                    },
                    buf -> new OwnerPcBankDataPayload(
                            UUID_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(256)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)).decode(buf),
                            OwnerPcPlayerEmployeePayload.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                            OwnerPcBankTellerPayload.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                            OwnerPcVaultSetupPayload.STREAM_CODEC.apply(ByteBufCodecs.list(512)).decode(buf),
                            OwnerPcSetupObjectivePayload.STREAM_CODEC.decode(buf),
                            OwnerPcPremisePayload.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            OwnerPcViewingRoomPayload.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                            OwnerPcSafeAccessLogPayload.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                            OwnerPcSafeAlarmPayload.STREAM_CODEC.decode(buf),
                            OwnerPcVaultStorageClaimPayload.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf)
                    )
            );

    public OwnerPcBankDataPayload {
        bankLevelRoadmap = copy(bankLevelRoadmap);
        roles = copy(roles);
        shares = copy(shares);
        cofounders = copy(cofounders);
        employees = copy(employees);
        loanProducts = copy(loanProducts);
        interbankOffers = copy(interbankOffers);
        interbankLoans = copy(interbankLoans);
        accountRoster = copy(accountRoster);
        certificateSchedule = copy(certificateSchedule);
        safeAreaSummaries = copy(safeAreaSummaries);
        safeBoxAssignments = copy(safeBoxAssignments);
        safeLockedQueue = copy(safeLockedQueue);
        playerEmployees = playerEmployees == null ? List.of() : List.copyOf(playerEmployees);
        bankTellers = bankTellers == null ? List.of() : List.copyOf(bankTellers);
        vaultSetups = vaultSetups == null ? List.of() : List.copyOf(vaultSetups);
        safeSetupObjective = safeSetupObjective == null
                ? OwnerPcSetupObjectivePayload.unavailable()
                : safeSetupObjective;
        premises = premises == null ? List.of() : List.copyOf(premises);
        viewingRoomCapacity = Math.max(0, viewingRoomCapacity);
        bankTellerCapacity = Math.max(0, bankTellerCapacity);
        viewingRooms = viewingRooms == null ? List.of() : List.copyOf(viewingRooms);
        safeAccessLogs = safeAccessLogs == null ? List.of() : List.copyOf(safeAccessLogs);
        safeAlarm = safeAlarm == null
                ? new OwnerPcSafeAlarmPayload(true, "minecraft:block.note_block.bell",
                2.0F, 0.55F, 0.8F, 40, false, "", 0)
                : safeAlarm;
        vaultStorageClaims = vaultStorageClaims == null ? List.of() : List.copyOf(vaultStorageClaims);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    @Override
    public Type<OwnerPcBankDataPayload> type() {
        return TYPE;
    }
}
