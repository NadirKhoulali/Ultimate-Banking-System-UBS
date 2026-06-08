package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Syncs the owner's guided shop setup objective card to the client HUD.
 */
public record ShopSetupObjectivePayload(
        boolean active,
        String shopName,
        int step,
        int totalSteps,
        String objectiveTitle,
        String objectiveDetail,
        List<RequirementProgress> requirements
) implements CustomPacketPayload {

    public static final Type<ShopSetupObjectivePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "shop_setup_objective"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSetupObjectivePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ShopSetupObjectivePayload decode(RegistryFriendlyByteBuf buffer) {
                    boolean active = buffer.readBoolean();
                    String shopName = buffer.readUtf(72);
                    int step = buffer.readVarInt();
                    int totalSteps = buffer.readVarInt();
                    String objectiveTitle = buffer.readUtf(128);
                    String objectiveDetail = buffer.readUtf(384);
                    int requirementCount = Math.max(0, Math.min(8, buffer.readVarInt()));
                    List<RequirementProgress> requirements = new ArrayList<>(requirementCount);
                    for (int i = 0; i < requirementCount; i++) {
                        requirements.add(RequirementProgress.STREAM_CODEC.decode(buffer));
                    }
                    return new ShopSetupObjectivePayload(
                            active,
                            shopName,
                            step,
                            totalSteps,
                            objectiveTitle,
                            objectiveDetail,
                            requirements
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, ShopSetupObjectivePayload payload) {
                    ShopSetupObjectivePayload safe = payload == null ? inactive() : payload;
                    buffer.writeBoolean(safe.active());
                    buffer.writeUtf(safe.shopName());
                    buffer.writeVarInt(safe.step());
                    buffer.writeVarInt(safe.totalSteps());
                    buffer.writeUtf(safe.objectiveTitle());
                    buffer.writeUtf(safe.objectiveDetail());
                    List<RequirementProgress> requirements = safe.requirements() == null ? List.of() : safe.requirements();
                    int count = Math.min(8, requirements.size());
                    buffer.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        RequirementProgress.STREAM_CODEC.encode(buffer, requirements.get(i));
                    }
                }
            };

    public ShopSetupObjectivePayload {
        shopName = trimTo(shopName, 72);
        objectiveTitle = trimTo(objectiveTitle, 128);
        objectiveDetail = trimTo(objectiveDetail, 384);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        totalSteps = Math.max(1, Math.min(32, totalSteps));
        step = Math.max(1, Math.min(totalSteps, step));
    }

    public ShopSetupObjectivePayload(boolean active,
                                     String shopName,
                                     int step,
                                     int totalSteps,
                                     String objectiveTitle,
                                     String objectiveDetail) {
        this(active, shopName, step, totalSteps, objectiveTitle, objectiveDetail, List.of());
    }

    public static ShopSetupObjectivePayload inactive() {
        return new ShopSetupObjectivePayload(false, "", 1, 1, "", "", List.of());
    }

    public record RequirementProgress(String itemName,
                                      int current,
                                      int needed) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RequirementProgress> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, RequirementProgress::itemName,
                        ByteBufCodecs.VAR_INT, RequirementProgress::current,
                        ByteBufCodecs.VAR_INT, RequirementProgress::needed,
                        RequirementProgress::new
                );

        public RequirementProgress {
            itemName = trimTo(itemName, 72);
            current = Math.max(0, current);
            needed = Math.max(1, needed);
        }

        public boolean complete() {
            return current >= needed;
        }
    }

    private static String trimTo(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength));
    }

    @Override
    public Type<ShopSetupObjectivePayload> type() {
        return TYPE;
    }
}
