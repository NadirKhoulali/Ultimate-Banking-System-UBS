package net.austizz.ultimatebankingsystem.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.FriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.austizz.ultimatebankingsystem.compat.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.handling.IPayloadContext;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Bridges NeoForge-style payload registration/sending onto Forge 1.20.1 SimpleChannel.
 */
public final class ForgePayloadNetwork {
    public enum Direction {
        PLAY_TO_SERVER,
        PLAY_TO_CLIENT
    }

    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(UltimateBankingSystem.MODID, "payloads"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static final Map<ResourceLocation, Registration<?>> REGISTRATIONS = new ConcurrentHashMap<>();
    private static final Map<CustomPacketPayload.Type<?>, Registration<?>> REGISTRATIONS_BY_TYPE = new ConcurrentHashMap<>();
    private static boolean initialized = false;
    private static int discriminator = 0;

    private ForgePayloadNetwork() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        CHANNEL.registerMessage(
                discriminator++,
                ServerboundEnvelope.class,
                ServerboundEnvelope::encode,
                ServerboundEnvelope::decode,
                ForgePayloadNetwork::handleServerbound
        );
        CHANNEL.registerMessage(
                discriminator++,
                ClientboundEnvelope.class,
                ClientboundEnvelope::encode,
                ClientboundEnvelope::decode,
                ForgePayloadNetwork::handleClientbound
        );
        initialized = true;
    }

    public static synchronized <T extends CustomPacketPayload> void register(
            CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler,
            Direction direction
    ) {
        init();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(direction, "direction");

        ResourceLocation id = type.id();
        Registration<T> registration = new Registration<>(type, codec, handler, direction);
        Registration<?> existing = REGISTRATIONS.putIfAbsent(id, registration);
        if (existing != null) {
            UltimateBankingSystem.LOGGER.warn("[UBS] Duplicate payload registration ignored for {}", id);
            return;
        }
        REGISTRATIONS_BY_TYPE.put(type, registration);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        Registration<CustomPacketPayload> registration = findRegistration(payload);
        if (registration == null) {
            UltimateBankingSystem.LOGGER.warn("[UBS] Tried to send unregistered payload to server: {}", payload.type());
            return;
        }
        byte[] data = encodePayload(payload, registration.codec);
        CHANNEL.sendToServer(new ServerboundEnvelope(payload.type().id(), data));
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player == null) {
            return;
        }
        Registration<CustomPacketPayload> registration = findRegistration(payload);
        if (registration == null) {
            UltimateBankingSystem.LOGGER.warn("[UBS] Tried to send unregistered payload to player: {}", payload.type());
            return;
        }
        byte[] data = encodePayload(payload, registration.codec);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientboundEnvelope(payload.type().id(), data));
    }

    @SuppressWarnings("unchecked")
    private static Registration<CustomPacketPayload> findRegistration(CustomPacketPayload payload) {
        if (payload == null || payload.type() == null) {
            return null;
        }
        Registration<?> byType = REGISTRATIONS_BY_TYPE.get(payload.type());
        if (byType != null) {
            return (Registration<CustomPacketPayload>) byType;
        }
        Registration<?> byId = REGISTRATIONS.get(payload.type().id());
        return (Registration<CustomPacketPayload>) byId;
    }

    private static void handleServerbound(ServerboundEnvelope envelope, Supplier<NetworkEvent.Context> supplier) {
        handleEnvelope(envelope.typeId, envelope.payloadData, supplier, Direction.PLAY_TO_SERVER);
    }

    private static void handleClientbound(ClientboundEnvelope envelope, Supplier<NetworkEvent.Context> supplier) {
        handleEnvelope(envelope.typeId, envelope.payloadData, supplier, Direction.PLAY_TO_CLIENT);
    }

    private static void handleEnvelope(
            ResourceLocation typeId,
            byte[] payloadData,
            Supplier<NetworkEvent.Context> supplier,
            Direction expectedDirection
    ) {
        NetworkEvent.Context context = supplier.get();
        Registration<?> registration = REGISTRATIONS.get(typeId);
        if (registration == null) {
            UltimateBankingSystem.LOGGER.warn("[UBS] Received unknown payload id {}", typeId);
            context.setPacketHandled(true);
            return;
        }
        if (registration.direction != expectedDirection) {
            UltimateBankingSystem.LOGGER.warn(
                    "[UBS] Ignoring payload {} due to direction mismatch. Expected {}, registered {}",
                    typeId, expectedDirection, registration.direction
            );
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> invokeHandler(registration, payloadData, context));
        context.setPacketHandled(true);
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void invokeHandler(
            Registration<?> registration,
            byte[] payloadData,
            NetworkEvent.Context context
    ) {
        Registration<T> typed = (Registration<T>) registration;
        T payload = decodePayload(typed.codec, payloadData);
        if (payload == null) {
            return;
        }
        typed.handler.accept(payload, new PayloadContext(context));
    }

    private static <T extends CustomPacketPayload> byte[] encodePayload(
            T payload,
            StreamCodec<RegistryFriendlyByteBuf, T> codec
    ) {
        ByteBuf raw = Unpooled.buffer();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(raw);
        try {
            codec.encode(buf, payload);
            byte[] out = new byte[buf.readableBytes()];
            buf.getBytes(0, out);
            return out;
        } finally {
            buf.release();
        }
    }

    private static <T extends CustomPacketPayload> T decodePayload(
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            byte[] payloadData
    ) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(payloadData));
        try {
            return codec.decode(buf);
        } catch (Exception ex) {
            UltimateBankingSystem.LOGGER.error("[UBS] Failed to decode payload", ex);
            return null;
        } finally {
            buf.release();
        }
    }

    private record Registration<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler,
            Direction direction
    ) {
    }

    private record PayloadContext(NetworkEvent.Context forgeContext) implements IPayloadContext {
        @Override
        public Player player() {
            return forgeContext.getSender();
        }

        @Override
        public void enqueueWork(Runnable work) {
            if (work != null) {
                forgeContext.enqueueWork(work);
            }
        }
    }

    private record ServerboundEnvelope(ResourceLocation typeId, byte[] payloadData) {
        private static void encode(ServerboundEnvelope envelope, FriendlyByteBuf buf) {
            buf.writeResourceLocation(envelope.typeId);
            buf.writeByteArray(envelope.payloadData);
        }

        private static ServerboundEnvelope decode(FriendlyByteBuf buf) {
            return new ServerboundEnvelope(buf.readResourceLocation(), buf.readByteArray());
        }
    }

    private record ClientboundEnvelope(ResourceLocation typeId, byte[] payloadData) {
        private static void encode(ClientboundEnvelope envelope, FriendlyByteBuf buf) {
            buf.writeResourceLocation(envelope.typeId);
            buf.writeByteArray(envelope.payloadData);
        }

        private static ClientboundEnvelope decode(FriendlyByteBuf buf) {
            return new ClientboundEnvelope(buf.readResourceLocation(), buf.readByteArray());
        }
    }
}
