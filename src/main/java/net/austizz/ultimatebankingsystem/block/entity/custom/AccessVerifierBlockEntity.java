package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.austizz.ultimatebankingsystem.network.AccessVerifierActionPayload;
import net.austizz.ultimatebankingsystem.network.AccessVerifierOpenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public class AccessVerifierBlockEntity extends BlockEntity {
    private static final int MIN_SIGNAL = 1;
    private static final int MAX_SIGNAL = 15;
    private static final int MIN_ATTEMPTS = 1;
    private static final int MAX_ATTEMPTS = 12;

    private boolean configured = false;
    private String pinHash = "";
    private int successSignal = 15;
    private int failSignal = 14;
    private int maxAttempts = 3;
    private int attemptsRemaining = 3;
    private boolean successCircuitActive = false;
    private boolean failCircuitActive = false;

    public AccessVerifierBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ACCESS_VERIFIER.get(), pos, blockState);
    }

    public int currentSignal() {
        if (successCircuitActive) {
            return successSignal;
        }
        if (failCircuitActive) {
            return failSignal;
        }
        return 0;
    }

    public void openFor(ServerPlayer player) {
        sendOpen(player, false, "", true);
    }

    public void handleAction(ServerPlayer player, AccessVerifierActionPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        String action = payload.action().trim().toUpperCase(Locale.ROOT);
        switch (action) {
            case "SETUP" -> setup(player, payload);
            case "LOGIN" -> login(player, payload.pin());
            case "TOGGLE_SUCCESS" -> toggleSuccessCircuit(player, payload.pin());
            case "STOP_FAIL" -> stopFailCircuit(player, payload.pin());
            case "SAVE_SETTINGS" -> saveSettings(player, payload);
            default -> sendOpen(player, false, "Unknown verifier action.", false);
        }
    }

    private void setup(ServerPlayer player, AccessVerifierActionPayload payload) {
        if (configured) {
            sendOpen(player, false, "PIN already configured.", false);
            return;
        }
        String newPin = sanitizePin(payload.newPin());
        if (!isValidPin(newPin)) {
            sendOpen(player, false, "PIN must be 4-12 digits.", false);
            return;
        }
        this.pinHash = hashPin(newPin);
        this.configured = true;
        this.successSignal = clampSignal(payload.successSignal(), 15);
        this.failSignal = clampSignal(payload.failSignal(), 14);
        this.maxAttempts = clampAttempts(payload.maxAttempts());
        this.attemptsRemaining = this.maxAttempts;
        this.successCircuitActive = false;
        this.failCircuitActive = false;
        markUpdated();
        sendOpen(player, true, "PIN set. Controls unlocked.", true);
    }

    private void login(ServerPlayer player, String pin) {
        if (!configured) {
            sendOpen(player, false, "Set a PIN first.", false);
            return;
        }
        if (matchesPin(pin)) {
            this.attemptsRemaining = maxAttempts;
            markUpdated();
            sendOpen(player, true, "Access granted.", true);
            return;
        }
        registerFailedAttempt();
        sendOpen(player, false, failCircuitActive ? "Access denied. Fail circuit is active." : "Access denied.", false);
    }

    private void toggleSuccessCircuit(ServerPlayer player, String pin) {
        if (!requirePin(player, pin)) {
            return;
        }
        this.successCircuitActive = !successCircuitActive;
        if (successCircuitActive) {
            this.failCircuitActive = false;
            this.attemptsRemaining = maxAttempts;
        }
        markUpdated();
        updateRedstoneNeighbors();
        sendOpen(
                player,
                true,
                successCircuitActive ? "Success circuit opened." : "Success circuit closed.",
                true
        );
    }

    private void stopFailCircuit(ServerPlayer player, String pin) {
        if (!requirePin(player, pin)) {
            return;
        }
        this.failCircuitActive = false;
        this.attemptsRemaining = maxAttempts;
        markUpdated();
        updateRedstoneNeighbors();
        sendOpen(player, true, "Fail circuit stopped.", true);
    }

    private void saveSettings(ServerPlayer player, AccessVerifierActionPayload payload) {
        if (!requirePin(player, payload.pin())) {
            return;
        }
        this.successSignal = clampSignal(payload.successSignal(), successSignal);
        this.failSignal = clampSignal(payload.failSignal(), failSignal);
        this.maxAttempts = clampAttempts(payload.maxAttempts());
        this.attemptsRemaining = Math.min(Math.max(attemptsRemaining, 0), maxAttempts);
        if (attemptsRemaining == 0 && !failCircuitActive) {
            attemptsRemaining = maxAttempts;
        }
        markUpdated();
        updateRedstoneNeighbors();
        sendOpen(player, true, "Verifier settings saved.", true);
    }

    private boolean requirePin(ServerPlayer player, String pin) {
        if (!configured) {
            sendOpen(player, false, "Set a PIN first.", false);
            return false;
        }
        if (matchesPin(pin)) {
            this.attemptsRemaining = maxAttempts;
            markUpdated();
            return true;
        }
        registerFailedAttempt();
        sendOpen(player, false, failCircuitActive ? "PIN rejected. Fail circuit is active." : "PIN rejected.", false);
        return false;
    }

    private void registerFailedAttempt() {
        attemptsRemaining = Math.max(0, attemptsRemaining - 1);
        if (attemptsRemaining <= 0) {
            failCircuitActive = true;
            successCircuitActive = false;
            updateRedstoneNeighbors();
        }
        markUpdated();
    }

    private void sendOpen(ServerPlayer player, boolean authenticated, String message, boolean messageSuccess) {
        PacketDistributor.sendToPlayer(player, snapshot(authenticated, message, messageSuccess));
    }

    private AccessVerifierOpenPayload snapshot(boolean authenticated, String message, boolean messageSuccess) {
        Level level = getLevel();
        return new AccessVerifierOpenPayload(
                level == null ? "" : level.dimension().location().toString(),
                worldPosition.getX(),
                worldPosition.getY(),
                worldPosition.getZ(),
                configured,
                authenticated,
                successCircuitActive,
                failCircuitActive,
                successSignal,
                failSignal,
                maxAttempts,
                attemptsRemaining,
                message,
                messageSuccess
        );
    }

    private boolean matchesPin(String pin) {
        String clean = sanitizePin(pin);
        return configured && !pinHash.isBlank() && hashPin(clean).equals(pinHash);
    }

    private String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salt = "ultimatebankingsystem:access_verifier:" + worldPosition.asLong();
            byte[] bytes = digest.digest((salt + ":" + sanitizePin(pin)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String sanitizePin(String pin) {
        return pin == null ? "" : pin.trim();
    }

    private static boolean isValidPin(String pin) {
        return pin != null && pin.matches("\\d{4,12}");
    }

    private static int clampSignal(int value, int fallback) {
        int resolved = value <= 0 ? fallback : value;
        return Mth.clamp(resolved, MIN_SIGNAL, MAX_SIGNAL);
    }

    private static int clampAttempts(int value) {
        return Mth.clamp(value <= 0 ? 3 : value, MIN_ATTEMPTS, MAX_ATTEMPTS);
    }

    private void updateRedstoneNeighbors() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        level.updateNeighborsAt(worldPosition, state.getBlock());
        for (var direction : net.minecraft.core.Direction.values()) {
            level.updateNeighborsAt(worldPosition.relative(direction), state.getBlock());
        }
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.configured = tag.getBoolean("configured");
        this.pinHash = tag.getString("pin_hash");
        this.successSignal = clampSignal(tag.getInt("success_signal"), 15);
        this.failSignal = clampSignal(tag.getInt("fail_signal"), 14);
        this.maxAttempts = clampAttempts(tag.getInt("max_attempts"));
        this.attemptsRemaining = Mth.clamp(
                tag.contains("attempts_remaining") ? tag.getInt("attempts_remaining") : maxAttempts,
                0,
                maxAttempts
        );
        this.successCircuitActive = tag.getBoolean("success_circuit_active");
        this.failCircuitActive = tag.getBoolean("fail_circuit_active");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("configured", configured);
        tag.putString("pin_hash", pinHash == null ? "" : pinHash);
        tag.putInt("success_signal", successSignal);
        tag.putInt("fail_signal", failSignal);
        tag.putInt("max_attempts", maxAttempts);
        tag.putInt("attempts_remaining", attemptsRemaining);
        tag.putBoolean("success_circuit_active", successCircuitActive);
        tag.putBoolean("fail_circuit_active", failCircuitActive);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
