package net.austizz.ultimatebankingsystem.diagnostics;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID)
public final class HologramDiagnostic {
    private static final String ROOT_TAG = "ubs_hologram_diag";
    private static final String MARKER_TAG = "ubs_hologram_diag_marker";
    private static final String VARIANT_TAG_PREFIX = "ubs_hologram_diag_variant_";
    private static final double PALLET_LABEL_Y_OFFSET = 3.35D;
    private static final double LINE_SPACING = 3.0D;
    private static final ConcurrentHashMap<UUID, Set<String>> SEEN_BY_PLAYER = new ConcurrentHashMap<>();

    private static final List<Variant> VARIANTS = List.of(
            new Variant("A", "TextDisplay normal", false, true, 0x66000000, 0.0D, ChatFormatting.GREEN),
            new Variant("B", "TextDisplay see-through", true, true, 0x66000000, 0.0D, ChatFormatting.AQUA),
            new Variant("C", "Normal no shadow", false, false, 0x66000000, 0.0D, ChatFormatting.YELLOW),
            new Variant("D", "Normal no background", false, true, 0x00000000, 0.0D, ChatFormatting.LIGHT_PURPLE),
            new Variant("E", "See-through no background", true, true, 0x00000000, 0.0D, ChatFormatting.GOLD),
            new Variant("F", "Normal one block higher", false, true, 0x66000000, 1.0D, ChatFormatting.WHITE)
    );

    private HologramDiagnostic() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(buildRoot());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot() {
        return Commands.literal("ubsdiag")
                .then(Commands.literal("holograms")
                        .executes(context -> spawnAtSource(context.getSource()))
                        .then(Commands.literal("here")
                                .executes(context -> spawnAtSource(context.getSource()))
                        )
                        .then(Commands.literal("at")
                                .then(Commands.argument("base", Vec3Argument.vec3())
                                        .executes(context -> spawnAtBase(
                                                context.getSource(),
                                                Vec3Argument.getVec3(context, "base")
                                        ))
                                )
                        )
                        .then(Commands.literal("report")
                                .executes(context -> reportSeen(context.getSource()))
                        )
                        .then(Commands.literal("clear")
                                .executes(context -> clearDiagnostics(context.getSource(), true))
                        )
                );
    }

    private static int spawnAtSource(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        Vec3 base = player == null ? source.getPosition() : new Vec3(player.getX(), Math.floor(player.getY()), player.getZ());
        return spawnAtBase(source, base);
    }

    private static int spawnAtBase(CommandSourceStack source, Vec3 base) {
        ServerLevel level = source.getLevel();
        clearDiagnostics(source, false);
        int spawned = 0;
        for (int i = 0; i < VARIANTS.size(); i++) {
            Variant variant = VARIANTS.get(i);
            Vec3 labelPos = new Vec3(
                    base.x + (i * LINE_SPACING),
                    base.y + PALLET_LABEL_Y_OFFSET + variant.extraYOffset(),
                    base.z
            );
            if (spawnVariant(level, labelPos, variant)) {
                spawned++;
            }
        }

        int spawnedFinal = spawned;
        source.sendSuccess(() -> Component.literal("Spawned " + spawnedFinal + " UBS hologram diagnostic variants.")
                .withStyle(ChatFormatting.GREEN), false);
        source.sendSystemMessage(Component.literal("Right-click or left-click every visible marker. Then run /ubsdiag holograms report.")
                .withStyle(ChatFormatting.YELLOW));
        source.sendSystemMessage(Component.literal("Base: " + formatVec(base) + " | label Y starts at base + " + PALLET_LABEL_Y_OFFSET)
                .withStyle(ChatFormatting.GRAY));
        return spawnedFinal;
    }

    private static boolean spawnVariant(ServerLevel level, Vec3 labelPos, Variant variant) {
        Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
        Interaction marker = EntityType.INTERACTION.create(level);
        if (display == null || marker == null) {
            return false;
        }

        configureTextDisplay(display, level, labelPos, variant);
        configureInteraction(marker, labelPos, variant);
        level.addFreshEntity(display);
        level.addFreshEntity(marker);
        return true;
    }

    private static void configureTextDisplay(Display.TextDisplay display,
                                             ServerLevel level,
                                             Vec3 labelPos,
                                             Variant variant) {
        display.setPos(labelPos);
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.putString("text", Component.Serializer.toJson(variantText(variant, labelPos), level.registryAccess()));
        tag.putInt("line_width", 260);
        tag.putByte("text_opacity", (byte) 0xFF);
        tag.putInt("background", variant.background());
        tag.putBoolean("shadow", variant.shadow());
        tag.putBoolean("see_through", variant.seeThrough());
        tag.putBoolean("default_background", false);
        tag.putString("alignment", "center");
        tag.putString("billboard", "center");
        tag.putFloat("view_range", 96.0F);
        tag.putFloat("shadow_radius", 0.0F);
        tag.putFloat("shadow_strength", 0.0F);
        tag.putFloat("width", 2.75F);
        tag.putFloat("height", 1.4F);
        CompoundTag brightness = new CompoundTag();
        brightness.putInt("block", 15);
        brightness.putInt("sky", 15);
        tag.put("brightness", brightness);
        display.load(tag);
        display.setPos(labelPos);
        display.setNoGravity(true);
        display.setSilent(true);
        display.setInvulnerable(true);
        display.addTag(ROOT_TAG);
        display.addTag(VARIANT_TAG_PREFIX + variant.id().toLowerCase(Locale.ROOT));
        display.setCustomName(Component.literal("UBS Hologram Diagnostic " + variant.id()));
        display.setCustomNameVisible(false);
    }

    private static void configureInteraction(Interaction marker, Vec3 labelPos, Variant variant) {
        marker.setPos(labelPos.x, labelPos.y - 0.45D, labelPos.z);
        CompoundTag tag = marker.saveWithoutId(new CompoundTag());
        tag.putFloat("width", 2.75F);
        tag.putFloat("height", 1.60F);
        tag.putBoolean("response", true);
        marker.load(tag);
        marker.setPos(labelPos.x, labelPos.y - 0.45D, labelPos.z);
        marker.setNoGravity(true);
        marker.setSilent(true);
        marker.setInvulnerable(true);
        marker.addTag(ROOT_TAG);
        marker.addTag(MARKER_TAG);
        marker.addTag(VARIANT_TAG_PREFIX + variant.id().toLowerCase(Locale.ROOT));
        marker.setCustomName(Component.literal("Click " + variant.id() + " - " + variant.label()));
        marker.setCustomNameVisible(false);
    }

    private static Component variantText(Variant variant, Vec3 labelPos) {
        return Component.empty()
                .append(Component.literal(variant.id() + " ").withStyle(variant.color(), ChatFormatting.BOLD))
                .append(Component.literal(variant.label()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\nseeThrough=" + variant.seeThrough() + " shadow=" + variant.shadow()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n" + formatVec(labelPos)).withStyle(ChatFormatting.DARK_GRAY));
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (handleMarkerClick(event.getEntity(), event.getTarget(), "right-click")) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (handleMarkerClick(event.getEntity(), event.getTarget(), "left-click")) {
            event.setCanceled(true);
        }
    }

    private static boolean handleMarkerClick(Player player, Entity target, String action) {
        if (!(player instanceof ServerPlayer serverPlayer) || target == null || !target.getTags().contains(MARKER_TAG)) {
            return false;
        }
        String variantId = findVariantId(target);
        Variant variant = findVariant(variantId);
        if (variant == null) {
            return false;
        }

        Set<String> seen = SEEN_BY_PLAYER.computeIfAbsent(serverPlayer.getUUID(), ignored -> ConcurrentHashMap.newKeySet());
        boolean added = seen.add(variant.id());
        Component message = Component.literal("Recorded visible " + variant.id() + " - " + variant.label()
                        + " via " + action + ".")
                .withStyle(added ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        serverPlayer.sendSystemMessage(message);
        serverPlayer.sendSystemMessage(Component.literal("Seen so far: " + formatSeen(seen)).withStyle(ChatFormatting.GRAY));
        UltimateBankingSystem.LOGGER.info("[UBS-HOLOGRAM-DIAG] {} reported visible {} ({}) using {} at {}.",
                serverPlayer.getGameProfile().getName(),
                variant.id(),
                variant.label(),
                action,
                formatVec(target.position()));
        return true;
    }

    private static int reportSeen(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this report as a player."));
            return 0;
        }
        Set<String> seen = SEEN_BY_PLAYER.get(player.getUUID());
        source.sendSystemMessage(Component.literal("UBS hologram diagnostic seen markers: "
                        + (seen == null || seen.isEmpty() ? "none" : formatSeen(seen)))
                .withStyle(ChatFormatting.AQUA));
        source.sendSystemMessage(Component.literal("Tell Codex this exact list. Example: A, C, F.")
                .withStyle(ChatFormatting.YELLOW));
        return seen == null ? 0 : seen.size();
    }

    private static int clearDiagnostics(CommandSourceStack source, boolean notify) {
        int removed = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity != null && entity.getTags().contains(ROOT_TAG)) {
                    entity.discard();
                    removed++;
                }
            }
        }
        SEEN_BY_PLAYER.clear();
        if (notify) {
            int removedFinal = removed;
            source.sendSuccess(() -> Component.literal("Removed " + removedFinal + " UBS hologram diagnostic entities.")
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return removed;
    }

    private static String findVariantId(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag != null && tag.startsWith(VARIANT_TAG_PREFIX)) {
                return tag.substring(VARIANT_TAG_PREFIX.length()).toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }

    private static Variant findVariant(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (Variant variant : VARIANTS) {
            if (variant.id().equalsIgnoreCase(id)) {
                return variant;
            }
        }
        return null;
    }

    private static String formatSeen(Set<String> seen) {
        return seen.stream()
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    private static String formatVec(Vec3 vec) {
        if (vec == null) {
            return "(?, ?, ?)";
        }
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", vec.x, vec.y, vec.z);
    }

    private record Variant(String id,
                           String label,
                           boolean seeThrough,
                           boolean shadow,
                           int background,
                           double extraYOffset,
                           ChatFormatting color) {
    }
}
