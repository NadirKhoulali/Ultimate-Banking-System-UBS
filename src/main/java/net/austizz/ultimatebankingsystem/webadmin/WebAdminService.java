package net.austizz.ultimatebankingsystem.webadmin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.loan.AccountLoan;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankAppSummary;
import net.austizz.ultimatebankingsystem.network.OwnerPcDesktopDataPayload;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Embedded web admin server used by server owners to manage UBS online.
 *
 * <p>This service intentionally keeps all business mutations on the Minecraft
 * server thread and reuses existing UBS services (bank owner PC + shop/bank actions)
 * to avoid duplicate logic paths.</p>
 */
public final class WebAdminService {
    private static final AttributeKey<String> SESSION_ID_ATTR = AttributeKey.valueOf("ubs_webadmin_session");
    private static final int HTTP_MAX_BODY_BYTES = 1024 * 1024;
    private static final int MAX_AUDIT_ENTRIES = 200;
    private static final long HISTORY_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L;
    private static final long HISTORY_INTERVAL_MILLIS = 60L * 60L * 1000L;
    private static final String HISTORY_KIND = "webadmin_hourly_v1";
    private static final String DEFAULT_DIMENSION = "minecraft:overworld";
    private static final int MAX_PENDING_DELIVERIES_PER_PLAYER = 128;
    private static final byte[] NO_BYTES = new byte[0];

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final ChannelGroup webSocketChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final ConcurrentHashMap<String, UUID> impersonationBySession = new ConcurrentHashMap<>();
    private final Deque<AuditEntry> auditEntries = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<UUID, Deque<QueuedDelivery>> pendingDeliveriesByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, byte[]> vanillaAssetCache = new ConcurrentHashMap<>();
    private volatile Path localMinecraftClientJar;
    private volatile boolean localMinecraftClientJarResolved;

    private volatile MinecraftServer server;
    private volatile EventLoopGroup bossGroup;
    private volatile EventLoopGroup workerGroup;
    private volatile Channel serverChannel;
    private volatile ScheduledExecutorService periodicPublisher;
    private volatile long startedAtMillis;
    private volatile String bindHost = "0.0.0.0";
    private volatile int bindPort = 8080;
    private volatile boolean running;

    public synchronized void start(MinecraftServer minecraftServer) {
        if (minecraftServer == null || running || !Config.WEB_ADMIN_ENABLED.get()) {
            return;
        }

        String configuredHost = sanitizeHost(Config.WEB_ADMIN_BIND_HOST.get());
        int configuredPort = Math.max(1, Math.min(65535, Config.WEB_ADMIN_PORT.get()));

        this.server = minecraftServer;
        this.bindHost = configuredHost;
        this.bindPort = configuredPort;

        try {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup(2);

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(HTTP_MAX_BODY_BYTES));
                            ch.pipeline().addLast(new ChunkedWriteHandler());
                            ch.pipeline().addLast(new WebSocketServerProtocolHandler("/ws/webadmin", null, true, HTTP_MAX_BODY_BYTES));
                            ch.pipeline().addLast(new WebSocketHandler(WebAdminService.this));
                            ch.pipeline().addLast(new HttpHandler(WebAdminService.this));
                        }
                    });

            serverChannel = bootstrap.bind(bindHost, bindPort).syncUninterruptibly().channel();
            running = true;
            startedAtMillis = System.currentTimeMillis();

            startPeriodicPublisher();

            if (Config.WEB_ADMIN_WARN_UNSECURED.get()) {
                UltimateBankingSystem.LOGGER.warn(
                        "[UBS WebAdmin] Running without authentication on {}:{} (explicitly intended for v1).",
                        bindHost,
                        bindPort
                );
            }
            UltimateBankingSystem.LOGGER.info("[UBS WebAdmin] Started on http://{}:{}/ubs-admin/", bindHost, bindPort);
        } catch (Exception ex) {
            UltimateBankingSystem.LOGGER.error("[UBS WebAdmin] Failed to start", ex);
            stop();
        }
    }

    public synchronized void stop() {
        running = false;

        if (periodicPublisher != null) {
            periodicPublisher.shutdownNow();
            periodicPublisher = null;
        }

        webSocketChannels.close().awaitUninterruptibly();
        impersonationBySession.clear();
        auditEntries.clear();
        pendingDeliveriesByPlayer.clear();
        vanillaAssetCache.clear();
        localMinecraftClientJar = null;
        localMinecraftClientJarResolved = false;

        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly();
            serverChannel = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().awaitUninterruptibly();
            workerGroup = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().awaitUninterruptibly();
            bossGroup = null;
        }

        server = null;
    }

    public boolean isRunning() {
        return running;
    }

    public String bindHost() {
        return bindHost;
    }

    public int bindPort() {
        return bindPort;
    }

    private void startPeriodicPublisher() {
        periodicPublisher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ubs-webadmin-publisher");
            t.setDaemon(true);
            return t;
        });
        periodicPublisher.scheduleAtFixedRate(this::publishPeriodicHealth, 2L, 2L, TimeUnit.SECONDS);
    }

    /**
     * Periodic event push keeps the dashboard live even when changes happen
     * from in-game actions outside the web panel.
     */
    private void publishPeriodicHealth() {
        if (!running) {
            return;
        }

        // Keep persistent economy chart history updated even when no dashboard client
        // is currently connected.
        try {
            callSafely(this::captureHourlyEconomySnapshotIfDue);
        } catch (Exception ignored) {
        }
        try {
            callSafely(() -> {
                flushPendingDeliveriesNow();
                return true;
            });
        } catch (Exception ignored) {
        }

        if (webSocketChannels.isEmpty()) {
            return;
        }

        for (Channel channel : webSocketChannels) {
            if (!channel.isActive()) {
                continue;
            }
            sendEvent(channel, "server_health", callSafely(this::buildHealthPayload));
        }
    }

    void registerWebSocket(Channel channel, String requestedSessionId) {
        String sessionId = sanitizeSessionId(requestedSessionId, channel);
        channel.attr(SESSION_ID_ATTR).set(sessionId);
        webSocketChannels.add(channel);
        sendEvent(channel, "connected", Map.of("sessionId", sessionId));
        sendSnapshot(channel, sessionId);
        sendEvent(channel, "server_health", callSafely(this::buildHealthPayload));
    }

    void unregisterWebSocket(Channel channel) {
        webSocketChannels.remove(channel);
    }

    void handleWebSocketText(Channel channel, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            JsonObject object = JsonParser.parseString(text).getAsJsonObject();
            String type = getString(object, "type");
            if ("refresh".equalsIgnoreCase(type)) {
                sendSnapshot(channel, sessionIdOf(channel));
            } else if ("ping".equalsIgnoreCase(type)) {
                sendEvent(channel, "pong", Map.of("ts", System.currentTimeMillis()));
            }
        } catch (Exception ignored) {
            // Ignore malformed client frames to keep dashboard resilient.
        }
    }

    ApiResponse handleApi(FullHttpRequest request, String path) {
        String sessionId = sanitizeSessionId(request.headers().get("X-Session-Id"), request);
        HttpMethod method = request.method();

        try {
            if (method.equals(HttpMethod.GET) && "/api/webadmin/dashboard".equals(path)) {
                return ApiResponse.ok(callSafely(this::buildDashboardPayload));
            }
            if (method.equals(HttpMethod.GET) && "/api/webadmin/history".equals(path)) {
                return ApiResponse.ok(callSafely(this::buildHistoryPayload));
            }
            if (method.equals(HttpMethod.GET) && "/api/webadmin/banks".equals(path)) {
                return ApiResponse.ok(callSafely(this::buildBanksPayload));
            }
            if (method.equals(HttpMethod.GET) && "/api/webadmin/shops".equals(path)) {
                return ApiResponse.ok(callSafely(this::buildShopsPayload));
            }
            if (method.equals(HttpMethod.GET) && "/api/webadmin/shop-items".equals(path)) {
                return ApiResponse.ok(callSafely(this::buildShopItemsPayload));
            }
            if (method.equals(HttpMethod.GET) && path.startsWith("/api/webadmin/shop-items/")) {
                String rawItemId = path.substring("/api/webadmin/shop-items/".length()).trim();
                return ApiResponse.ok(callSafely(() -> buildShopItemDetailPayload(rawItemId)));
            }
            if (method.equals(HttpMethod.GET) && "/api/webadmin/users".equals(path)) {
                return ApiResponse.ok(callSafely(this::buildUsersPayload));
            }
            if (method.equals(HttpMethod.GET) && path.startsWith("/api/webadmin/users/")) {
                String rawId = path.substring("/api/webadmin/users/".length()).trim();
                return handleUserDetail(rawId);
            }
            if (path.startsWith("/api/webadmin/shop/")) {
                String suffix = path.substring("/api/webadmin/shop/".length());
                if (suffix.endsWith("/action")) {
                    if (!method.equals(HttpMethod.POST)) {
                        return ApiResponse.methodNotAllowed("Use POST for shop actions.");
                    }
                    String shopIdRaw = suffix.substring(0, suffix.length() - "/action".length());
                    JsonObject body = parseBodyAsObject(request);
                    return handleShopAction(sessionId, shopIdRaw, body, request);
                }
                if (!method.equals(HttpMethod.GET)) {
                    return ApiResponse.methodNotAllowed("Use GET for shop details.");
                }
                return handleShopPayload(sessionId, suffix);
            }
            if (path.startsWith("/api/webadmin/account/")) {
                String suffix = path.substring("/api/webadmin/account/".length());
                if (suffix.endsWith("/action")) {
                    if (!method.equals(HttpMethod.POST)) {
                        return ApiResponse.methodNotAllowed("Use POST for account actions.");
                    }
                    String accountIdRaw = suffix.substring(0, suffix.length() - "/action".length());
                    JsonObject body = parseBodyAsObject(request);
                    return handleAccountAction(sessionId, accountIdRaw, body, request);
                }
                if (!method.equals(HttpMethod.GET)) {
                    return ApiResponse.methodNotAllowed("Use GET for account details.");
                }
                return handleAccountPayload(suffix);
            }

            if (method.equals(HttpMethod.GET) && "/api/webadmin/bootstrap".equals(path)) {
                return ApiResponse.ok(callSafely(() -> buildBootstrapPayload(sessionId)));
            }
            if (method.equals(HttpMethod.POST) && "/api/webadmin/impersonation/select".equals(path)) {
                return ApiResponse.methodNotAllowed("Impersonation is disabled in dashboard v2.");
            }
            if (method.equals(HttpMethod.GET) && "/api/webadmin/desktop".equals(path)) {
                return ApiResponse.ok(callSafely(() -> buildDesktopPayload(sessionId)));
            }
            if (method.equals(HttpMethod.POST) && "/api/webadmin/desktop/action".equals(path)) {
                JsonObject body = parseBodyAsObject(request);
                return handleDesktopAction(sessionId, body, request);
            }
            if (method.equals(HttpMethod.GET) && "/api/webadmin/audit".equals(path)) {
                return ApiResponse.ok(Map.of("entries", copyAuditEntries()));
            }
            if (method.equals(HttpMethod.POST) && "/api/webadmin/command".equals(path)) {
                JsonObject body = parseBodyAsObject(request);
                return handleServerCommand(sessionId, body, request);
            }
            if (method.equals(HttpMethod.GET) && "/api/webadmin/health".equals(path)) {
                return ApiResponse.ok(callSafely(this::buildHealthPayload));
            }
            if (method.equals(HttpMethod.POST) && "/api/webadmin/cards/action".equals(path)) {
                JsonObject body = parseBodyAsObject(request);
                return handleCardAction(sessionId, body, request);
            }

            // /api/webadmin/bank/{bankId}
            if (path.startsWith("/api/webadmin/bank/")) {
                String suffix = path.substring("/api/webadmin/bank/".length());
                if (suffix.endsWith("/action")) {
                    if (!method.equals(HttpMethod.POST)) {
                        return ApiResponse.methodNotAllowed("Use POST for bank actions.");
                    }
                    String bankIdRaw = suffix.substring(0, suffix.length() - "/action".length());
                    JsonObject body = parseBodyAsObject(request);
                    return handleBankAction(sessionId, bankIdRaw, body, request);
                }
                if (!method.equals(HttpMethod.GET)) {
                    return ApiResponse.methodNotAllowed("Use GET for bank details.");
                }
                return handleBankPayload(sessionId, suffix);
            }
        } catch (Exception ex) {
            UltimateBankingSystem.LOGGER.error("[UBS WebAdmin] API failure: {} {}", method, path, ex);
            return ApiResponse.serverError("Internal server error: " + ex.getMessage());
        }

        return ApiResponse.notFound("Unknown API route.");
    }

    private ApiResponse handleImpersonationSelect(String sessionId, JsonObject body) {
        String playerIdRaw = getString(body, "playerId");
        String playerNameRaw = getString(body, "playerName");
        if (playerIdRaw.isBlank() && playerNameRaw.isBlank()) {
            return ApiResponse.badRequest("playerId or playerName is required.");
        }

        Map<String, Object> response = callSafely(() -> {
            MinecraftServer current = requireServer();
            UUID resolvedId = null;
            String resolvedName = "";

            if (!playerIdRaw.isBlank()) {
                try {
                    UUID id = UUID.fromString(playerIdRaw.trim());
                    ServerPlayer player = current.getPlayerList().getPlayer(id);
                    if (player != null) {
                        resolvedId = id;
                        resolvedName = player.getGameProfile().getName();
                    }
                } catch (IllegalArgumentException ignored) {
                    return Map.of("ok", false, "message", "Invalid player UUID.");
                }
            }

            if (resolvedId == null && !playerNameRaw.isBlank()) {
                for (ServerPlayer player : current.getPlayerList().getPlayers()) {
                    if (player != null && player.getGameProfile().getName().equalsIgnoreCase(playerNameRaw.trim())) {
                        resolvedId = player.getUUID();
                        resolvedName = player.getGameProfile().getName();
                        break;
                    }
                }
            }

            if (resolvedId == null) {
                return Map.of("ok", false, "message", "Selected player must be online.");
            }

            impersonationBySession.put(sessionId, resolvedId);
            CentralBank centralBank = BankManager.getCentralBank(current);
            ensureVirtualDesktopContext(centralBank, resolvedId);

            appendAudit("IMPERSONATION_SELECT", sessionId, resolvedId, "Switched target player to " + resolvedName, true);
            sendSnapshotToSession(sessionId);
            return Map.of(
                    "ok", true,
                    "playerId", resolvedId.toString(),
                    "playerName", resolvedName
            );
        });
        return ApiResponse.ok(response);
    }

    private ApiResponse handleBankPayload(String sessionId, String bankIdRaw) {
        UUID bankId;
        try {
            bankId = UUID.fromString(bankIdRaw.trim());
        } catch (IllegalArgumentException ex) {
            return ApiResponse.badRequest("Invalid bank id.");
        }
        return ApiResponse.ok(callSafely(() -> buildBankDetailPayload(bankId)));
    }

    private ApiResponse handleBankAction(String sessionId, String bankIdRaw, JsonObject body, FullHttpRequest request) {
        UUID bankId;
        try {
            bankId = UUID.fromString(bankIdRaw.trim());
        } catch (IllegalArgumentException ex) {
            return ApiResponse.badRequest("Invalid bank id.");
        }

        String action = getString(body, "action");
        if (action.isBlank()) {
            return ApiResponse.badRequest("action is required.");
        }

        String arg1 = getString(body, "arg1");
        String arg2 = getString(body, "arg2");
        String arg3 = getString(body, "arg3");
        String arg4 = getString(body, "arg4");

        Map<String, Object> payload = callSafely(() -> {
            MinecraftServer current = requireServer();
            CentralBank centralBank = BankManager.getCentralBank(current);
            if (centralBank == null) {
                return Map.of("ok", false, "message", "Bank data unavailable.");
            }
            Bank bank = centralBank.getBank(bankId);
            if (bank == null) {
                return Map.of("ok", false, "message", "Bank not found.");
            }

            String normalizedAction = safeString(action).toUpperCase(Locale.ROOT);
            String source = remoteAddress(request);
            boolean success = false;
            String message;
            UUID actorId = null;

            try {
                switch (normalizedAction) {
                    case "SHARES_REMOVE" -> {
                        String targetRaw = safeString(getString(body, "playerId"));
                        if (targetRaw.isBlank()) {
                            targetRaw = safeString(arg1);
                        }
                        UUID targetId = resolvePlayerId(current, targetRaw);
                        if (targetId == null) {
                            message = "playerId must be a valid UUID or online player name.";
                            break;
                        }
                        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
                        Map<UUID, BigDecimal> shares = decodeShareMap(metadata.getString("shares"));
                        if (shares.remove(targetId) == null) {
                            message = "Shareholder not found.";
                            break;
                        }
                        metadata.putString("shares", encodeShareMap(shares));
                        centralBank.putBankMetadata(bankId, metadata);
                        BankManager.markDirty();
                        success = true;
                        message = "Share removed for " + resolvePlayerName(current, targetId) + ".";
                        actorId = targetId;
                    }
                    case "COFOUNDER_REMOVE" -> {
                        String targetRaw = safeString(getString(body, "playerId"));
                        if (targetRaw.isBlank()) {
                            targetRaw = safeString(arg1);
                        }
                        UUID targetId = resolvePlayerId(current, targetRaw);
                        if (targetId == null) {
                            message = "playerId must be a valid UUID or online player name.";
                            break;
                        }
                        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
                        List<UUID> cofounders = decodeUuidList(metadata.getString("cofounders"));
                        if (!cofounders.remove(targetId)) {
                            message = "Cofounder not found.";
                            break;
                        }
                        metadata.putString("cofounders", encodeUuidList(cofounders));
                        centralBank.putBankMetadata(bankId, metadata);
                        BankManager.markDirty();
                        success = true;
                        message = "Cofounder removed: " + resolvePlayerName(current, targetId) + ".";
                        actorId = targetId;
                    }
                    case "LOAN_PRODUCT_DELETE" -> {
                        String productName = safeString(getString(body, "name"));
                        if (productName.isBlank()) {
                            productName = safeString(arg1);
                        }
                        if (productName.isBlank()) {
                            message = "name is required.";
                            break;
                        }
                        final String productNameFinal = productName;
                        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
                        List<LoanProductSpec> products = decodeLoanProducts(metadata.getString("loanProducts"));
                        boolean removed = products.removeIf(product -> product != null
                                && product.name() != null
                                && product.name().equalsIgnoreCase(productNameFinal));
                        if (!removed) {
                            message = "Loan product not found.";
                            break;
                        }
                        metadata.putString("loanProducts", encodeLoanProducts(products));
                        centralBank.putBankMetadata(bankId, metadata);
                        BankManager.markDirty();
                        success = true;
                        message = "Loan product deleted: " + productNameFinal + ".";
                    }
                    case "UPDATE_OVERVIEW" -> {
                        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
                        List<String> updatedFields = new ArrayList<>();

                        String ownerLookupRaw = safeString(getString(body, "ownerLookup"));
                        String ownerIdRaw = safeString(getString(body, "ownerId"));
                        String nextStatusRaw = safeString(getString(body, "status"));
                        String nextOwnershipRaw = safeString(getString(body, "ownershipModel"));
                        String nextColorRaw = safeString(getString(body, "color"));
                        String nextMotto = getString(body, "motto");
                        String federalFundsRateRaw = safeString(getString(body, "federalFundsRate"));
                        String issueFeeRaw = safeString(getString(body, "issueFee"));
                        String replacementFeeRaw = safeString(getString(body, "replacementFee"));
                        String singleLimitRaw = safeString(getString(body, "singleLimit"));
                        String dailyPlayerLimitRaw = safeString(getString(body, "dailyPlayerLimit"));
                        String dailyBankLimitRaw = safeString(getString(body, "dailyBankLimit"));
                        String tellerLimitRaw = safeString(getString(body, "tellerLimit"));

                        String ownerCandidateRaw = ownerIdRaw.isBlank() ? ownerLookupRaw : ownerIdRaw;
                        UUID nextOwnerId = null;
                        if (!ownerCandidateRaw.isBlank()) {
                            nextOwnerId = parseUuidOrNull(ownerCandidateRaw);
                            if (nextOwnerId == null) {
                                nextOwnerId = resolvePlayerId(current, ownerCandidateRaw);
                            }
                            if (nextOwnerId == null) {
                                message = "Owner must be a valid UUID or an online player name.";
                                break;
                            }
                        }

                        String currentStatus = normalizeBankStatus(metadata.getString("status"));
                        String nextStatus = nextStatusRaw.isBlank()
                                ? currentStatus
                                : normalizeEditableBankStatus(nextStatusRaw);
                        if (nextStatus == null) {
                            message = "Invalid status. Use ACTIVE, WARNING, RESTRICTED, SUSPENDED, REVOKED, or LOCKDOWN.";
                            break;
                        }

                        String currentOwnership = normalizeEditableOwnershipModel(metadata.getString("ownershipModel"));
                        if (currentOwnership == null) {
                            currentOwnership = "SOLE";
                        }
                        String nextOwnership = nextOwnershipRaw.isBlank()
                                ? currentOwnership
                                : normalizeEditableOwnershipModel(nextOwnershipRaw);
                        if (nextOwnership == null) {
                            message = "Invalid ownership model. Use SOLE, ROLE_BASED, PERCENTAGE_SHARES, or FIXED_COFOUNDERS.";
                            break;
                        }

                        String currentColor = metadata.getString("color");
                        if (currentColor == null || currentColor.isBlank()) {
                            currentColor = "#55AAFF";
                        }
                        String nextColor = nextColorRaw.isBlank()
                                ? normalizeEditableBankColor(currentColor)
                                : normalizeEditableBankColor(nextColorRaw);
                        if (nextColor == null) {
                            message = "Invalid brand color. Use #RRGGBB or known color names.";
                            break;
                        }

                        if (nextMotto == null) {
                            nextMotto = "";
                        }
                        if (nextMotto.length() > 80) {
                            message = "Motto is too long (max 80 characters).";
                            break;
                        }

                        Double nextFederalFundsRate = federalFundsRateRaw.isBlank()
                                ? centralBank.getFederalFundsRate()
                                : parseDoubleOrNull(federalFundsRateRaw);
                        if (nextFederalFundsRate == null) {
                            message = "Federal funds rate must be a valid number.";
                            break;
                        }

                        BigDecimal existingIssueFee = CreditCardService.getIssueFee(centralBank, bankId).setScale(2, RoundingMode.HALF_EVEN);
                        BigDecimal existingReplacementFee = CreditCardService.getReplacementFee(centralBank, bankId).setScale(2, RoundingMode.HALF_EVEN);
                        BigDecimal nextIssueFee = issueFeeRaw.isBlank()
                                ? existingIssueFee
                                : parseNonNegativeAmount(issueFeeRaw);
                        if (nextIssueFee == null) {
                            message = "Issue fee must be a valid non-negative number.";
                            break;
                        }
                        BigDecimal nextReplacementFee = replacementFeeRaw.isBlank()
                                ? existingReplacementFee
                                : parseNonNegativeAmount(replacementFeeRaw);
                        if (nextReplacementFee == null) {
                            message = "Replacement fee must be a valid non-negative number.";
                            break;
                        }

                        BigDecimal currentSingleLimit = metadata.contains("limitSingle")
                                ? decimalOrZero(metadata, "limitSingle").setScale(2, RoundingMode.HALF_EVEN)
                                : BigDecimal.valueOf(Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()).setScale(2, RoundingMode.HALF_EVEN);
                        BigDecimal nextSingleLimit = singleLimitRaw.isBlank()
                                ? currentSingleLimit
                                : parsePositiveAmount(singleLimitRaw);
                        if (nextSingleLimit == null) {
                            message = "Single transaction limit must be a valid positive number.";
                            break;
                        }
                        BigDecimal singleLimitMax = BigDecimal.valueOf(Config.GLOBAL_MAX_SINGLE_TRANSACTION.get()).setScale(2, RoundingMode.HALF_EVEN);
                        if (nextSingleLimit.compareTo(singleLimitMax) > 0) {
                            message = "Single transaction limit cannot exceed global max " + singleLimitMax.toPlainString() + ".";
                            break;
                        }

                        BigDecimal currentDailyPlayerLimit = metadata.contains("limitDailyPlayer")
                                ? decimalOrZero(metadata, "limitDailyPlayer").setScale(2, RoundingMode.HALF_EVEN)
                                : BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_PLAYER_VOLUME.get()).setScale(2, RoundingMode.HALF_EVEN);
                        BigDecimal nextDailyPlayerLimit = dailyPlayerLimitRaw.isBlank()
                                ? currentDailyPlayerLimit
                                : parsePositiveAmount(dailyPlayerLimitRaw);
                        if (nextDailyPlayerLimit == null) {
                            message = "Daily player limit must be a valid positive number.";
                            break;
                        }
                        BigDecimal dailyPlayerLimitMax = BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_PLAYER_VOLUME.get()).setScale(2, RoundingMode.HALF_EVEN);
                        if (nextDailyPlayerLimit.compareTo(dailyPlayerLimitMax) > 0) {
                            message = "Daily player limit cannot exceed global max " + dailyPlayerLimitMax.toPlainString() + ".";
                            break;
                        }

                        BigDecimal currentDailyBankLimit = metadata.contains("limitDailyBank")
                                ? decimalOrZero(metadata, "limitDailyBank").setScale(2, RoundingMode.HALF_EVEN)
                                : BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_BANK_VOLUME.get()).setScale(2, RoundingMode.HALF_EVEN);
                        BigDecimal nextDailyBankLimit = dailyBankLimitRaw.isBlank()
                                ? currentDailyBankLimit
                                : parsePositiveAmount(dailyBankLimitRaw);
                        if (nextDailyBankLimit == null) {
                            message = "Daily bank limit must be a valid positive number.";
                            break;
                        }
                        BigDecimal dailyBankLimitMax = BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_BANK_VOLUME.get()).setScale(2, RoundingMode.HALF_EVEN);
                        if (nextDailyBankLimit.compareTo(dailyBankLimitMax) > 0) {
                            message = "Daily bank limit cannot exceed global max " + dailyBankLimitMax.toPlainString() + ".";
                            break;
                        }

                        BigDecimal currentTellerLimit = metadata.contains("limitTeller")
                                ? decimalOrZero(metadata, "limitTeller").setScale(2, RoundingMode.HALF_EVEN)
                                : BigDecimal.valueOf(250000L).setScale(2, RoundingMode.HALF_EVEN);
                        BigDecimal nextTellerLimit = tellerLimitRaw.isBlank()
                                ? currentTellerLimit
                                : parsePositiveAmount(tellerLimitRaw);
                        if (nextTellerLimit == null) {
                            message = "Teller cash limit must be a valid positive number.";
                            break;
                        }
                        BigDecimal tellerLimitMax = BigDecimal.valueOf(Integer.MAX_VALUE / 100L).setScale(2, RoundingMode.HALF_EVEN);
                        if (nextTellerLimit.compareTo(tellerLimitMax) > 0) {
                            message = "Teller cash limit cannot exceed " + tellerLimitMax.toPlainString() + ".";
                            break;
                        }

                        // Persist owner/status/policy changes in one transaction-like operation.
                        if (nextOwnerId != null && !nextOwnerId.equals(bank.getBankOwnerId())) {
                            bank.setBankOwnerId(nextOwnerId);
                            actorId = nextOwnerId;
                            updatedFields.add("owner");
                        }
                        if (!nextStatus.equalsIgnoreCase(currentStatus)) {
                            metadata.putString("status", nextStatus);
                            updatedFields.add("status");
                        }
                        if (!nextOwnership.equalsIgnoreCase(currentOwnership)) {
                            metadata.putString("ownershipModel", nextOwnership);
                            updatedFields.add("ownership model");
                        }
                        if (!nextColor.equalsIgnoreCase(currentColor)) {
                            metadata.putString("color", nextColor);
                            updatedFields.add("brand color");
                        }
                        if (!nextMotto.equals(metadata.getString("motto"))) {
                            metadata.putString("motto", nextMotto);
                            updatedFields.add("motto");
                        }
                        if (Math.abs(centralBank.getFederalFundsRate() - nextFederalFundsRate) > 0.0001D) {
                            if (!centralBank.setFederalFundsRate(nextFederalFundsRate)) {
                                message = "Federal funds rate is outside the configured allowed range.";
                                break;
                            }
                            updatedFields.add("federal funds rate");
                        }
                        if (nextIssueFee.compareTo(existingIssueFee) != 0 || nextReplacementFee.compareTo(existingReplacementFee) != 0) {
                            if (!CreditCardService.setFees(centralBank, bankId, nextIssueFee, nextReplacementFee)) {
                                message = "Could not update card fees.";
                                break;
                            }
                            updatedFields.add("card fees");
                        }
                        if (nextSingleLimit.compareTo(currentSingleLimit) != 0) {
                            metadata.putString("limitSingle", nextSingleLimit.toPlainString());
                            updatedFields.add("single tx limit");
                        }
                        if (nextDailyPlayerLimit.compareTo(currentDailyPlayerLimit) != 0) {
                            metadata.putString("limitDailyPlayer", nextDailyPlayerLimit.toPlainString());
                            updatedFields.add("daily player limit");
                        }
                        if (nextDailyBankLimit.compareTo(currentDailyBankLimit) != 0) {
                            metadata.putString("limitDailyBank", nextDailyBankLimit.toPlainString());
                            updatedFields.add("daily bank limit");
                        }
                        if (nextTellerLimit.compareTo(currentTellerLimit) != 0) {
                            metadata.putString("limitTeller", nextTellerLimit.toPlainString());
                            updatedFields.add("teller cash limit");
                        }

                        centralBank.putBankMetadata(bankId, metadata);
                        BankManager.markDirty();
                        success = true;
                        message = updatedFields.isEmpty()
                                ? "Bank overview saved (no changes detected)."
                                : "Updated " + String.join(", ", updatedFields) + ".";
                    }
                    default -> {
                        UUID selectedPlayer = resolveSelectedPlayer(sessionId, current, false);
                        ServerPlayer target = selectedPlayer == null ? null : current.getPlayerList().getPlayer(selectedPlayer);
                        if (target == null && bank.getBankOwnerId() != null) {
                            target = current.getPlayerList().getPlayer(bank.getBankOwnerId());
                        }
                        if (target == null) {
                            for (ServerPlayer online : current.getPlayerList().getPlayers()) {
                                if (online == null) {
                                    continue;
                                }
                                boolean allowCentral = bankId.equals(centralBank.getBankId()) && online.hasPermissions(3);
                                if (BankOwnerPcService.canAccessBank(centralBank, online.getUUID(), bankId, allowCentral)) {
                                    target = online;
                                    break;
                                }
                            }
                        }
                        if (target == null) {
                            message = "No eligible online player available to execute this action.";
                            break;
                        }

                        actorId = target.getUUID();
                        BankOwnerPcService.ActionResult result = BankOwnerPcService.executeAction(
                                current,
                                centralBank,
                                target,
                                bankId,
                                normalizedAction,
                                arg1,
                                arg2,
                                arg3,
                                arg4
                        );
                        success = result.success();
                        message = result.message();
                    }
                }
            } catch (Exception ex) {
                success = false;
                message = "Bank action failed: " + ex.getMessage();
            }

            if (success) {
                BankManager.markDirty();
            }

            appendAudit(
                    "BANK_ACTION",
                    sessionId,
                    actorId,
                    "action=" + normalizedAction + ", bankId=" + bankId + ", source=" + source,
                    success
            );

            sendSnapshotToSession(sessionId);
            broadcastEvent("bank_changed", Map.of("bankId", bankId.toString(), "action", normalizedAction, "success", success));

            Map<String, Object> refreshed = new LinkedHashMap<>(buildBankDetailPayload(bankId));
            refreshed.put("ok", success);
            refreshed.put("message", message);
            refreshed.put("action", normalizedAction);
            return refreshed;
        });

        return ApiResponse.ok(payload);
    }

    private ApiResponse handleShopPayload(String sessionId, String shopIdRaw) {
        UUID shopId;
        try {
            shopId = UUID.fromString(shopIdRaw.trim());
        } catch (IllegalArgumentException ex) {
            return ApiResponse.badRequest("Invalid shop id.");
        }
        return ApiResponse.ok(callSafely(() -> buildShopDetailPayload(shopId)));
    }

    private ApiResponse handleShopAction(String sessionId, String shopIdRaw, JsonObject body, FullHttpRequest request) {
        UUID shopId;
        try {
            shopId = UUID.fromString(shopIdRaw.trim());
        } catch (IllegalArgumentException ex) {
            return ApiResponse.badRequest("Invalid shop id.");
        }

        String action = safeString(getString(body, "action")).toUpperCase(Locale.ROOT);
        if (action.isBlank()) {
            return ApiResponse.badRequest("action is required.");
        }

        Map<String, Object> payload = callSafely(() -> {
            MinecraftServer current = requireServer();
            CentralBank centralBank = BankManager.getCentralBank(current);
            if (centralBank == null) {
                return Map.of("ok", false, "message", "Shop data unavailable.");
            }

            UUID ownerId = ShopService.resolveShopOwnerId(centralBank, shopId);
            if (ownerId == null) {
                return Map.of("ok", false, "message", "Shop owner not found for selected shop.");
            }

            String source = remoteAddress(request);
            boolean success = false;
            String message;

            try {
                switch (action) {
                    case "UPDATE_OVERVIEW" -> {
                        List<String> messages = new ArrayList<>();

                        String name = safeString(getString(body, "name"));
                        if (!name.isBlank()) {
                            ShopService.ShopActionResult result = ShopService.renameShop(centralBank, ownerId, shopId, name);
                            messages.add(result.message());
                            if (!result.success()) {
                                message = result.message();
                                break;
                            }
                        }

                        String type = safeString(getString(body, "type"));
                        if (!type.isBlank()) {
                            ShopService.ShopActionResult result = ShopService.setShopType(centralBank, ownerId, shopId, type);
                            messages.add(result.message());
                            if (!result.success()) {
                                message = result.message();
                                break;
                            }
                        }

                        String levelRaw = safeString(getString(body, "level"));
                        if (!levelRaw.isBlank()) {
                            Integer level = parseIntegerOrNull(levelRaw);
                            if (level == null) {
                                message = "level must be a valid integer.";
                                break;
                            }
                            // Route level updates through ShopService admin leveling so dependent capacities refresh from level rules.
                            ShopService.ShopActionResult result = ShopService.adminSetShopLevel(centralBank, shopId, level);
                            messages.add(result.message());
                            if (!result.success()) {
                                message = result.message();
                                break;
                            }
                        }

                        success = true;
                        message = messages.isEmpty() ? "No overview changes provided." : String.join(" ", messages);
                    }
                    case "SET_LEVEL" -> {
                        Integer level = parseIntegerOrNull(safeString(getString(body, "level")));
                        if (level == null) {
                            message = "level must be a valid integer.";
                            break;
                        }
                        ShopService.ShopActionResult result = ShopService.adminSetShopLevel(centralBank, shopId, level);
                        success = result.success();
                        message = result.message();
                    }
                    case "ADJUST_LEVEL" -> {
                        Integer delta = parseIntegerOrNull(safeString(getString(body, "delta")));
                        if (delta == null) {
                            message = "delta must be a valid integer.";
                            break;
                        }
                        ShopService.ShopActionResult result = ShopService.adminAdjustShopLevel(centralBank, shopId, delta);
                        success = result.success();
                        message = result.message();
                    }
                    case "PERMISSION_SET" -> {
                        String player = firstNonBlank(
                                safeString(getString(body, "playerId")),
                                safeString(getString(body, "player")),
                                safeString(getString(body, "arg1"))
                        );
                        String role = firstNonBlank(
                                safeString(getString(body, "role")),
                                safeString(getString(body, "arg2"))
                        );
                        if (player.isBlank() || role.isBlank()) {
                            message = "playerId/player and role are required.";
                            break;
                        }
                        ShopService.ShopActionResult result = ShopService.setPermissionRole(
                                current,
                                centralBank,
                                ownerId,
                                shopId,
                                player + "|" + role
                        );
                        success = result.success();
                        message = result.message();
                    }
                    case "PERMISSION_REMOVE" -> {
                        String player = firstNonBlank(
                                safeString(getString(body, "playerId")),
                                safeString(getString(body, "player")),
                                safeString(getString(body, "arg1"))
                        );
                        if (player.isBlank()) {
                            message = "playerId/player is required.";
                            break;
                        }
                        ShopService.ShopActionResult result = ShopService.removePermissionRole(
                                current,
                                centralBank,
                                ownerId,
                                shopId,
                                player
                        );
                        success = result.success();
                        message = result.message();
                    }
                    case "CLAIM_ADD", "CLAIM_REMOVE", "STOCKROOM_ADD", "STOCKROOM_REMOVE" -> {
                        ShopRegionBounds bounds = parseShopRegionBounds(body);
                        if (bounds == null) {
                            message = "dimension + min/max coordinates are required.";
                            break;
                        }

                        ShopService.ShopActionResult result;
                        if ("CLAIM_ADD".equals(action)) {
                            result = ShopService.addClaimRegion(centralBank, ownerId, shopId, bounds.dimensionId(), bounds.first(), bounds.second());
                        } else if ("CLAIM_REMOVE".equals(action)) {
                            result = ShopService.removeClaimRegion(centralBank, ownerId, shopId, bounds.dimensionId(), bounds.first(), bounds.second());
                        } else if ("STOCKROOM_ADD".equals(action)) {
                            result = ShopService.addStockroomRegion(centralBank, ownerId, shopId, bounds.dimensionId(), bounds.first(), bounds.second());
                        } else {
                            result = ShopService.removeStockroomRegion(centralBank, ownerId, shopId, bounds.dimensionId(), bounds.first(), bounds.second());
                        }
                        success = result.success();
                        message = result.message();
                    }
                    case "SET_HOURS" -> {
                        String open = firstNonBlank(safeString(getString(body, "open")), safeString(getString(body, "arg1")));
                        String close = firstNonBlank(safeString(getString(body, "close")), safeString(getString(body, "arg2")));
                        if (open.isBlank() || close.isBlank()) {
                            message = "open and close values are required (e.g. 09:00 | 18:00).";
                            break;
                        }
                        ShopService.ShopActionResult result = ShopService.setShopHours(current, centralBank, ownerId, shopId, open + "|" + close);
                        success = result.success();
                        message = result.message();
                    }
                    case "SET_CLOSED_DELIVERER_ACCESS" -> {
                        String enabled = firstNonBlank(safeString(getString(body, "enabled")), safeString(getString(body, "arg1")));
                        if (enabled.isBlank()) {
                            message = "enabled is required.";
                            break;
                        }
                        ShopService.ShopActionResult result = ShopService.setShopClosedDelivererStockroomAccess(
                                current,
                                centralBank,
                                ownerId,
                                shopId,
                                enabled
                        );
                        success = result.success();
                        message = result.message();
                    }
                    case "SET_LIGHTING" -> {
                        List<String> messages = new ArrayList<>();
                        boolean anyField = false;

                        String enabled = safeString(getString(body, "enabled"));
                        if (!enabled.isBlank()) {
                            anyField = true;
                            ShopService.ShopActionResult result = ShopService.setShopLightingEnabled(current, centralBank, ownerId, shopId, enabled);
                            messages.add(result.message());
                            if (!result.success()) {
                                message = result.message();
                                break;
                            }
                        }

                        String mainMode = safeString(getString(body, "mainMode"));
                        if (!mainMode.isBlank()) {
                            anyField = true;
                            ShopService.ShopActionResult result = ShopService.setShopMainLightingMode(current, centralBank, ownerId, shopId, mainMode);
                            messages.add(result.message());
                            if (!result.success()) {
                                message = result.message();
                                break;
                            }
                        }

                        String stockroomMode = safeString(getString(body, "stockroomMode"));
                        if (!stockroomMode.isBlank()) {
                            anyField = true;
                            ShopService.ShopActionResult result = ShopService.setShopStockroomLightingMode(current, centralBank, ownerId, shopId, stockroomMode);
                            messages.add(result.message());
                            if (!result.success()) {
                                message = result.message();
                                break;
                            }
                        }

                        String excludeStockroom = safeString(getString(body, "excludeStockroom"));
                        if (!excludeStockroom.isBlank()) {
                            anyField = true;
                            ShopService.ShopActionResult result = ShopService.setShopExcludeStockroomLighting(current, centralBank, ownerId, shopId, excludeStockroom);
                            messages.add(result.message());
                            if (!result.success()) {
                                message = result.message();
                                break;
                            }
                        }

                        String lightLevel = safeString(getString(body, "lightLevel"));
                        if (!lightLevel.isBlank()) {
                            anyField = true;
                            ShopService.ShopActionResult result = ShopService.setShopLightingLevel(current, centralBank, ownerId, shopId, lightLevel);
                            messages.add(result.message());
                            if (!result.success()) {
                                message = result.message();
                                break;
                            }
                        }

                        success = true;
                        message = !anyField
                                ? "No lighting changes provided."
                                : String.join(" ", messages);
                    }
                    case "SET_SETTLEMENT_ACCOUNT" -> {
                        String accountSelection = firstNonBlank(
                                safeString(getString(body, "accountId")),
                                safeString(getString(body, "selection")),
                                safeString(getString(body, "arg1"))
                        );
                        if (accountSelection.isBlank()) {
                            message = "accountId/selection is required.";
                            break;
                        }
                        ShopService.ShopActionResult result = ShopService.setSettlementAccount(centralBank, ownerId, shopId, accountSelection);
                        success = result.success();
                        message = result.message();
                    }
                    case "FIRE_EMPLOYEE" -> {
                        String employeeSelection = firstNonBlank(
                                safeString(getString(body, "employeeId")),
                                safeString(getString(body, "selection")),
                                safeString(getString(body, "arg1"))
                        );
                        if (employeeSelection.isBlank()) {
                            message = "employeeId/selection is required.";
                            break;
                        }
                        ShopService.ShopActionResult result = ShopService.fireEmployee(current, centralBank, ownerId, shopId, employeeSelection);
                        success = result.success();
                        message = result.message();
                    }
                    case "CLEAR_CHECKOUT_TERMINAL" -> {
                        ShopService.ShopActionResult result = ShopService.clearCheckoutTerminal(centralBank, ownerId, shopId);
                        success = result.success();
                        message = result.message();
                    }
                    case "CLEAR_CASHIER_TERMINALS" -> {
                        ShopService.ShopActionResult result = ShopService.clearCashierTerminalLinks(centralBank, ownerId, shopId);
                        success = result.success();
                        message = result.message();
                    }
                    case "DELETE_SHOP" -> {
                        String confirmName = firstNonBlank(
                                safeString(getString(body, "confirmName")),
                                safeString(getString(body, "name")),
                                safeString(getString(body, "arg1"))
                        );
                        if (confirmName.isBlank()) {
                            message = "confirmName is required and must match shop name.";
                            break;
                        }
                        ShopService.ShopActionResult result = ShopService.deleteShop(current, centralBank, ownerId, shopId, confirmName);
                        success = result.success();
                        message = result.message();
                    }
                    default -> message = "Unsupported shop action: " + action;
                }
            } catch (Exception ex) {
                success = false;
                message = "Shop action failed: " + ex.getMessage();
            }

            if (success) {
                BankManager.markDirty();
            }

            appendAudit(
                    "SHOP_ACTION",
                    sessionId,
                    ownerId,
                    "action=" + action + ", shopId=" + shopId + ", source=" + source,
                    success
            );

            sendSnapshotToSession(sessionId);
            broadcastEvent("shop_changed", Map.of("shopId", shopId.toString(), "action", action, "success", success));

            if (success && "DELETE_SHOP".equals(action)) {
                return Map.of(
                        "ok", true,
                        "deleted", true,
                        "shopId", shopId.toString(),
                        "message", message,
                        "action", action
                );
            }

            Map<String, Object> refreshed = new LinkedHashMap<>(buildShopDetailPayload(shopId));
            refreshed.put("ok", success);
            refreshed.put("message", message);
            refreshed.put("action", action);
            return refreshed;
        });

        return ApiResponse.ok(payload);
    }

    private ApiResponse handleDesktopAction(String sessionId, JsonObject body, FullHttpRequest request) {
        String action = getString(body, "action");
        String arg1 = getString(body, "arg1");
        String arg2 = getString(body, "arg2");
        if (action.isBlank()) {
            return ApiResponse.badRequest("action is required.");
        }

        Map<String, Object> payload = callSafely(() -> {
            MinecraftServer current = requireServer();
            CentralBank centralBank = BankManager.getCentralBank(current);
            if (centralBank == null) {
                return Map.of("ok", false, "message", "Bank data unavailable.");
            }

            UUID selectedPlayer = resolveSelectedPlayer(sessionId, current, true);
            if (selectedPlayer == null) {
                return Map.of("ok", false, "message", "No online players available for impersonation.");
            }

            ensureVirtualDesktopContext(centralBank, selectedPlayer);
            ServerPlayer target = current.getPlayerList().getPlayer(selectedPlayer);
            if (target == null) {
                return Map.of("ok", false, "message", "Impersonated player must be online.");
            }

            BankOwnerPcService.ActionResult result = BankOwnerPcService.executeDesktopAction(
                    current,
                    centralBank,
                    target,
                    action,
                    arg1,
                    arg2
            );

            OwnerPcDesktopDataPayload desktopData = BankOwnerPcService.buildDesktopData(centralBank, selectedPlayer);
            List<OwnerPcBankAppSummary> apps = BankOwnerPcService.listAccessibleApps(current, centralBank, selectedPlayer, true);

            appendAudit(
                    "DESKTOP_ACTION",
                    sessionId,
                    selectedPlayer,
                    "action=" + action + ", source=" + remoteAddress(request),
                    result.success()
            );

            sendSnapshotToSession(sessionId);
            broadcastEvent("desktop_changed", Map.of("action", action, "success", result.success()));

            return Map.of(
                    "ok", true,
                    "result", result,
                    "desktop", desktopData,
                    "apps", apps
            );
        });
        return ApiResponse.ok(payload);
    }

    private ApiResponse handleServerCommand(String sessionId, JsonObject body, FullHttpRequest request) {
        String command = getString(body, "command");
        if (command.isBlank()) {
            return ApiResponse.badRequest("command is required.");
        }
        String normalizedCommand = command.startsWith("/") ? command.substring(1) : command;

        Map<String, Object> payload = callSafely(() -> {
            MinecraftServer current = requireServer();
            CommandSourceStack source = current.createCommandSourceStack().withPermission(4);
            current.getCommands().performPrefixedCommand(source, normalizedCommand);
            int result = 1;

            UUID selectedPlayer = resolveSelectedPlayer(sessionId, current, false);
            appendAudit(
                    "SERVER_COMMAND",
                    sessionId,
                    selectedPlayer,
                    "command=/" + normalizedCommand + ", source=" + remoteAddress(request),
                    result >= 0
            );

            sendSnapshotToSession(sessionId);
            broadcastEvent("command_executed", Map.of("command", normalizedCommand, "result", result));

            return Map.of("ok", true, "result", result);
        });
        return ApiResponse.ok(payload);
    }

    private ApiResponse handleUserDetail(String rawId) {
        UUID playerId;
        try {
            playerId = UUID.fromString(rawId);
        } catch (Exception ex) {
            return ApiResponse.badRequest("Invalid player id.");
        }
        return ApiResponse.ok(callSafely(() -> buildUserDetailPayload(playerId)));
    }

    private ApiResponse handleAccountPayload(String accountIdRaw) {
        UUID accountId;
        try {
            accountId = UUID.fromString(accountIdRaw.trim());
        } catch (Exception ex) {
            return ApiResponse.badRequest("Invalid account id.");
        }
        return ApiResponse.ok(callSafely(() -> buildAccountDetailPayload(accountId)));
    }

    /**
     * Account admin actions are intentionally explicit and auditable so server
     * owners can run operational account management from the dashboard.
     */
    private ApiResponse handleAccountAction(String sessionId, String accountIdRaw, JsonObject body, FullHttpRequest request) {
        UUID accountId;
        try {
            accountId = UUID.fromString(accountIdRaw.trim());
        } catch (Exception ex) {
            return ApiResponse.badRequest("Invalid account id.");
        }

        String action = safeString(getString(body, "action")).toUpperCase(Locale.ROOT);
        if (action.isBlank()) {
            return ApiResponse.badRequest("action is required.");
        }

        Map<String, Object> payload = callSafely(() -> {
            MinecraftServer current = requireServer();
            CentralBank centralBank = BankManager.getCentralBank(current);
            if (centralBank == null) {
                return Map.of("ok", false, "message", "Bank data unavailable.");
            }

            AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
            Bank bank = findBankForAccount(centralBank, accountId);
            if (account == null || bank == null) {
                return Map.of("ok", false, "message", "Account not found.");
            }

            UUID accountOwner = account.getPlayerUUID();
            String source = remoteAddress(request);
            String detail = "action=" + action + ", accountId=" + accountId + ", source=" + source;
            Map<String, Object> actionExtras = new LinkedHashMap<>();
            String actionMessage = "Account action completed.";

            try {
                switch (action) {
                    case "FREEZE" -> {
                        String reason = safeString(getString(body, "reason"));
                        account.freeze(reason.isBlank() ? "Frozen by web admin." : reason);
                        actionMessage = "Account frozen.";
                    }
                    case "UNFREEZE" -> {
                        account.unfreeze();
                        actionMessage = "Account unfrozen.";
                    }
                    case "SET_PRIMARY" -> {
                        setPrimaryAccountForOwner(centralBank, account);
                        actionMessage = "Primary account updated.";
                    }
                    case "SET_CREDIT_SCORE" -> {
                        Integer score = parseIntegerOrNull(getString(body, "creditScore"));
                        if (score == null) {
                            return Map.of("ok", false, "message", "creditScore must be a valid integer.");
                        }
                        account.setCreditScore(score);
                        actionMessage = "Credit score set to " + score + ".";
                    }
                    case "ADJUST_CREDIT_SCORE" -> {
                        Integer delta = parseIntegerOrNull(getString(body, "delta"));
                        if (delta == null) {
                            return Map.of("ok", false, "message", "delta must be a valid integer.");
                        }
                        account.adjustCreditScore(delta);
                        actionMessage = "Credit score adjusted by " + delta + ".";
                    }
                    case "SET_DEFAULTED" -> {
                        boolean next = parseBooleanFlag(getString(body, "defaulted"));
                        account.setDefaulted(next);
                        actionMessage = next ? "Account marked defaulted." : "Account marked active.";
                    }
                    case "SET_ACCESS_TYPE" -> {
                        account.setAccountAccessType(getString(body, "accessType"));
                        actionMessage = "Access type updated.";
                    }
                    case "SET_BUSINESS_LABEL" -> {
                        account.setBusinessLabel(getString(body, "businessLabel"));
                        actionMessage = "Business label updated.";
                    }
                    case "SET_PIN" -> {
                        String pin = safeString(getString(body, "pin"));
                        if (!account.setPin(pin)) {
                            return Map.of("ok", false, "message", "PIN must be exactly 4 digits.");
                        }
                        actionMessage = "PIN updated.";
                    }
                    case "FORCE_DEPOSIT" -> {
                        BigDecimal amount = parsePositiveAmount(getString(body, "amount"));
                        if (amount == null) {
                            return Map.of("ok", false, "message", "amount must be a positive number.");
                        }
                        if (!account.forceAddBalance(amount)) {
                            return Map.of("ok", false, "message", "Could not deposit to account.");
                        }
                        account.addTransaction(new UserTransaction(
                                bank.getBankId(),
                                account.getAccountUUID(),
                                amount,
                                LocalDateTime.now(),
                                "ADMIN_DEPOSIT"
                        ));
                        actionMessage = "Deposited " + amount.toPlainString() + ".";
                    }
                    case "FORCE_WITHDRAW" -> {
                        BigDecimal amount = parsePositiveAmount(getString(body, "amount"));
                        if (amount == null) {
                            return Map.of("ok", false, "message", "amount must be a positive number.");
                        }
                        if (!account.forceRemoveBalance(amount)) {
                            return Map.of("ok", false, "message", "Could not withdraw (insufficient balance).");
                        }
                        account.addTransaction(new UserTransaction(
                                account.getAccountUUID(),
                                bank.getBankId(),
                                amount,
                                LocalDateTime.now(),
                                "ADMIN_WITHDRAW"
                        ));
                        actionMessage = "Withdrew " + amount.toPlainString() + ".";
                    }
                    case "SET_TEMP_WITHDRAWAL_LIMIT" -> {
                        BigDecimal amount = parsePositiveAmount(getString(body, "amount"));
                        if (amount == null) {
                            return Map.of("ok", false, "message", "amount must be a positive number.");
                        }
                        long gameTime = 0L;
                        if (current.getLevel(Level.OVERWORLD) != null) {
                            gameTime = current.getLevel(Level.OVERWORLD).getGameTime();
                        }
                        if (!account.setTemporaryWithdrawalLimit(amount, gameTime)) {
                            return Map.of("ok", false, "message", "Temporary limit must be a positive whole dollar amount.");
                        }
                        actionMessage = "Temporary withdrawal limit updated.";
                    }
                    case "CLEAR_TEMP_WITHDRAWAL_LIMIT" -> {
                        account.clearTemporaryWithdrawalLimit();
                        actionMessage = "Temporary limit cleared.";
                    }
                    case "CLEAR_DAILY_WITHDRAWN" -> {
                        account.rollbackDailyWithdrawal(account.getDailyWithdrawnAmount());
                        actionMessage = "Daily withdrawal usage reset.";
                    }
                    case "GRANT_ACCESS_ROLE" -> {
                        UUID playerId = parseUuidOrNull(getString(body, "playerId"));
                        if (playerId == null) {
                            return Map.of("ok", false, "message", "playerId must be a valid UUID.");
                        }
                        account.grantAccessRole(playerId, getString(body, "role"));
                        actionMessage = "Access role granted/updated for " + shortId(playerId) + ".";
                    }
                    case "REVOKE_ACCESS_ROLE" -> {
                        UUID playerId = parseUuidOrNull(getString(body, "playerId"));
                        if (playerId == null) {
                            return Map.of("ok", false, "message", "playerId must be a valid UUID.");
                        }
                        account.revokeAccessRole(playerId);
                        actionMessage = "Access role revoked for " + shortId(playerId) + ".";
                    }
                    case "SAFEBOX_ADD_ITEM" -> {
                        String itemIdRaw = safeString(getString(body, "itemId"));
                        ResourceLocation key = ResourceLocation.tryParse(itemIdRaw);
                        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                            return Map.of("ok", false, "message", "itemId must be a valid item registry id.");
                        }
                        Item item = BuiltInRegistries.ITEM.get(key);
                        Integer countRaw = parseIntegerOrNull(getString(body, "count"));
                        int count = countRaw == null ? 1 : Math.max(1, countRaw);
                        int maxStack = Math.max(1, new ItemStack(item).getMaxStackSize());
                        ItemStack stack = new ItemStack(item, Math.min(count, maxStack));
                        CompoundTag stackTag = ItemStackDataCompat.saveStack(stack, current.registryAccess());

                        int maxSlots = Math.max(1, account.getSafeBoxSlotCount());
                        Integer explicitSlot = parseIntegerOrNull(getString(body, "slot"));
                        int slot = -1;
                        if (explicitSlot != null) {
                            if (explicitSlot < 0 || explicitSlot >= maxSlots) {
                                return Map.of("ok", false, "message", "slot must be within safe box capacity.");
                            }
                            if (account.getSafeBoxSlots().containsKey(explicitSlot)) {
                                return Map.of("ok", false, "message", "slot is already occupied. Delete or deliver it first.");
                            }
                            slot = explicitSlot;
                        } else {
                            for (int i = 0; i < maxSlots; i++) {
                                if (!account.getSafeBoxSlots().containsKey(i)) {
                                    slot = i;
                                    break;
                                }
                            }
                            if (slot < 0) {
                                return Map.of("ok", false, "message", "No free safe box slot available.");
                            }
                        }
                        account.getSafeBoxSlots().put(slot, stackTag);
                        actionMessage = "Safe box slot " + slot + " updated with " + itemIdRaw + " x" + stack.getCount() + ".";
                    }
                    case "SAFEBOX_DELETE_SLOT" -> {
                        Integer slot = parseIntegerOrNull(getString(body, "slot"));
                        if (slot == null || slot < 0) {
                            return Map.of("ok", false, "message", "slot must be a valid non-negative integer.");
                        }
                        CompoundTag removed = account.getSafeBoxSlots().remove(slot);
                        if (removed == null) {
                            return Map.of("ok", false, "message", "Safe box slot is empty.");
                        }
                        actionMessage = "Safe box slot " + slot + " deleted.";
                    }
                    case "SAFEBOX_DELIVER_SLOT" -> {
                        Integer slot = parseIntegerOrNull(getString(body, "slot"));
                        if (slot == null || slot < 0) {
                            return Map.of("ok", false, "message", "slot must be a valid non-negative integer.");
                        }
                        ItemStack withdrawn = account.withdrawFromSafeBox(slot, current.registryAccess());
                        if (withdrawn == null || withdrawn.isEmpty()) {
                            return Map.of("ok", false, "message", "Safe box slot is empty.");
                        }
                        Map<String, Object> delivery = deliverItemToOwnerOrQueue(
                                current,
                                accountOwner,
                                withdrawn,
                                "Safe box slot " + slot + " delivery"
                        );
                        actionExtras.putAll(delivery);
                        actionMessage = String.valueOf(delivery.getOrDefault("deliveryMessage", "Safe box item delivered."));
                    }
                    case "BLOCK_ACCOUNT_CARDS" -> {
                        CreditCardService.blockCardsForAccount(
                                centralBank,
                                accountId,
                                null,
                                "Blocked via UBS account admin"
                        );
                        actionMessage = "All account cards blocked.";
                    }
                    case "ISSUE_NEW_CARD", "ISSUE_REPLACEMENT_CARD" -> {
                        String holderName = safeString(getString(body, "holderName"));
                        UUID sourceCardId = parseUuidOrNull(getString(body, "cardId"));
                        if (holderName.isBlank() && sourceCardId != null) {
                            CompoundTag sourceRecord = centralBank.getIssuedCreditCards().get(sourceCardId);
                            if (sourceRecord != null) {
                                holderName = safeString(sourceRecord.getString("holderName"));
                            }
                        }
                        if (holderName.isBlank()) {
                            holderName = resolvePlayerName(current, account.getPlayerUUID());
                        }
                        boolean replacement = "ISSUE_REPLACEMENT_CARD".equals(action);
                        boolean privateCard = CreditCardService.isPrivateBankCardAccountEligible(centralBank, account, accountOwner);
                        CreditCardService.CardIssueResult issued = CreditCardService.issueCard(
                                centralBank,
                                account,
                                holderName,
                                replacement,
                                privateCard
                        );
                        if (!issued.success()) {
                            return Map.of("ok", false, "message", issued.message());
                        }
                        Map<String, Object> delivery = deliverItemToOwnerOrQueue(
                                current,
                                accountOwner,
                                issued.cardStack(),
                                replacement ? "Replacement credit card issuance" : "New credit card issuance"
                        );
                        actionExtras.put("cardId", issued.cardId() == null ? "" : issued.cardId().toString());
                        actionExtras.put("maskedNumber", CreditCardService.maskCardNumber(issued.cardNumber()));
                        actionExtras.put("expiryEpochMillis", issued.expiryEpochMillis());
                        actionExtras.putAll(delivery);
                        actionMessage = replacement
                                ? String.valueOf(delivery.getOrDefault("deliveryMessage", "Replacement card issued."))
                                : String.valueOf(delivery.getOrDefault("deliveryMessage", "New card issued."));
                    }
                    case "CARD_ADD_TO_INVENTORY" -> {
                        UUID cardId = parseUuidOrNull(getString(body, "cardId"));
                        if (cardId == null) {
                            return Map.of("ok", false, "message", "cardId is required.");
                        }
                        CompoundTag record = centralBank.getIssuedCreditCards().get(cardId);
                        if (record == null || !record.hasUUID("accountId") || !accountId.equals(record.getUUID("accountId"))) {
                            return Map.of("ok", false, "message", "Card not found for this account.");
                        }
                        ItemStack stack = buildCreditCardStackFromRecord(cardId, record);
                        Map<String, Object> delivery = deliverItemToOwnerOrQueue(
                                current,
                                accountOwner,
                                stack,
                                "Credit card " + shortId(cardId) + " inventory delivery"
                        );
                        actionExtras.putAll(delivery);
                        actionMessage = String.valueOf(delivery.getOrDefault("deliveryMessage", "Card delivered."));
                    }
                    case "CARD_REPLACE" -> {
                        UUID cardId = parseUuidOrNull(getString(body, "cardId"));
                        if (cardId == null) {
                            return Map.of("ok", false, "message", "cardId is required.");
                        }
                        CompoundTag record = centralBank.getIssuedCreditCards().get(cardId);
                        if (record == null || !record.hasUUID("accountId") || !accountId.equals(record.getUUID("accountId"))) {
                            return Map.of("ok", false, "message", "Card not found for this account.");
                        }
                        String holderName = safeString(record.getString("holderName"));
                        if (holderName.isBlank()) {
                            holderName = resolvePlayerName(current, accountOwner);
                        }
                        boolean privateCard = CreditCardService.isPrivateBankCardAccountEligible(centralBank, account, accountOwner);
                        CreditCardService.CardIssueResult issued = CreditCardService.issueCard(
                                centralBank,
                                account,
                                holderName,
                                true,
                                privateCard
                        );
                        if (!issued.success()) {
                            return Map.of("ok", false, "message", issued.message());
                        }
                        Map<String, Object> delivery = deliverItemToOwnerOrQueue(
                                current,
                                accountOwner,
                                issued.cardStack(),
                                "Replacement credit card issuance"
                        );
                        actionExtras.put("cardId", issued.cardId() == null ? "" : issued.cardId().toString());
                        actionExtras.put("maskedNumber", CreditCardService.maskCardNumber(issued.cardNumber()));
                        actionExtras.put("expiryEpochMillis", issued.expiryEpochMillis());
                        actionExtras.putAll(delivery);
                        actionMessage = String.valueOf(delivery.getOrDefault("deliveryMessage", "Replacement card issued."));
                    }
                    case "CARD_DELETE" -> {
                        UUID cardId = parseUuidOrNull(getString(body, "cardId"));
                        if (cardId == null) {
                            return Map.of("ok", false, "message", "cardId is required.");
                        }
                        CompoundTag removed = centralBank.getIssuedCreditCards().remove(cardId);
                        if (removed == null || !removed.hasUUID("accountId") || !accountId.equals(removed.getUUID("accountId"))) {
                            if (removed != null) {
                                centralBank.getIssuedCreditCards().put(cardId, removed);
                            }
                            return Map.of("ok", false, "message", "Card not found for this account.");
                        }
                        actionMessage = "Card deleted.";
                    }
                    case "CARD_BLOCK", "CARD_UNBLOCK" -> {
                        UUID cardId = parseUuidOrNull(getString(body, "cardId"));
                        if (cardId == null) {
                            return Map.of("ok", false, "message", "cardId is required.");
                        }
                        CompoundTag record = centralBank.getIssuedCreditCards().get(cardId);
                        if (record == null || !record.hasUUID("accountId") || !accountId.equals(record.getUUID("accountId"))) {
                            return Map.of("ok", false, "message", "Card not found for this account.");
                        }
                        boolean block = "CARD_BLOCK".equals(action);
                        record.putBoolean("blocked", block);
                        record.putString("status", block ? "BLOCKED" : "ACTIVE");
                        if (block) {
                            record.putLong("blockedEpochMillis", System.currentTimeMillis());
                            record.putString("blockedReason", "Blocked via UBS account admin");
                        } else {
                            record.remove("blockedReason");
                            record.remove("blockedEpochMillis");
                        }
                        syncCardRecordToOnlineInventories(current, cardId, record);
                        actionMessage = block ? "Card blocked." : "Card unblocked.";
                    }
                    case "LOAN_SET_DEFAULTED" -> {
                        UUID loanId = parseUuidOrNull(getString(body, "loanId"));
                        if (loanId == null) {
                            return Map.of("ok", false, "message", "loanId must be a valid UUID.");
                        }
                        AccountLoan loan = account.getActiveLoans().get(loanId);
                        if (loan == null) {
                            return Map.of("ok", false, "message", "Loan not found.");
                        }
                        boolean defaulted = parseBooleanFlag(getString(body, "defaulted"));
                        loan.setDefaulted(defaulted);
                        actionMessage = defaulted ? "Loan marked defaulted." : "Loan marked active.";
                    }
                    case "LOAN_SET_REMAINING" -> {
                        UUID loanId = parseUuidOrNull(getString(body, "loanId"));
                        if (loanId == null) {
                            return Map.of("ok", false, "message", "loanId must be a valid UUID.");
                        }
                        AccountLoan loan = account.getActiveLoans().get(loanId);
                        if (loan == null) {
                            return Map.of("ok", false, "message", "Loan not found.");
                        }
                        BigDecimal remaining = parseNonNegativeAmount(getString(body, "remaining"));
                        if (remaining == null) {
                            return Map.of("ok", false, "message", "remaining must be a valid non-negative number.");
                        }
                        loan.setRemainingBalance(remaining);
                        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                            account.removeLoan(loanId);
                            actionMessage = "Loan balance reached zero and loan was closed.";
                        } else {
                            actionMessage = "Loan remaining balance updated.";
                        }
                    }
                    case "LOAN_DELETE" -> {
                        UUID loanId = parseUuidOrNull(getString(body, "loanId"));
                        if (loanId == null) {
                            return Map.of("ok", false, "message", "loanId must be a valid UUID.");
                        }
                        if (!account.getActiveLoans().containsKey(loanId)) {
                            return Map.of("ok", false, "message", "Loan not found.");
                        }
                        account.removeLoan(loanId);
                        actionMessage = "Loan removed.";
                    }
                    case "DELETE_ACCOUNT" -> {
                        String confirm = safeString(getString(body, "confirm"));
                        if (!"DELETE".equalsIgnoreCase(confirm)) {
                            return Map.of("ok", false, "message", "Type DELETE in confirmation to remove account.");
                        }
                        boolean wasPrimary = account.isPrimaryAccount();
                        CreditCardService.blockCardsForAccount(
                                centralBank,
                                accountId,
                                null,
                                "Blocked after account deletion"
                        );
                        bank.RemoveAccount(account);
                        if (wasPrimary) {
                            assignFallbackPrimary(centralBank, accountOwner);
                        }
                        BankManager.markDirty();
                        appendAudit("ACCOUNT_ACTION", sessionId, accountOwner, detail, true);
                        sendSnapshotToSession(sessionId);
                        broadcastEvent("account_changed", Map.of(
                                "action", action,
                                "accountId", accountId.toString(),
                                "deleted", true
                        ));
                        return Map.of(
                                "ok", true,
                                "message", "Account deleted.",
                                "deleted", true,
                                "accountId", accountId.toString(),
                                "playerId", accountOwner == null ? "" : accountOwner.toString()
                        );
                    }
                    default -> {
                        return Map.of("ok", false, "message", "Unsupported account action: " + action);
                    }
                }
            } catch (Exception ex) {
                appendAudit("ACCOUNT_ACTION", sessionId, accountOwner, detail + ", error=" + ex.getMessage(), false);
                return Map.of("ok", false, "message", "Account action failed: " + ex.getMessage());
            }

            BankManager.markDirty();
            appendAudit("ACCOUNT_ACTION", sessionId, accountOwner, detail, true);
            sendSnapshotToSession(sessionId);
            broadcastEvent("account_changed", Map.of(
                    "action", action,
                    "accountId", accountId.toString(),
                    "playerId", accountOwner == null ? "" : accountOwner.toString()
            ));
            Map<String, Object> refreshed = new LinkedHashMap<>(buildAccountDetailPayload(accountId));
            refreshed.put("message", actionMessage);
            for (Map.Entry<String, Object> entry : actionExtras.entrySet()) {
                refreshed.put(entry.getKey(), entry.getValue());
            }
            return refreshed;
        });

        return ApiResponse.ok(payload);
    }

    private ApiResponse handleCardAction(String sessionId, JsonObject body, FullHttpRequest request) {
        String action = safeString(getString(body, "action")).toUpperCase(Locale.ROOT);
        if (action.isBlank()) {
            return ApiResponse.badRequest("action is required.");
        }

        UUID cardId = parseUuidOrNull(getString(body, "cardId"));
        UUID accountId = parseUuidOrNull(getString(body, "accountId"));
        String holderNameRaw = safeString(getString(body, "holderName"));

        Map<String, Object> payload = callSafely(() -> {
            MinecraftServer current = requireServer();
            CentralBank centralBank = BankManager.getCentralBank(current);
            if (centralBank == null) {
                return Map.of("ok", false, "message", "Bank data unavailable.");
            }

            UUID effectiveAccountId = accountId;
            CompoundTag referencedCard = cardId == null ? null : centralBank.getIssuedCreditCards().get(cardId);
            if (effectiveAccountId == null && referencedCard != null && referencedCard.hasUUID("accountId")) {
                effectiveAccountId = referencedCard.getUUID("accountId");
            }
            if (effectiveAccountId == null) {
                return Map.of("ok", false, "message", "accountId or resolvable cardId is required.");
            }

            UUID auditPlayer = resolveSelectedPlayer(sessionId, current, false);
            String source = remoteAddress(request);

            if ("BLOCK_ACCOUNT_CARDS".equals(action)) {
                CreditCardService.blockCardsForAccount(
                        centralBank,
                        effectiveAccountId,
                        null,
                        "Blocked via UBS web admin"
                );
                BankManager.markDirty();
                appendAudit(
                        "CARD_ACTION",
                        sessionId,
                        auditPlayer,
                        "action=BLOCK_ACCOUNT_CARDS, accountId=" + effectiveAccountId + ", source=" + source,
                        true
                );
                broadcastEvent("card_changed", Map.of(
                        "action", action,
                        "accountId", effectiveAccountId.toString()
                ));
                return Map.of(
                        "ok", true,
                        "message", "Blocked active cards for account " + shortId(effectiveAccountId) + ".",
                        "accountId", effectiveAccountId.toString()
                );
            }

            if ("ISSUE_REPLACEMENT".equals(action)) {
                AccountHolder account = centralBank.SearchForAccountByAccountId(effectiveAccountId);
                if (account == null) {
                    return Map.of("ok", false, "message", "Account not found.");
                }

                String holderName = holderNameRaw.isBlank()
                        ? resolvePlayerName(current, account.getPlayerUUID())
                        : holderNameRaw;
                boolean privateCard = CreditCardService.isPrivateBankCardAccountEligible(
                        centralBank,
                        account,
                        account.getPlayerUUID()
                );
                CreditCardService.CardIssueResult issued = CreditCardService.issueCard(
                        centralBank,
                        account,
                        holderName,
                        true,
                        privateCard
                );
                if (!issued.success()) {
                    appendAudit(
                            "CARD_ACTION",
                            sessionId,
                            auditPlayer,
                            "action=ISSUE_REPLACEMENT, accountId=" + effectiveAccountId + ", source=" + source,
                            false
                    );
                    return Map.of("ok", false, "message", issued.message());
                }

                Map<String, Object> delivery = deliverItemToOwnerOrQueue(
                        current,
                        account.getPlayerUUID(),
                        issued.cardStack(),
                        "Replacement credit card issuance"
                );

                appendAudit(
                        "CARD_ACTION",
                        sessionId,
                        auditPlayer,
                        "action=ISSUE_REPLACEMENT, accountId=" + effectiveAccountId + ", cardId="
                                + (issued.cardId() == null ? "" : issued.cardId()) + ", source=" + source,
                        true
                );
                broadcastEvent("card_changed", Map.of(
                        "action", action,
                        "accountId", effectiveAccountId.toString(),
                        "cardId", issued.cardId() == null ? "" : issued.cardId().toString()
                ));
                return Map.of(
                        "ok", true,
                        "message", delivery.getOrDefault("deliveryMessage", "Replacement card issued."),
                        "accountId", effectiveAccountId.toString(),
                        "cardId", issued.cardId() == null ? "" : issued.cardId().toString(),
                        "maskedNumber", CreditCardService.maskCardNumber(issued.cardNumber()),
                        "expiryEpochMillis", issued.expiryEpochMillis(),
                        "deliveryStatus", delivery.getOrDefault("deliveryStatus", "unknown"),
                        "deliveryQueued", delivery.getOrDefault("deliveryQueued", false)
                );
            }

            return Map.of("ok", false, "message", "Unsupported card action: " + action);
        });

        return ApiResponse.ok(payload);
    }

    private record EconomySnapshot(
            long capturedAtMillis,
            int banksTotal,
            int activeBanks,
            int flaggedBanks,
            int accountsTotal,
            BigDecimal totalDeposits,
            BigDecimal totalReserves,
            BigDecimal moneyCirculating,
            int onlinePlayers,
            int shopsTotal,
            long shopsRevenueDollars,
            int issuedCards,
            int activeCards,
            int blockedCards,
            Map<String, Integer> bankStatusCounts,
            Map<String, Integer> shopTypeCounts
    ) {}

    /**
     * v2 dashboard payload focused on analytics and admin overviews.
     */
    private Map<String, Object> buildDashboardPayload() {
        MinecraftServer current = requireServer();
        CentralBank centralBank = BankManager.getCentralBank(current);
        captureHourlyEconomySnapshotIfDue();

        EconomySnapshot snapshot = collectEconomySnapshot(current, centralBank);
        List<Map<String, Object>> bankRows = buildBankRows(current, centralBank);
        List<Map<String, Object>> shopRows = buildShopRows(current, centralBank);
        List<Map<String, Object>> historyPoints = readEconomyHistory(centralBank, System.currentTimeMillis());

        List<Map<String, Object>> topBanks = bankRows.stream()
                .sorted((a, b) -> Double.compare(asDouble(b.get("deposits")), asDouble(a.get("deposits"))))
                .limit(6)
                .toList();
        List<Map<String, Object>> topShops = shopRows.stream()
                .sorted((a, b) -> Double.compare(asDouble(b.get("revenueDollars")), asDouble(a.get("revenueDollars"))))
                .limit(6)
                .toList();

        BigDecimal reserveRatio = snapshot.totalDeposits().compareTo(BigDecimal.ZERO) > 0
                ? snapshot.totalReserves().divide(snapshot.totalDeposits(), 6, RoundingMode.HALF_EVEN)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.valueOf(100);

        List<String> warnings = new ArrayList<>();
        if (snapshot.flaggedBanks() > 0) {
            warnings.add(snapshot.flaggedBanks() + " bank(s) are not ACTIVE.");
        }
        if (reserveRatio.compareTo(BigDecimal.valueOf(10)) < 0) {
            warnings.add("Global reserve ratio is below 10%.");
        }
        if (warnings.isEmpty()) {
            warnings.add("No critical economy alerts.");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("generatedAt", Instant.now().toString());
        response.put("webAdmin", Map.of(
                "bindHost", bindHost,
                "bindPort", bindPort,
                "running", running
        ));
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("banksTotal", snapshot.banksTotal());
        kpis.put("activeBanks", snapshot.activeBanks());
        kpis.put("flaggedBanks", snapshot.flaggedBanks());
        kpis.put("accountsTotal", snapshot.accountsTotal());
        kpis.put("totalDeposits", decimal2(snapshot.totalDeposits()));
        kpis.put("totalReserves", decimal2(snapshot.totalReserves()));
        kpis.put("reserveRatioPct", decimal2(reserveRatio));
        kpis.put("moneyCirculating", decimal2(snapshot.moneyCirculating()));
        kpis.put("shopsTotal", snapshot.shopsTotal());
        kpis.put("shopsRevenueDollars", snapshot.shopsRevenueDollars());
        kpis.put("issuedCards", snapshot.issuedCards());
        kpis.put("activeCards", snapshot.activeCards());
        kpis.put("blockedCards", snapshot.blockedCards());
        kpis.put("onlinePlayers", snapshot.onlinePlayers());
        kpis.put("wsClients", webSocketChannels.size());
        response.put("kpis", kpis);
        response.put("charts", Map.of(
                "economyHistory", historyPoints,
                "bankStatus", toCountRows(snapshot.bankStatusCounts(), "status"),
                "shopType", toCountRows(snapshot.shopTypeCounts(), "type"),
                "topBanksByDeposits", topBanks,
                "topShopsByRevenue", topShops
        ));
        response.put("highlights", Map.of("warnings", warnings));
        response.put("serverHealth", buildHealthPayload());
        return response;
    }

    private Map<String, Object> buildHistoryPayload() {
        MinecraftServer current = requireServer();
        CentralBank centralBank = BankManager.getCentralBank(current);
        captureHourlyEconomySnapshotIfDue();
        return Map.of(
                "ok", true,
                "points", readEconomyHistory(centralBank, System.currentTimeMillis())
        );
    }

    private Map<String, Object> buildBanksPayload() {
        MinecraftServer current = requireServer();
        CentralBank centralBank = BankManager.getCentralBank(current);
        List<Map<String, Object>> rows = buildBankRows(current, centralBank);
        EconomySnapshot snapshot = collectEconomySnapshot(current, centralBank);

        return Map.of(
                "ok", true,
                "rows", rows,
                "metrics", Map.of(
                        "banksTotal", snapshot.banksTotal(),
                        "activeBanks", snapshot.activeBanks(),
                        "flaggedBanks", snapshot.flaggedBanks(),
                        "accountsTotal", snapshot.accountsTotal(),
                        "totalDeposits", decimal2(snapshot.totalDeposits()),
                        "totalReserves", decimal2(snapshot.totalReserves())
                )
        );
    }

    private Map<String, Object> buildShopsPayload() {
        MinecraftServer current = requireServer();
        CentralBank centralBank = BankManager.getCentralBank(current);
        List<Map<String, Object>> rows = buildShopRows(current, centralBank);
        EconomySnapshot snapshot = collectEconomySnapshot(current, centralBank);

        double avgLevel = 0.0;
        if (!rows.isEmpty()) {
            double total = 0.0;
            for (Map<String, Object> row : rows) {
                total += asDouble(row.get("level"));
            }
            avgLevel = total / rows.size();
        }

        return Map.of(
                "ok", true,
                "rows", rows,
                "metrics", Map.of(
                        "shopsTotal", snapshot.shopsTotal(),
                        "revenueDollars", snapshot.shopsRevenueDollars(),
                        "avgLevel", round2(avgLevel)
                )
        );
    }

    /**
     * Aggregates all priced shelf items across every shop so admins can compare
     * world-wide low/avg/high prices and quickly spot outliers.
     */
    private Map<String, Object> buildShopItemsPayload() {
        MinecraftServer current = requireServer();
        CentralBank centralBank = BankManager.getCentralBank(current);
        if (centralBank == null) {
            return Map.of(
                    "ok", true,
                    "rows", List.of(),
                    "metrics", Map.of(
                            "itemsTotal", 0,
                            "shopsScanned", 0,
                            "shopsWithListings", 0,
                            "listingsTotal", 0
                    )
            );
        }

        List<ShopService.ShopSummary> summaries = ShopService.listAllShopSummaries(centralBank);
        Map<String, ShopItemPriceAggregate> aggregates = new LinkedHashMap<>();
        int shopsWithListings = 0;
        int listingsTotal = 0;

        for (ShopService.ShopSummary summary : summaries) {
            if (summary == null || summary.shopId() == null) {
                continue;
            }
            UUID ownerId = ShopService.resolveShopOwnerId(centralBank, summary.shopId());
            if (ownerId == null) {
                continue;
            }

            Map<String, List<String>> tokens = parseShopReportTokens(
                    ShopService.shelfReport(current, centralBank, ownerId, summary.shopId()).message()
            );
            if (tokens.isEmpty()) {
                continue;
            }

            Map<Integer, Boolean> creativeByShelfIndex = new HashMap<>();
            for (String[] row : shopTokenRows(tokens, "shelf_card", 11)) {
                int shelfIndex = parseIntToken(row[0], 0);
                if (shelfIndex <= 0) {
                    continue;
                }
                creativeByShelfIndex.put(shelfIndex, "CREATIVE".equalsIgnoreCase(row[5]));
            }

            Map<String, ShopItemListingAccumulator> perShopItems = new LinkedHashMap<>();
            for (String[] row : shopTokenRows(tokens, "shelf_item", 13)) {
                int shelfIndex = parseIntToken(row[0], 0);
                boolean creativeSource = creativeByShelfIndex.getOrDefault(shelfIndex, false);
                String itemId = safeString(row[2]);
                if (itemId.isBlank()) {
                    continue;
                }

                String itemKey = itemId.toLowerCase(Locale.ROOT);
                String itemName = row.length >= 8 ? safeString(row[7]) : "";
                long priceCents = Math.max(0L, parseLongToken(row[3], 0L));
                ShopItemListingAccumulator listing = perShopItems.computeIfAbsent(
                        itemKey,
                        ignored -> new ShopItemListingAccumulator(itemId, itemName)
                );
                listing.addListing(priceCents, creativeSource, itemName);
            }

            if (perShopItems.isEmpty()) {
                continue;
            }
            shopsWithListings++;

            String shopName = safeString(summary.name()).isBlank() ? "Unnamed Shop" : safeString(summary.name());
            for (ShopItemListingAccumulator listing : perShopItems.values()) {
                if (listing == null || listing.itemId().isBlank()) {
                    continue;
                }
                listingsTotal += Math.max(0, listing.listingCount());
                String aggregateKey = listing.itemId().toLowerCase(Locale.ROOT);
                ShopItemPriceAggregate aggregate = aggregates.computeIfAbsent(
                        aggregateKey,
                        ignored -> new ShopItemPriceAggregate(listing.itemId(), listing.itemName())
                );
                aggregate.includeShop(
                        summary.shopId(),
                        shopName,
                        listing.averagePriceCents(),
                        listing.listingCount(),
                        listing.hasCreativeSource(),
                        listing.hasNormalSource(),
                        listing.itemName()
                );
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ShopItemPriceAggregate aggregate : aggregates.values()) {
            if (aggregate == null || aggregate.shopCount() <= 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", aggregate.itemId());
            row.put("itemName", aggregate.itemName());
            row.put("shopsSelling", aggregate.shopCount());
            row.put("listingCount", aggregate.listingCount());
            row.put("priceLowCents", round2(aggregate.lowestPriceCents()));
            row.put("priceAvgCents", round2(aggregate.averagePriceCents()));
            row.put("priceHighCents", round2(aggregate.highestPriceCents()));
            row.put("lowShopId", aggregate.lowShopId());
            row.put("lowShopName", aggregate.lowShopName());
            row.put("highShopId", aggregate.highShopId());
            row.put("highShopName", aggregate.highShopName());
            row.put("hasCreativeSources", aggregate.hasCreativeSources());
            row.put("hasNormalSources", aggregate.hasNormalSources());
            row.put("creativeShops", aggregate.creativeShopCount());
            row.put("normalShops", aggregate.normalShopCount());
            rows.add(row);
        }
        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> String.valueOf(row.get("itemName")), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> String.valueOf(row.get("itemId")), String.CASE_INSENSITIVE_ORDER));

        return Map.of(
                "ok", true,
                "rows", rows,
                "metrics", Map.of(
                        "itemsTotal", rows.size(),
                        "shopsScanned", summaries.size(),
                        "shopsWithListings", shopsWithListings,
                        "listingsTotal", listingsTotal
                )
        );
    }

    /**
     * Builds deep analytics for a single item across all shops.
     *
     * <p>Data is derived from live shelf listings plus per-slot daily sales metadata
     * already tracked by the shop system. No external services are used.</p>
     */
    private Map<String, Object> buildShopItemDetailPayload(String rawItemId) {
        MinecraftServer current = requireServer();
        CentralBank centralBank = BankManager.getCentralBank(current);
        if (centralBank == null) {
            return Map.of("ok", false, "message", "Shop data unavailable.");
        }

        String decoded = decodeItemId(rawItemId);
        ResourceLocation parsedItemId = parseItemResourceLocation(decoded);
        if (parsedItemId == null) {
            return Map.of("ok", false, "message", "Invalid item id.");
        }

        String normalizedItemId = parsedItemId.toString();
        String normalizedItemLower = normalizedItemId.toLowerCase(Locale.ROOT);
        long nowMillis = System.currentTimeMillis();
        long nowDay = Math.floorDiv(Math.max(0L, nowMillis), 86_400_000L);

        List<ShopService.ShopSummary> summaries = ShopService.listAllShopSummaries(centralBank);
        Map<UUID, ShopItemShopAggregate> shopAggregates = new LinkedHashMap<>();
        List<Map<String, Object>> listingRows = new ArrayList<>();
        List<Long> listingPrices = new ArrayList<>();
        Map<Long, Long> unitsSoldByDay = new HashMap<>();
        Map<Long, Double> revenueCentsByDay = new HashMap<>();
        Map<Long, Double> weightedPriceCentsByDay = new HashMap<>();
        Map<Long, Double> weightedUnitsByDay = new HashMap<>();

        String resolvedItemName = "";
        long latestSaleMillis = 0L;
        int creativeListings = 0;
        int normalListings = 0;
        long trackedStockUnits = 0L;
        int trackedStockSlots = 0;
        double totalVelocityPerDay = 0.0D;

        for (ShopService.ShopSummary summary : summaries) {
            if (summary == null || summary.shopId() == null) {
                continue;
            }
            UUID ownerId = ShopService.resolveShopOwnerId(centralBank, summary.shopId());
            if (ownerId == null) {
                continue;
            }

            Map<String, List<String>> shelfTokens = parseShopReportTokens(
                    ShopService.shelfReport(current, centralBank, ownerId, summary.shopId()).message()
            );
            if (shelfTokens.isEmpty()) {
                continue;
            }

            Map<Integer, Boolean> creativeByShelfIndex = new HashMap<>();
            for (String[] row : shopTokenRows(shelfTokens, "shelf_card", 11)) {
                int shelfIndex = parseIntToken(row[0], 0);
                if (shelfIndex <= 0) {
                    continue;
                }
                creativeByShelfIndex.put(shelfIndex, "CREATIVE".equalsIgnoreCase(row[5]));
            }

            CompoundTag shopTag = findShopTagById(centralBank, summary.shopId());
            Map<String, Map<Long, Long>> slotSalesByKey = extractShopSlotDailySalesByKey(shopTag);
            String shopName = safeString(summary.name()).isBlank() ? "Unnamed Shop" : safeString(summary.name());

            for (String[] row : shopTokenRows(shelfTokens, "shelf_item", 13)) {
                String listedItemId = safeString(row[2]);
                if (listedItemId.isBlank()) {
                    continue;
                }
                if (!listedItemId.equalsIgnoreCase(normalizedItemId)
                        && !listedItemId.toLowerCase(Locale.ROOT).equals(normalizedItemLower)) {
                    continue;
                }

                int shelfIndex = parseIntToken(row[0], 0);
                int slot = parseIntToken(row[1], 0);
                long priceCents = Math.max(0L, parseLongToken(row[3], 0L));
                int stock = parseIntToken(row[4], -1);
                boolean restockable = parseIntToken(row[5], 0) > 0;
                String slotKey = safeString(row[6]);
                String itemName = safeString(row[7]);
                int minTarget = parseIntToken(row[8], 0);
                int maxTarget = parseIntToken(row[9], 0);
                int stockroomAvailable = parseIntToken(row[10], -1);
                long lastSoldMillis = Math.max(0L, parseLongToken(row[11], 0L));
                double velocityPerDay = Math.max(0.0D, parseDoubleToken(row[12], 0.0D));
                boolean creativeSource = creativeByShelfIndex.getOrDefault(shelfIndex, false);

                if (resolvedItemName.isBlank() && !itemName.isBlank()) {
                    resolvedItemName = itemName;
                }

                ShopItemShopAggregate aggregate = shopAggregates.computeIfAbsent(
                        summary.shopId(),
                        ignored -> new ShopItemShopAggregate(summary.shopId(), shopName, safeString(summary.type()))
                );
                aggregate.addListing(priceCents, stock, velocityPerDay, lastSoldMillis, creativeSource);

                Map<String, Object> listing = new LinkedHashMap<>();
                listing.put("shopId", summary.shopId().toString());
                listing.put("shopName", shopName);
                listing.put("shopType", safeString(summary.type()));
                listing.put("shelfIndex", shelfIndex);
                listing.put("slot", slot);
                listing.put("itemId", listedItemId);
                listing.put("itemName", itemName);
                listing.put("priceCents", priceCents);
                listing.put("stock", stock);
                listing.put("restockable", restockable);
                listing.put("slotKey", slotKey);
                listing.put("minTarget", minTarget);
                listing.put("maxTarget", maxTarget);
                listing.put("stockroomAvailable", stockroomAvailable);
                listing.put("velocityPerDay", round2(velocityPerDay));
                listing.put("lastSoldMillis", lastSoldMillis);
                listing.put("creativeSource", creativeSource);
                listingRows.add(listing);

                listingPrices.add(priceCents);
                totalVelocityPerDay += velocityPerDay;
                latestSaleMillis = Math.max(latestSaleMillis, lastSoldMillis);
                if (creativeSource) {
                    creativeListings++;
                } else {
                    normalListings++;
                }
                if (stock >= 0) {
                    trackedStockUnits += stock;
                    trackedStockSlots++;
                }

                if (!slotKey.isBlank()) {
                    Map<Long, Long> salesByDay = slotSalesByKey.get(slotKey.toLowerCase(Locale.ROOT));
                    if (salesByDay != null && !salesByDay.isEmpty()) {
                        for (Map.Entry<Long, Long> salesEntry : salesByDay.entrySet()) {
                            long day = salesEntry.getKey() == null ? 0L : salesEntry.getKey();
                            long units = salesEntry.getValue() == null ? 0L : Math.max(0L, salesEntry.getValue());
                            if (day <= 0L || units <= 0L) {
                                continue;
                            }
                            unitsSoldByDay.merge(day, units, Long::sum);
                            revenueCentsByDay.merge(day, units * (double) priceCents, Double::sum);
                            weightedPriceCentsByDay.merge(day, units * (double) priceCents, Double::sum);
                            weightedUnitsByDay.merge(day, (double) units, Double::sum);
                        }
                    }
                }
            }
        }

        if (listingRows.isEmpty()) {
            return Map.of("ok", false, "message", "Item not found in current shop listings.");
        }

        listingRows.sort(Comparator
                .comparing((Map<String, Object> row) -> String.valueOf(row.get("shopName")), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> asLong(row.get("priceCents"))));

        List<Map<String, Object>> shopRows = new ArrayList<>();
        for (ShopItemShopAggregate aggregate : shopAggregates.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shopId", aggregate.shopId().toString());
            row.put("shopName", aggregate.shopName());
            row.put("shopType", aggregate.shopType());
            row.put("listingCount", aggregate.listingCount());
            row.put("priceLowCents", aggregate.minPriceCents());
            row.put("priceAvgCents", round2(aggregate.avgPriceCents()));
            row.put("priceHighCents", aggregate.maxPriceCents());
            row.put("velocityPerDay", round2(aggregate.totalVelocityPerDay()));
            row.put("stockUnits", aggregate.stockUnits());
            row.put("creativeSource", aggregate.creativeSource());
            row.put("lastSoldMillis", aggregate.lastSoldMillis());
            shopRows.add(row);
        }
        shopRows.sort(Comparator
                .comparing((Map<String, Object> row) -> asDouble(row.get("priceAvgCents")))
                .thenComparing(row -> String.valueOf(row.get("shopName")), String.CASE_INSENSITIVE_ORDER));

        long minPriceCents = listingPrices.stream().mapToLong(value -> value == null ? 0L : value).min().orElse(0L);
        long maxPriceCents = listingPrices.stream().mapToLong(value -> value == null ? 0L : value).max().orElse(0L);
        double avgPriceCents = listingPrices.stream().mapToLong(value -> value == null ? 0L : value).average().orElse(0.0D);
        double spreadPct = avgPriceCents > 0.0D
                ? ((maxPriceCents - minPriceCents) / avgPriceCents) * 100.0D
                : 0.0D;
        double avgVelocityPerListing = listingRows.isEmpty() ? 0.0D : totalVelocityPerDay / Math.max(1, listingRows.size());
        long monthlyUnitsEstimate = Math.max(0L, Math.round(totalVelocityPerDay * 30.0D));
        double volatilityPct = computeItemPriceVolatilityPct(shopRows);

        List<Map<String, Object>> dailyRows = buildShopItemDailySeries(
                nowDay,
                unitsSoldByDay,
                revenueCentsByDay,
                weightedPriceCentsByDay,
                weightedUnitsByDay,
                avgPriceCents,
                totalVelocityPerDay
        );
        boolean estimatedDemandSeries = dailyRows.stream().anyMatch(row -> Boolean.TRUE.equals(row.get("estimated")));

        List<Map<String, Object>> alerts = new ArrayList<>();
        if (shopRows.size() <= 1) {
            alerts.add(Map.of("tone", "warn", "text", "Single-shop market: this item is currently sold by only one shop."));
        } else {
            alerts.add(Map.of("tone", "ok", "text", "Multi-shop market: " + shopRows.size() + " shops are actively listing this item."));
        }
        if (spreadPct >= 40.0D) {
            alerts.add(Map.of("tone", "warn", "text", "High price dispersion: spread is " + round2(spreadPct) + "%, indicating unstable pricing."));
        } else {
            alerts.add(Map.of("tone", "ok", "text", "Price spread is " + round2(spreadPct) + "%, indicating relatively stable market pricing."));
        }
        if (latestSaleMillis <= 0L) {
            alerts.add(Map.of("tone", "warn", "text", "No recorded sale timestamp found for this item yet."));
        } else if ((nowMillis - latestSaleMillis) > (14L * 24L * 60L * 60L * 1000L)) {
            alerts.add(Map.of("tone", "warn", "text", "Last sale is older than 14 days; demand may be weakening."));
        } else {
            alerts.add(Map.of("tone", "ok", "text", "Recent sale activity detected in the last 14 days."));
        }
        if (estimatedDemandSeries) {
            alerts.add(Map.of("tone", "warn", "text", "Demand chart is partially estimated from velocity because direct daily slot sales are sparse."));
        }

        long soldUnits7d = 0L;
        long soldUnits30d = 0L;
        double revenueCents7d = 0.0D;
        double revenueCents30d = 0.0D;
        for (Map<String, Object> row : dailyRows) {
            long day = asLong(row.get("dayEpoch"));
            long units = asLong(row.get("unitsSold"));
            double revenueCents = asDouble(row.get("revenueCents"));
            if (day >= nowDay - 29L) {
                soldUnits30d += units;
                revenueCents30d += revenueCents;
            }
            if (day >= nowDay - 6L) {
                soldUnits7d += units;
                revenueCents7d += revenueCents;
            }
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemId", normalizedItemId);
        item.put("itemName", resolvedItemName.isBlank() ? normalizedItemId : resolvedItemName);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("shopsSelling", shopRows.size());
        kpis.put("listingCount", listingRows.size());
        kpis.put("priceLowCents", minPriceCents);
        kpis.put("priceAvgCents", round2(avgPriceCents));
        kpis.put("priceHighCents", maxPriceCents);
        kpis.put("priceSpreadPct", round2(spreadPct));
        kpis.put("priceVolatilityPct", round2(volatilityPct));
        kpis.put("velocityPerDay", round2(totalVelocityPerDay));
        kpis.put("avgVelocityPerListing", round2(avgVelocityPerListing));
        kpis.put("monthlyUnitsEstimate", monthlyUnitsEstimate);
        kpis.put("trackedStockUnits", trackedStockUnits);
        kpis.put("trackedStockSlots", trackedStockSlots);
        kpis.put("creativeListings", creativeListings);
        kpis.put("normalListings", normalListings);
        kpis.put("lastSoldMillis", latestSaleMillis);
        kpis.put("soldUnits7d", soldUnits7d);
        kpis.put("soldUnits30d", soldUnits30d);
        kpis.put("revenueCents7d", round2(revenueCents7d));
        kpis.put("revenueCents30d", round2(revenueCents30d));

        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("daily", dailyRows);
        charts.put("shopPrices", shopRows);
        charts.put("priceDistribution", buildItemPriceDistributionRows(listingPrices));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("item", item);
        response.put("kpis", kpis);
        response.put("alerts", alerts);
        response.put("charts", charts);
        response.put("shops", shopRows);
        response.put("listings", listingRows);
        return response;
    }

    private Map<String, Map<Long, Long>> extractShopSlotDailySalesByKey(CompoundTag shopTag) {
        Map<String, Map<Long, Long>> out = new HashMap<>();
        if (shopTag == null || !shopTag.contains("shelf_slot_meta", Tag.TAG_LIST)) {
            return out;
        }
        ListTag slotMeta = shopTag.getList("shelf_slot_meta", Tag.TAG_COMPOUND);
        for (Tag tag : slotMeta) {
            if (!(tag instanceof CompoundTag meta)) {
                continue;
            }
            String slotKey = safeString(meta.getString("slot_key")).toLowerCase(Locale.ROOT);
            if (slotKey.isBlank()) {
                continue;
            }
            ListTag sales = meta.getList("slot_daily_sales", Tag.TAG_COMPOUND);
            if (sales.isEmpty()) {
                continue;
            }
            Map<Long, Long> byDay = out.computeIfAbsent(slotKey, ignored -> new HashMap<>());
            for (Tag salesTag : sales) {
                if (!(salesTag instanceof CompoundTag dayEntry)) {
                    continue;
                }
                long day = Math.max(0L, dayEntry.getLong("day"));
                long amount = Math.max(0L, dayEntry.getLong("amount"));
                if (day <= 0L || amount <= 0L) {
                    continue;
                }
                byDay.merge(day, amount, Long::sum);
            }
        }
        return out;
    }

    private List<Map<String, Object>> buildShopItemDailySeries(long nowDay,
                                                               Map<Long, Long> unitsSoldByDay,
                                                               Map<Long, Double> revenueCentsByDay,
                                                               Map<Long, Double> weightedPriceCentsByDay,
                                                               Map<Long, Double> weightedUnitsByDay,
                                                               double fallbackAvgPriceCents,
                                                               double fallbackVelocityPerDay) {
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean hasRealSales = unitsSoldByDay != null && !unitsSoldByDay.isEmpty();
        for (long day = nowDay - 29L; day <= nowDay; day++) {
            long units = unitsSoldByDay == null ? 0L : Math.max(0L, unitsSoldByDay.getOrDefault(day, 0L));
            double revenueCents = revenueCentsByDay == null ? 0.0D : Math.max(0.0D, revenueCentsByDay.getOrDefault(day, 0.0D));
            double weightedUnits = weightedUnitsByDay == null ? 0.0D : Math.max(0.0D, weightedUnitsByDay.getOrDefault(day, 0.0D));
            double weightedPriceCents = weightedPriceCentsByDay == null ? 0.0D : Math.max(0.0D, weightedPriceCentsByDay.getOrDefault(day, 0.0D));
            double avgPriceCents = weightedUnits > 0.0D ? weightedPriceCents / weightedUnits : Math.max(0.0D, fallbackAvgPriceCents);
            boolean estimated = false;

            if (!hasRealSales && fallbackVelocityPerDay > 0.0D) {
                // Demand fallback keeps the chart readable before enough sales history accumulates.
                double weekdayPhase = Math.sin((day % 7L) * (Math.PI / 3.5D));
                double multiplier = 0.86D + (weekdayPhase * 0.18D);
                units = Math.max(0L, Math.round(fallbackVelocityPerDay * multiplier));
                revenueCents = units * Math.max(0.0D, fallbackAvgPriceCents);
                avgPriceCents = Math.max(0.0D, fallbackAvgPriceCents);
                estimated = true;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dayEpoch", day);
            row.put("date", LocalDate.ofEpochDay(day).toString());
            row.put("capturedAtMillis", day * 86_400_000L);
            row.put("unitsSold", units);
            row.put("revenueCents", round2(revenueCents));
            row.put("avgPriceCents", round2(avgPriceCents));
            row.put("estimated", estimated);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> buildItemPriceDistributionRows(List<Long> listingPrices) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (listingPrices == null || listingPrices.isEmpty()) {
            return rows;
        }

        long min = listingPrices.stream().mapToLong(value -> value == null ? 0L : value).min().orElse(0L);
        long max = listingPrices.stream().mapToLong(value -> value == null ? 0L : value).max().orElse(0L);
        if (min == max) {
            rows.add(Map.of(
                    "label", min + "c",
                    "fromCents", min,
                    "toCents", max,
                    "count", listingPrices.size()
            ));
            return rows;
        }

        int bucketCount = Math.max(4, Math.min(8, (int) Math.ceil(Math.sqrt(listingPrices.size()))));
        double span = max - min;
        double step = Math.max(1.0D, span / bucketCount);
        int[] buckets = new int[bucketCount];
        for (Long raw : listingPrices) {
            long value = raw == null ? 0L : Math.max(0L, raw);
            int index = (int) Math.floor((value - min) / step);
            if (index >= bucketCount) {
                index = bucketCount - 1;
            }
            if (index < 0) {
                index = 0;
            }
            buckets[index]++;
        }

        for (int i = 0; i < bucketCount; i++) {
            long from = Math.round(min + (step * i));
            long to = (i == bucketCount - 1)
                    ? max
                    : Math.round(min + (step * (i + 1)) - 1.0D);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", from + "c - " + to + "c");
            row.put("fromCents", from);
            row.put("toCents", to);
            row.put("count", buckets[i]);
            rows.add(row);
        }
        return rows;
    }

    private double computeItemPriceVolatilityPct(List<Map<String, Object>> shopRows) {
        if (shopRows == null || shopRows.isEmpty()) {
            return 0.0D;
        }
        List<Double> values = new ArrayList<>();
        for (Map<String, Object> row : shopRows) {
            double value = asDouble(row.get("priceAvgCents"));
            if (value > 0.0D) {
                values.add(value);
            }
        }
        if (values.size() <= 1) {
            return 0.0D;
        }
        double mean = values.stream().mapToDouble(v -> v).average().orElse(0.0D);
        if (mean <= 0.0D) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (double value : values) {
            double delta = value - mean;
            sum += delta * delta;
        }
        double stdDev = Math.sqrt(sum / values.size());
        return (stdDev / mean) * 100.0D;
    }

    private Map<String, Object> buildShopDetailPayload(UUID shopId) {
        MinecraftServer current = requireServer();
        CentralBank centralBank = BankManager.getCentralBank(current);
        if (centralBank == null || shopId == null) {
            return Map.of("ok", false, "message", "Shop data unavailable.");
        }

        ShopService.ShopSummary summary = null;
        for (ShopService.ShopSummary row : ShopService.listAllShopSummaries(centralBank)) {
            if (row != null && shopId.equals(row.shopId())) {
                summary = row;
                break;
            }
        }
        if (summary == null) {
            return Map.of("ok", false, "message", "Shop not found.");
        }

        CompoundTag shopTag = findShopTagById(centralBank, shopId);
        UUID ownerId = ShopService.resolveShopOwnerId(centralBank, shopId);
        Map<UUID, String> participantRoles = ShopService.listShopParticipantRoles(centralBank, shopId);
        if (ownerId == null) {
            for (Map.Entry<UUID, String> entry : participantRoles.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                if (ShopService.SHOP_ROLE_OWNER.equalsIgnoreCase(safeString(entry.getValue()))) {
                    ownerId = entry.getKey();
                    break;
                }
            }
        }

        Map<UUID, String> nameHints = collectPlayerNameHints(current, centralBank);
        String ownerName = resolvePlayerName(current, ownerId, nameHints);

        Map<String, List<String>> overviewTokens = Map.of();
        Map<String, List<String>> roadmapTokens = Map.of();
        Map<String, List<String>> hoursTokens = Map.of();
        Map<String, List<String>> financeTokens = Map.of();
        Map<String, List<String>> vaultTokens = Map.of();
        Map<String, List<String>> orderTokens = Map.of();
        Map<String, List<String>> employeeTokens = Map.of();
        Map<String, List<String>> shelfTokens = Map.of();
        Map<String, List<String>> stockroomTokens = Map.of();
        if (ownerId != null) {
            overviewTokens = parseShopReportTokens(ShopService.overview(current, centralBank, ownerId, shopId).message());
            roadmapTokens = parseShopReportTokens(ShopService.levelRoadmapReport(centralBank, ownerId, shopId).message());
            hoursTokens = parseShopReportTokens(ShopService.shopHoursLightingReport(current, centralBank, ownerId, shopId).message());
            financeTokens = parseShopReportTokens(ShopService.financeReport(current, centralBank, ownerId, shopId).message());
            vaultTokens = parseShopReportTokens(ShopService.cashVaultReport(centralBank, ownerId, shopId).message());
            orderTokens = parseShopReportTokens(ShopService.orderManagerReport(current, centralBank, ownerId, shopId).message());
            employeeTokens = parseShopReportTokens(ShopService.listEmployeesReport(current, centralBank, ownerId, shopId).message());
            shelfTokens = parseShopReportTokens(ShopService.shelfReport(current, centralBank, ownerId, shopId).message());
            stockroomTokens = parseShopReportTokens(ShopService.stockroomReport(current, centralBank, ownerId, shopId).message());
        }

        List<Map<String, Object>> claimRows = extractShopRegionRows(shopTag == null ? null : shopTag.getList("claims", Tag.TAG_COMPOUND));
        List<Map<String, Object>> stockroomRows = extractShopRegionRows(shopTag == null ? null : shopTag.getList("stockroom_claims", Tag.TAG_COMPOUND));

        int level = Math.max(1, summary.level());
        long claimCapacity = ShopService.claimCapacityForLevel(level);
        long stockroomCapacity = ShopService.stockroomCapacityForLevel(level);
        int displayCapacity = ShopService.maxDisplayBlocksForLevel(level);
        int cashierCapacity = ShopService.maxCashierSpawnEggsForLevel(level);
        int palletCapacity = ShopService.maxAssignedOrderPalletsForLevel(level);

        long revenueDollars = parseLongToken(firstShopToken(overviewTokens, "kpi.revenue_dollars"), Math.max(0L, summary.revenueDollars()));
        long targetDollars = parseLongToken(firstShopToken(overviewTokens, "kpi.target_dollars"), Math.max(1L, summary.nextTargetDollars()));
        long claimUsedBlocks = parseLongToken(firstShopToken(overviewTokens, "kpi.claim_used_blocks"), Math.max(0L, summary.usedClaimBlocks()));
        long stockroomUsedBlocks = parseLongToken(firstShopToken(overviewTokens, "kpi.stockroom_used_blocks"), 0L);

        Map<String, Object> shopInfo = new LinkedHashMap<>();
        shopInfo.put("shopId", shopId.toString());
        shopInfo.put("name", summary.name());
        shopInfo.put("type", summary.type());
        shopInfo.put("level", level);
        shopInfo.put("ownerId", ownerId == null ? "" : ownerId.toString());
        shopInfo.put("ownerName", ownerName);
        shopInfo.put("ownerOnline", ownerId != null && current.getPlayerList().getPlayer(ownerId) != null);
        shopInfo.put("role", ownerId == null ? "" : safeString(participantRoles.getOrDefault(ownerId, ShopService.SHOP_ROLE_OWNER)));
        shopInfo.put("revenueDollars", revenueDollars);
        shopInfo.put("nextTargetDollars", targetDollars);
        shopInfo.put("createdAtMillis", shopTag == null ? 0L : Math.max(0L, shopTag.getLong("created_millis")));
        shopInfo.put("settlementAccountId", (shopTag != null && shopTag.contains("settlement_account_id"))
                ? shopTag.getUUID("settlement_account_id").toString()
                : "");

        String checkoutTerminal = "";
        if (shopTag != null && shopTag.contains("checkout_terminal", Tag.TAG_COMPOUND)) {
            CompoundTag checkout = shopTag.getCompound("checkout_terminal");
            checkoutTerminal = safeString(checkout.getString("dim"))
                    + " "
                    + checkout.getInt("x") + ","
                    + checkout.getInt("y") + ","
                    + checkout.getInt("z");
        }
        shopInfo.put("checkoutTerminal", checkoutTerminal.trim());

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("level", level);
        kpis.put("revenueDollars", revenueDollars);
        kpis.put("nextTargetDollars", targetDollars);
        kpis.put("claimUsedBlocks", claimUsedBlocks);
        kpis.put("claimCapacityBlocks", claimCapacity);
        kpis.put("stockroomUsedBlocks", stockroomUsedBlocks);
        kpis.put("stockroomCapacityBlocks", stockroomCapacity);
        kpis.put("claimRegions", claimRows.size());
        kpis.put("stockroomRegions", stockroomRows.size());
        kpis.put("displayCapacity", displayCapacity);
        kpis.put("cashierCapacity", cashierCapacity);
        kpis.put("assignedPalletCapacity", palletCapacity);
        kpis.put("cashiers", parseIntToken(firstShopToken(overviewTokens, "kpi.cashiers"), 0));
        kpis.put("linkedCashiers", parseIntToken(firstShopToken(overviewTokens, "kpi.linked_cashiers"), 0));
        kpis.put("cashTxCount", parseLongToken(firstShopToken(overviewTokens, "kpi.cash_tx_count"), 0L));
        kpis.put("terminalTxCount", parseLongToken(firstShopToken(overviewTokens, "kpi.terminal_tx_count"), 0L));
        kpis.put("cashTotalCents", parseLongToken(firstShopToken(overviewTokens, "kpi.cash_total_cents"), 0L));
        kpis.put("terminalTotalCents", parseLongToken(firstShopToken(overviewTokens, "kpi.terminal_total_cents"), 0L));
        kpis.put("vaultTotalCents", parseLongToken(firstShopToken(overviewTokens, "kpi.vault_total_cents"), 0L));

        List<Map<String, Object>> permissionRows = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : participantRoles.entrySet()) {
            UUID playerId = entry.getKey();
            if (playerId == null) {
                continue;
            }
            String role = safeString(entry.getValue()).toUpperCase(Locale.ROOT);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("playerId", playerId.toString());
            row.put("name", resolvePlayerName(current, playerId, nameHints));
            row.put("role", role.isBlank() ? "STAFF" : role);
            row.put("owner", ownerId != null && ownerId.equals(playerId));
            row.put("online", current.getPlayerList().getPlayer(playerId) != null);
            permissionRows.add(row);
        }
        permissionRows.sort(
                Comparator.comparing((Map<String, Object> row) -> Boolean.TRUE.equals(row.get("owner"))).reversed()
                        .thenComparing(row -> String.valueOf(row.get("role")), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(row -> String.valueOf(row.get("name")), String.CASE_INSENSITIVE_ORDER)
        );

        List<Map<String, Object>> roadmapRows = new ArrayList<>();
        for (String[] row : shopTokenRows(roadmapTokens, "roadmap.node", 8)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("level", parseIntToken(row[0], 0));
            item.put("requiredRevenueDollars", parseLongToken(row[1], 0L));
            item.put("claimCapacityBlocks", parseLongToken(row[2], 0L));
            item.put("stockroomCapacityBlocks", parseLongToken(row[3], 0L));
            item.put("displayCapacity", parseIntToken(row[4], 0));
            item.put("cashierCapacity", parseIntToken(row[5], 0));
            item.put("palletCapacity", parseIntToken(row[6], 0));
            item.put("state", row[7]);
            roadmapRows.add(item);
        }

        Map<String, Object> hours = new LinkedHashMap<>();
        hours.put("openMinute", parseIntToken(firstShopToken(hoursTokens, "shop_hours.open_tick"), 540));
        hours.put("closeMinute", parseIntToken(firstShopToken(hoursTokens, "shop_hours.close_tick"), 1260));
        hours.put("openLabel", firstShopToken(hoursTokens, "shop_hours.open_label"));
        hours.put("closeLabel", firstShopToken(hoursTokens, "shop_hours.close_label"));
        hours.put("openNow", parseIntToken(firstShopToken(hoursTokens, "shop_hours.is_open"), 0) > 0);
        hours.put("untilChangeSeconds", parseLongToken(firstShopToken(hoursTokens, "shop_hours.until_change_seconds"), -1L));
        hours.put("closedDelivererStockroomAccess", parseIntToken(firstShopToken(hoursTokens, "shop_hours.closed_deliverer_stockroom_access"), 0) > 0);

        Map<String, Object> lighting = new LinkedHashMap<>();
        lighting.put("enabled", parseIntToken(firstShopToken(hoursTokens, "shop_lighting.enabled"), 0) > 0);
        lighting.put("mainMode", firstShopToken(hoursTokens, "shop_lighting.main_mode"));
        lighting.put("stockroomMode", firstShopToken(hoursTokens, "shop_lighting.stockroom_mode"));
        lighting.put("excludeStockroom", parseIntToken(firstShopToken(hoursTokens, "shop_lighting.exclude_stockroom"), 0) > 0);
        lighting.put("level", parseIntToken(firstShopToken(hoursTokens, "shop_lighting.level"), 15));
        lighting.put("managedBlocks", parseIntToken(firstShopToken(hoursTokens, "shop_lighting.managed_blocks"), 0));

        Map<String, Object> finance = new LinkedHashMap<>();
        finance.put("settlementAccountId", firstShopToken(financeTokens, "finance.settlement_account_id"));
        finance.put("checkoutAccountId", firstShopToken(financeTokens, "finance.checkout_account_id"));
        finance.put("checkoutTerminal", firstShopToken(financeTokens, "finance.checkout_terminal"));
        finance.put("cashTxCount", parseLongToken(firstShopToken(financeTokens, "finance.cash_tx_count"), 0L));
        finance.put("cashTotalCents", parseLongToken(firstShopToken(financeTokens, "finance.cash_total_cents"), 0L));
        finance.put("cashCustomers", parseIntToken(firstShopToken(financeTokens, "finance.cash_customers"), 0));
        finance.put("terminalTxCount", parseLongToken(firstShopToken(financeTokens, "finance.terminal_tx_count"), 0L));
        finance.put("terminalTotalCents", parseLongToken(firstShopToken(financeTokens, "finance.terminal_total_cents"), 0L));
        finance.put("terminalCustomers", parseIntToken(firstShopToken(financeTokens, "finance.terminal_customers"), 0));
        finance.put("vaultTotalCents", parseLongToken(firstShopToken(financeTokens, "finance.vault_total_cents"), 0L));

        List<Map<String, Object>> vaultRows = new ArrayList<>();
        String encodedVaultCounts = firstShopToken(vaultTokens, "vault.counts");
        if (!encodedVaultCounts.isBlank()) {
            for (String entry : encodedVaultCounts.split(",")) {
                String raw = safeString(entry);
                if (raw.isBlank() || !raw.contains(":")) {
                    continue;
                }
                String[] parts = raw.split(":", 2);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("denominationCents", parseLongToken(parts[0], 0L));
                row.put("count", parseIntToken(parts[1], 0));
                vaultRows.add(row);
            }
            vaultRows.sort((a, b) -> Long.compare(asLong(b.get("denominationCents")), asLong(a.get("denominationCents"))));
        }

        List<Map<String, Object>> orderRows = new ArrayList<>();
        for (String[] row : shopTokenRows(orderTokens, "shop_order", 12)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", parseIntToken(row[0], 0));
            item.put("orderId", row[1]);
            item.put("itemId", row[2]);
            item.put("itemName", row[3]);
            item.put("quantity", parseIntToken(row[4], 0));
            item.put("rewardCents", parseLongToken(row[5], 0L));
            item.put("status", row[6]);
            item.put("acceptedBy", row[7]);
            item.put("remainingSeconds", parseLongToken(row[8], 0L));
            item.put("timeoutMinutes", parseIntToken(row[9], 0));
            item.put("createdAtMillis", parseLongToken(row[10], 0L));
            item.put("boundPalletRef", row[11]);
            orderRows.add(item);
        }

        List<Map<String, Object>> orderPalletRows = new ArrayList<>();
        for (String[] row : shopTokenRows(orderTokens, "shop_order_pallet", 8)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", parseIntToken(row[0], 0));
            item.put("palletId", row[1]);
            item.put("dimensionId", row[2]);
            item.put("x", parseIntToken(row[3], 0));
            item.put("y", parseIntToken(row[4], 0));
            item.put("z", parseIntToken(row[5], 0));
            item.put("active", parseIntToken(row[6], 0) > 0);
            item.put("full", parseIntToken(row[7], 0) > 0);
            orderPalletRows.add(item);
        }

        List<Map<String, Object>> employeeRows = new ArrayList<>();
        for (String[] row : shopTokenRows(employeeTokens, "employee", 7)) {
            String[] coords = row[5].split(",", -1);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", parseIntToken(row[0], 0));
            item.put("employeeId", row[1]);
            item.put("cashierId", row[2]);
            item.put("label", row[3]);
            item.put("dimensionId", row[4]);
            item.put("x", coords.length > 0 ? parseIntToken(coords[0], 0) : 0);
            item.put("y", coords.length > 1 ? parseIntToken(coords[1], 0) : 0);
            item.put("z", coords.length > 2 ? parseIntToken(coords[2], 0) : 0);
            item.put("terminal", row[6]);
            employeeRows.add(item);
        }

        List<Map<String, Object>> shelfRows = new ArrayList<>();
        for (String[] row : shopTokenRows(shelfTokens, "shelf_card", 11)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", parseIntToken(row[0], 0));
            item.put("dimensionId", row[1]);
            item.put("x", parseIntToken(row[2], 0));
            item.put("y", parseIntToken(row[3], 0));
            item.put("z", parseIntToken(row[4], 0));
            item.put("shelfType", row[5]);
            item.put("configuredSlots", parseIntToken(row[6], 0));
            item.put("stockUnits", parseIntToken(row[7], 0));
            item.put("lowStockSlots", parseIntToken(row[8], 0));
            item.put("outOfStockSlots", parseIntToken(row[9], 0));
            item.put("target", row[10]);
            shelfRows.add(item);
        }

        List<Map<String, Object>> shelfItemRows = new ArrayList<>();
        for (String[] row : shopTokenRows(shelfTokens, "shelf_item", 13)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("shelfIndex", parseIntToken(row[0], 0));
            item.put("slot", parseIntToken(row[1], 0));
            item.put("itemId", row[2]);
            item.put("priceCents", parseLongToken(row[3], 0L));
            item.put("stock", parseIntToken(row[4], 0));
            item.put("restockable", parseIntToken(row[5], 0) > 0);
            item.put("slotTarget", row[6]);
            item.put("itemName", row[7]);
            item.put("minTarget", parseIntToken(row[8], 0));
            item.put("maxTarget", parseIntToken(row[9], 0));
            item.put("stockroomAvailable", parseIntToken(row[10], -1));
            item.put("lastSoldMillis", parseLongToken(row[11], 0L));
            item.put("velocityPerDay", parseDoubleToken(row[12], 0.0D));
            shelfItemRows.add(item);
        }

        List<Map<String, Object>> stockroomItemRows = new ArrayList<>();
        for (String[] row : shopTokenRows(stockroomTokens, "stockroom_item", 12)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", parseIntToken(row[0], 0));
            item.put("itemId", row[1]);
            item.put("itemName", row[2]);
            item.put("count", parseIntToken(row[3], 0));
            item.put("inventoryType", row[4]);
            item.put("dimensionId", row[5]);
            item.put("x", parseIntToken(row[6], 0));
            item.put("y", parseIntToken(row[7], 0));
            item.put("z", parseIntToken(row[8], 0));
            item.put("slot", parseIntToken(row[9], 0));
            item.put("totalSlots", parseIntToken(row[10], 0));
            item.put("locateTarget", row[11]);
            stockroomItemRows.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("shop", shopInfo);
        response.put("kpis", kpis);
        response.put("claims", claimRows);
        response.put("stockrooms", stockroomRows);
        response.put("permissions", permissionRows);
        response.put("roadmap", roadmapRows);
        response.put("hours", hours);
        response.put("lighting", lighting);
        response.put("finance", finance);
        response.put("vault", vaultRows);
        response.put("orders", orderRows);
        response.put("orderPallets", orderPalletRows);
        response.put("employees", employeeRows);
        response.put("shelves", shelfRows);
        response.put("shelfItems", shelfItemRows);
        response.put("stockroomItems", stockroomItemRows);
        return response;
    }

    private Map<String, Object> buildUsersPayload() {
        MinecraftServer current = requireServer();
        CentralBank centralBank = BankManager.getCentralBank(current);
        Map<UUID, String> nameHints = collectPlayerNameHints(current, centralBank);
        Set<UUID> knownIds = collectKnownPlayerIds(current, centralBank);
        List<Map<String, Object>> rows = new ArrayList<>();

        for (UUID playerId : knownIds) {
            if (playerId == null) {
                continue;
            }
            rows.add(buildUserSummaryRow(current, centralBank, playerId, nameHints));
        }

        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> Boolean.TRUE.equals(row.get("online"))).reversed()
                .thenComparing(row -> String.valueOf(row.get("name")), String.CASE_INSENSITIVE_ORDER));

        long online = rows.stream().filter(row -> Boolean.TRUE.equals(row.get("online"))).count();
        return Map.of(
                "ok", true,
                "rows", rows,
                "metrics", Map.of(
                        "knownUsers", rows.size(),
                        "onlineUsers", online
                )
        );
    }

    private Map<String, Object> buildUserDetailPayload(UUID playerId) {
        MinecraftServer current = requireServer();
        CentralBank centralBank = BankManager.getCentralBank(current);
        Map<UUID, String> nameHints = collectPlayerNameHints(current, centralBank);
        String playerName = resolvePlayerName(current, playerId, nameHints);
        boolean online = current.getPlayerList().getPlayer(playerId) != null;

        List<Map<String, Object>> accountRows = new ArrayList<>();
        Map<UUID, AccountHolder> accountMap = new LinkedHashMap<>();
        Set<String> ownedBanks = new LinkedHashSet<>();
        Set<String> delegatedBankRoles = new LinkedHashSet<>();
        Set<String> cofounderAt = new LinkedHashSet<>();
        Set<String> employeeAt = new LinkedHashSet<>();
        Map<String, String> shareholdings = new LinkedHashMap<>();

        BigDecimal totalBalance = BigDecimal.ZERO;
        int creditScoreTotal = 0;

        if (centralBank != null) {
            for (Bank bank : centralBank.getBanks().values()) {
                if (bank == null) {
                    continue;
                }
                if (playerId.equals(bank.getBankOwnerId())) {
                    ownedBanks.add(bank.getBankName() + " (" + bank.getBankId() + ")");
                }
                CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
                Map<UUID, String> roleMap = decodeUuidStringMap(metadata.getString("roles"));
                if (roleMap.containsKey(playerId)) {
                    delegatedBankRoles.add(bank.getBankName() + " -> " + roleMap.get(playerId));
                }
                if (decodeUuidList(metadata.getString("cofounders")).contains(playerId)) {
                    cofounderAt.add(bank.getBankName());
                }
                Map<UUID, String> employees = decodeEmployeeRoleMap(metadata.getString("employees"));
                if (employees.containsKey(playerId)) {
                    employeeAt.add(bank.getBankName() + " -> " + employees.get(playerId));
                }
                Map<UUID, BigDecimal> shares = decodeShareMap(metadata.getString("shares"));
                if (shares.containsKey(playerId)) {
                    shareholdings.put(bank.getBankName(), decimal2(shares.get(playerId)) + "%");
                }

                for (AccountHolder account : bank.getBankAccounts().values()) {
                    if (account == null || !playerId.equals(account.getPlayerUUID())) {
                        continue;
                    }
                    accountMap.put(account.getAccountUUID(), account);
                    totalBalance = totalBalance.add(account.getBalance());
                    creditScoreTotal += account.getCreditScore();
                    accountRows.add(buildAccountRow(bank, account));
                }
            }
        }

        accountRows.sort((a, b) -> Double.compare(asDouble(b.get("balance")), asDouble(a.get("balance"))));

        List<Map<String, Object>> cardRows = new ArrayList<>();
        int activeCards = 0;
        int blockedCards = 0;
        if (centralBank != null) {
            for (Map.Entry<UUID, CompoundTag> entry : centralBank.getIssuedCreditCards().entrySet()) {
                UUID cardId = entry.getKey();
                CompoundTag record = entry.getValue();
                if (cardId == null || record == null || !record.hasUUID("ownerPlayerId")) {
                    continue;
                }
                if (!playerId.equals(record.getUUID("ownerPlayerId"))) {
                    continue;
                }
                boolean blocked = record.getBoolean("blocked");
                if (blocked) {
                    blockedCards++;
                } else {
                    activeCards++;
                }
                // Use a mutable map here because Java's Map.of(...) overloads cap out at 10 key/value pairs.
                Map<String, Object> cardRow = new LinkedHashMap<>();
                cardRow.put("cardId", cardId.toString());
                cardRow.put("bankId", record.hasUUID("bankId") ? record.getUUID("bankId").toString() : "");
                cardRow.put("bankName", safeString(record.getString("bankName")));
                cardRow.put("accountId", record.hasUUID("accountId") ? record.getUUID("accountId").toString() : "");
                cardRow.put("status", blocked ? "BLOCKED" : safeStatus(record.getString("status")));
                cardRow.put("privateCard", record.getBoolean(CreditCardService.RECORD_PRIVATE_BANK_CARD));
                cardRow.put("holderName", safeString(record.getString("holderName")));
                cardRow.put("maskedNumber", CreditCardService.maskCardNumber(record.getString("cardNumber")));
                cardRow.put("issuedEpochMillis", record.getLong("issuedEpochMillis"));
                cardRow.put("expiryEpochMillis", record.getLong("expiryEpochMillis"));
                cardRow.put("replacement", record.getBoolean("replacement"));
                cardRows.add(cardRow);
            }
        }
        cardRows.sort((a, b) -> Long.compare(asLong(b.get("issuedEpochMillis")), asLong(a.get("issuedEpochMillis"))));

        List<Map<String, Object>> shopRows = new ArrayList<>();
        int ownedShopCount = 0;
        if (centralBank != null) {
            for (ShopService.ShopSummary summary : ShopService.listAllShopSummaries(centralBank)) {
                if (summary == null || summary.shopId() == null) {
                    continue;
                }
                Map<UUID, String> roles = ShopService.listShopParticipantRoles(centralBank, summary.shopId());
                String role = roles.get(playerId);
                if (role == null || role.isBlank()) {
                    continue;
                }
                if (ShopService.SHOP_ROLE_OWNER.equals(role)) {
                    ownedShopCount++;
                }
                shopRows.add(Map.of(
                        "shopId", summary.shopId().toString(),
                        "name", summary.name(),
                        "type", summary.type(),
                        "level", summary.level(),
                        "revenueDollars", summary.revenueDollars(),
                        "role", role
                ));
            }
        }
        shopRows.sort(Comparator.comparing(row -> String.valueOf(row.get("name")), String.CASE_INSENSITIVE_ORDER));

        // Deduplicate account-level transaction mirrors into one coherent feed.
        Set<UUID> ownAccountIds = new LinkedHashSet<>(accountMap.keySet());
        Map<UUID, UserTransaction> uniqueTransactions = new LinkedHashMap<>();
        for (AccountHolder account : accountMap.values()) {
            for (UserTransaction tx : account.getTransactions().values()) {
                if (tx != null) {
                    uniqueTransactions.put(tx.getTransactionUUID(), tx);
                }
            }
        }

        List<Map<String, Object>> transactionRows = uniqueTransactions.values().stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(120)
                .map(tx -> {
                    String direction = ownAccountIds.contains(tx.getSenderUUID()) && ownAccountIds.contains(tx.getReceiverUUID())
                            ? "INTERNAL"
                            : ownAccountIds.contains(tx.getSenderUUID()) ? "OUTGOING" : "INCOMING";
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("transactionId", tx.getTransactionUUID().toString());
                    row.put("timestamp", tx.getTimestamp().toString());
                    row.put("senderAccountId", tx.getSenderUUID() == null ? "" : tx.getSenderUUID().toString());
                    row.put("receiverAccountId", tx.getReceiverUUID() == null ? "" : tx.getReceiverUUID().toString());
                    row.put("amount", decimal2(tx.getAmount()));
                    row.put("description", tx.getTransactionDescription() == null ? "" : tx.getTransactionDescription());
                    row.put("direction", direction);
                    return row;
                })
                .toList();

        Map<String, Object> desktop = Map.of(
                "computerLabel", "Unavailable",
                "computerId", "",
                "maxStorageBytes", 0,
                "usedStorageBytes", 0,
                "pinSet", false,
                "poweredOn", false,
                "sessionUnlocked", false,
                "files", List.of(),
                "hiddenAppIds", List.of()
        );
        if (centralBank != null) {
            ensureVirtualDesktopContext(centralBank, playerId);
            OwnerPcDesktopDataPayload desktopData = BankOwnerPcService.buildDesktopData(centralBank, playerId);
            if (desktopData != null) {
                desktop = Map.of(
                        "computerLabel", desktopData.computerLabel(),
                        "computerId", desktopData.computerId(),
                        "maxStorageBytes", desktopData.maxStorageBytes(),
                        "usedStorageBytes", desktopData.usedStorageBytes(),
                        "pinSet", desktopData.pinSet(),
                        "poweredOn", desktopData.poweredOn(),
                        "sessionUnlocked", desktopData.sessionUnlocked(),
                        "files", desktopData.files(),
                        "hiddenAppIds", desktopData.hiddenAppIds()
                );
            }
        }

        int avgCreditScore = accountRows.isEmpty() ? 0 : Math.round((float) creditScoreTotal / (float) accountRows.size());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("profile", Map.of(
                "playerId", playerId.toString(),
                "name", playerName,
                "online", online
        ));
        response.put("summary", Map.of(
                "accounts", accountRows.size(),
                "totalBalance", decimal2(totalBalance),
                "activeCards", activeCards,
                "blockedCards", blockedCards,
                "ownedBanks", ownedBanks.size(),
                "delegatedBankRoles", delegatedBankRoles.size(),
                "shops", shopRows.size(),
                "ownedShops", ownedShopCount,
                "avgCreditScore", avgCreditScore,
                "transactions", uniqueTransactions.size()
        ));
        response.put("accounts", accountRows);
        response.put("creditCards", cardRows);
        response.put("bankFootprint", Map.of(
                "ownedBanks", new ArrayList<>(ownedBanks),
                "delegatedRoles", new ArrayList<>(delegatedBankRoles),
                "cofounderAt", new ArrayList<>(cofounderAt),
                "employeeAt", new ArrayList<>(employeeAt),
                "shareholdings", shareholdings
        ));
        response.put("shops", shopRows);
        response.put("transactions", transactionRows);
        response.put("desktop", desktop);
        return response;
    }

    private Map<String, Object> buildAccountDetailPayload(UUID accountId) {
        MinecraftServer current = requireServer();
        CentralBank centralBank = BankManager.getCentralBank(current);
        if (centralBank == null || accountId == null) {
            return Map.of("ok", false, "message", "Account data unavailable.");
        }

        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        Bank bank = findBankForAccount(centralBank, accountId);
        if (account == null || bank == null) {
            return Map.of("ok", false, "message", "Account not found.");
        }
        String bankName = safeString(bank.getBankName()).isBlank() ? "Unnamed Bank" : safeString(bank.getBankName());

        Map<UUID, String> nameHints = collectPlayerNameHints(current, centralBank);
        UUID ownerId = account.getPlayerUUID();
        String ownerName = resolvePlayerName(current, ownerId, nameHints);
        boolean ownerOnline = ownerId != null && current.getPlayerList().getPlayer(ownerId) != null;

        CompoundTag bankMeta = centralBank.getOrCreateBankMetadata(bank.getBankId());
        String bankStatus = normalizeBankStatus(bankMeta.getString("status"));
        BigDecimal bankDeposits = bank.getTotalDeposits().setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal bankReserve = bank.getDeclaredReserve().setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal bankRatio = bankDeposits.compareTo(BigDecimal.ZERO) > 0
                ? bankReserve.divide(bankDeposits, 6, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100))
                : BigDecimal.valueOf(100);

        long gameTime = 0L;
        if (current.getLevel(Level.OVERWORLD) != null) {
            gameTime = current.getLevel(Level.OVERWORLD).getGameTime();
        }

        List<Map<String, Object>> accessRoles = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : account.getAccessRoles().entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            UUID playerId = entry.getKey();
            String role = safeString(entry.getValue());
            accessRoles.add(Map.of(
                    "playerId", playerId.toString(),
                    "name", resolvePlayerName(current, playerId, nameHints),
                    "role", role.isBlank() ? "VIEW" : role.toUpperCase(Locale.ROOT),
                    "owner", playerId.equals(ownerId)
            ));
        }
        accessRoles.sort(Comparator.comparing(row -> String.valueOf(row.get("name")), String.CASE_INSENSITIVE_ORDER));

        List<Map<String, Object>> safeBoxRows = new ArrayList<>();
        for (Map.Entry<Integer, CompoundTag> entry : account.getSafeBoxSlots().entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            CompoundTag tag = entry.getValue();
            safeBoxRows.add(Map.of(
                    "slot", entry.getKey(),
                    "itemId", safeString(tag.getString("id")),
                    "count", Math.max(1, tag.getInt("Count"))
            ));
        }
        safeBoxRows.sort(Comparator.comparingInt(row -> (Integer) row.get("slot")));

        List<Map<String, Object>> loanRows = new ArrayList<>();
        for (AccountLoan loan : account.getActiveLoans().values()) {
            if (loan == null) {
                continue;
            }
            Bank lenderBank = loan.getLenderBankId() == null ? null : centralBank.getBank(loan.getLenderBankId());
            String lenderName = lenderBank == null ? "Unknown Bank" : safeString(lenderBank.getBankName());
            Map<String, Object> loanRow = new LinkedHashMap<>();
            loanRow.put("loanId", loan.getLoanId().toString());
            loanRow.put("lenderBankId", loan.getLenderBankId() == null ? "" : loan.getLenderBankId().toString());
            loanRow.put("lenderName", lenderName.isBlank() ? "Unknown Bank" : lenderName);
            loanRow.put("principal", decimal2(loan.getPrincipal()));
            loanRow.put("remainingBalance", decimal2(loan.getRemainingBalance()));
            loanRow.put("annualInterestRate", round2(loan.getAnnualInterestRate()));
            loanRow.put("periodicPayment", decimal2(loan.getPeriodicPayment()));
            loanRow.put("totalPayments", loan.getTotalPayments());
            loanRow.put("paymentsMade", loan.getPaymentsMade());
            loanRow.put("defaulted", loan.isDefaulted());
            loanRow.put("createdAtGameTime", loan.getCreatedAtGameTime());
            loanRow.put("nextDueGameTime", loan.getNextDueGameTime());
            loanRow.put("paymentIntervalTicks", loan.getPaymentIntervalTicks());
            loanRows.add(loanRow);
        }
        loanRows.sort((a, b) -> Double.compare(asDouble(b.get("remainingBalance")), asDouble(a.get("remainingBalance"))));

        List<Map<String, Object>> cardRows = new ArrayList<>();
        for (Map.Entry<UUID, CompoundTag> entry : centralBank.getIssuedCreditCards().entrySet()) {
            UUID cardId = entry.getKey();
            CompoundTag record = entry.getValue();
            if (cardId == null || record == null || !record.hasUUID("accountId")) {
                continue;
            }
            if (!accountId.equals(record.getUUID("accountId"))) {
                continue;
            }
            boolean blocked = record.getBoolean("blocked");
            cardRows.add(Map.of(
                    "cardId", cardId.toString(),
                    "status", blocked ? "BLOCKED" : safeStatus(record.getString("status")),
                    "privateCard", record.getBoolean(CreditCardService.RECORD_PRIVATE_BANK_CARD),
                    "holderName", safeString(record.getString("holderName")),
                    "maskedNumber", CreditCardService.maskCardNumber(record.getString("cardNumber")),
                    "issuedEpochMillis", record.getLong("issuedEpochMillis"),
                    "expiryEpochMillis", record.getLong("expiryEpochMillis"),
                    "replacement", record.getBoolean("replacement")
            ));
        }
        cardRows.sort((a, b) -> Long.compare(asLong(b.get("issuedEpochMillis")), asLong(a.get("issuedEpochMillis"))));

        List<Map<String, Object>> txRows = account.getTransactions().values().stream()
                .filter(Objects::nonNull)
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(240)
                .map(tx -> {
                    String direction;
                    if (accountId.equals(tx.getSenderUUID()) && accountId.equals(tx.getReceiverUUID())) {
                        direction = "INTERNAL";
                    } else if (accountId.equals(tx.getSenderUUID())) {
                        direction = "OUTGOING";
                    } else if (accountId.equals(tx.getReceiverUUID())) {
                        direction = "INCOMING";
                    } else {
                        direction = "RELATED";
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("transactionId", tx.getTransactionUUID().toString());
                    row.put("timestamp", tx.getTimestamp().toString());
                    row.put("senderAccountId", tx.getSenderUUID() == null ? "" : tx.getSenderUUID().toString());
                    row.put("receiverAccountId", tx.getReceiverUUID() == null ? "" : tx.getReceiverUUID().toString());
                    row.put("amount", decimal2(tx.getAmount()));
                    row.put("description", tx.getTransactionDescription() == null ? "" : tx.getTransactionDescription());
                    row.put("direction", direction);
                    return row;
                })
                .toList();

        BigDecimal configuredLimit = account.getConfiguredWithdrawalLimit();
        BigDecimal dailyLimit = account.getConfiguredDailyWithdrawalLimit();
        BigDecimal dailyWithdrawn = account.getDailyWithdrawnAmount();
        BigDecimal dailyRemaining = account.getRemainingDailyWithdrawalLimit();
        BigDecimal effectiveLimit = account.getEffectiveWithdrawalLimit(gameTime);
        BigDecimal tempLimit = account.getTemporaryWithdrawalLimitIfActive(gameTime);
        long tempLimitExpiryGameTime = account.getTemporaryWithdrawalLimitExpiresAtGameTime(gameTime);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("owner", Map.of(
                "playerId", ownerId == null ? "" : ownerId.toString(),
                "name", ownerName,
                "online", ownerOnline
        ));
        Map<String, Object> bankInfo = new LinkedHashMap<>();
        bankInfo.put("bankId", bank.getBankId().toString());
        bankInfo.put("name", bankName);
        bankInfo.put("ownerId", bank.getBankOwnerId() == null ? "" : bank.getBankOwnerId().toString());
        bankInfo.put("ownerName", resolvePlayerName(current, bank.getBankOwnerId(), nameHints));
        bankInfo.put("status", bankStatus);
        bankInfo.put("interestRate", round2(bank.getInterestRate()));
        bankInfo.put("deposits", decimal2(bankDeposits));
        bankInfo.put("reserve", decimal2(bankReserve));
        bankInfo.put("reserveRatioPct", round2(bankRatio.doubleValue()));
        bankInfo.put("minimumRequiredReserve", decimal2(bank.getMinimumRequiredReserve()));
        bankInfo.put("maxLendable", decimal2(bank.getMaxLendableAmount()));
        bankInfo.put("outstandingLoans", decimal2(bank.getOutstandingLoanBalance()));
        response.put("bank", bankInfo);

        Map<String, Object> accountInfo = new LinkedHashMap<>();
        accountInfo.put("accountId", account.getAccountUUID().toString());
        accountInfo.put("bankId", bank.getBankId().toString());
        accountInfo.put("bankName", bankName);
        accountInfo.put("ownerPlayerId", ownerId == null ? "" : ownerId.toString());
        accountInfo.put("ownerName", ownerName);
        accountInfo.put("createdAt", account.getDateOfCreation() == null ? "" : account.getDateOfCreation().toString());
        accountInfo.put("type", account.getAccountType() == null ? AccountTypes.CheckingAccount.label : account.getAccountType().label);
        accountInfo.put("balance", decimal2(account.getBalance()));
        accountInfo.put("primary", account.isPrimaryAccount());
        accountInfo.put("frozen", account.isFrozen());
        accountInfo.put("frozenReason", account.getFrozenReason());
        accountInfo.put("creditScore", account.getCreditScore());
        accountInfo.put("defaulted", account.isDefaulted());
        accountInfo.put("activeLoans", account.getActiveLoans().size());
        accountInfo.put("transactions", account.getTransactions().size());
        accountInfo.put("pinSet", account.hasPin());
        accountInfo.put("accessType", account.getAccountAccessType());
        accountInfo.put("businessLabel", account.getBusinessLabel());
        response.put("account", accountInfo);
        response.put("limits", Map.of(
                "configuredWithdrawalLimit", decimal2(configuredLimit),
                "effectiveWithdrawalLimit", decimal2(effectiveLimit),
                "dailyWithdrawalLimit", decimal2(dailyLimit),
                "dailyWithdrawn", decimal2(dailyWithdrawn),
                "dailyRemaining", decimal2(dailyRemaining),
                "dailyResetEpochMillis", account.getDailyWithdrawalResetEpochMillis(),
                "tempWithdrawalLimit", tempLimit == null ? "" : decimal2(tempLimit),
                "tempWithdrawalLimitExpiresAtGameTime", tempLimitExpiryGameTime
        ));
        response.put("certificate", Map.of(
                "tier", account.getCertificateTier(),
                "locked", account.isCertificateLocked(gameTime),
                "maturityGameTime", account.getCertificateMaturityGameTime(),
                "maturitySettled", account.isCertificateMaturitySettled(),
                "rate", round2(account.getCertificateRate()),
                "lastVariableRate", round2(account.getLastVariableRate())
        ));
        response.put("safeBox", Map.of(
                "usedSlots", account.getSafeBoxSlots().size(),
                "totalSlots", account.getSafeBoxSlotCount(),
                "rows", safeBoxRows
        ));
        response.put("roles", accessRoles);
        response.put("loans", loanRows);
        response.put("cards", cardRows);
        response.put("transactions", txRows);
        return response;
    }

    private Map<String, Object> buildBankDetailPayload(UUID bankId) {
        MinecraftServer current = requireServer();
        CentralBank centralBank = BankManager.getCentralBank(current);
        if (centralBank == null || bankId == null) {
            return Map.of("ok", false, "message", "Bank data unavailable.");
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return Map.of("ok", false, "message", "Bank not found.");
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        Map<UUID, String> nameHints = collectPlayerNameHints(current, centralBank);

        String status = normalizeBankStatus(metadata.getString("status"));
        String ownershipModel = safeString(metadata.getString("ownershipModel"));
        if (ownershipModel.isBlank()) {
            ownershipModel = "SOLE";
        }
        String color = safeString(metadata.getString("color"));
        if (color.isBlank()) {
            color = "#55AAFF";
        }
        String motto = safeString(metadata.getString("motto"));

        BigDecimal deposits = bank.getTotalDeposits().setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal reserve = bank.getDeclaredReserve().setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal minReserve = deposits.multiply(BigDecimal.valueOf(Config.BANK_MIN_RESERVE_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal reserveRatio = deposits.compareTo(BigDecimal.ZERO) > 0
                ? reserve.divide(deposits, 6, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100))
                : BigDecimal.valueOf(100);
        BigDecimal dailyCap = computeDailyCapForBank(bank, metadata);
        BigDecimal dailyUsed = decimalOrZero(metadata, "dailyWithdrawn").setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal dailyRemaining = dailyCap.subtract(dailyUsed).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal singleLimit = metadata.contains("limitSingle")
                ? decimalOrZero(metadata, "limitSingle")
                : BigDecimal.valueOf(Config.GLOBAL_MAX_SINGLE_TRANSACTION.get());
        BigDecimal dailyPlayerLimit = metadata.contains("limitDailyPlayer")
                ? decimalOrZero(metadata, "limitDailyPlayer")
                : BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_PLAYER_VOLUME.get());
        BigDecimal dailyBankLimit = metadata.contains("limitDailyBank")
                ? decimalOrZero(metadata, "limitDailyBank")
                : BigDecimal.valueOf(Config.GLOBAL_MAX_DAILY_BANK_VOLUME.get());
        BigDecimal tellerLimit = metadata.contains("limitTeller")
                ? decimalOrZero(metadata, "limitTeller")
                : BigDecimal.valueOf(250000L);
        if (tellerLimit.compareTo(BigDecimal.ZERO) <= 0) {
            tellerLimit = BigDecimal.valueOf(250000L);
        }

        List<Map<String, Object>> roleRows = new ArrayList<>();
        decodeUuidStringMap(metadata.getString("roles")).forEach((playerId, role) -> roleRows.add(Map.of(
                "playerId", playerId.toString(),
                "name", resolvePlayerName(current, playerId, nameHints),
                "role", safeString(role).isBlank() ? "VIEW" : safeString(role).toUpperCase(Locale.ROOT)
        )));
        roleRows.sort(Comparator.comparing(row -> String.valueOf(row.get("name")), String.CASE_INSENSITIVE_ORDER));

        List<Map<String, Object>> shareRows = new ArrayList<>();
        decodeShareMap(metadata.getString("shares")).forEach((playerId, sharePct) -> shareRows.add(Map.of(
                "playerId", playerId.toString(),
                "name", resolvePlayerName(current, playerId, nameHints),
                "percent", round2(sharePct.doubleValue())
        )));
        shareRows.sort((a, b) -> Double.compare(asDouble(b.get("percent")), asDouble(a.get("percent"))));

        List<Map<String, Object>> cofounderRows = new ArrayList<>();
        for (UUID cofounderId : decodeUuidList(metadata.getString("cofounders"))) {
            if (cofounderId == null) {
                continue;
            }
            cofounderRows.add(Map.of(
                    "playerId", cofounderId.toString(),
                    "name", resolvePlayerName(current, cofounderId, nameHints)
            ));
        }
        cofounderRows.sort(Comparator.comparing(row -> String.valueOf(row.get("name")), String.CASE_INSENSITIVE_ORDER));

        List<Map<String, Object>> employeeRows = new ArrayList<>();
        decodeEmployeeSpecMap(metadata.getString("employees")).forEach((playerId, spec) -> {
            if (playerId == null || spec == null) {
                return;
            }
            employeeRows.add(Map.of(
                    "playerId", playerId.toString(),
                    "name", resolvePlayerName(current, playerId, nameHints),
                    "role", safeString(spec.role()).toUpperCase(Locale.ROOT),
                    "salary", decimal2(spec.salary())
            ));
        });
        employeeRows.sort(Comparator.comparing(row -> String.valueOf(row.get("name")), String.CASE_INSENSITIVE_ORDER));

        List<Map<String, Object>> loanProductRows = new ArrayList<>();
        for (LoanProductSpec product : decodeLoanProducts(metadata.getString("loanProducts"))) {
            if (product == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", product.name());
            row.put("maxAmount", decimal2(product.maxAmount()));
            row.put("annualRate", round2(product.interestRate()));
            row.put("durationTicks", product.durationTicks());
            loanProductRows.add(row);
        }
        loanProductRows.sort(Comparator.comparing(row -> String.valueOf(row.get("name")), String.CASE_INSENSITIVE_ORDER));

        List<Map<String, Object>> accountRows = new ArrayList<>();
        List<Map<String, Object>> certificateRows = new ArrayList<>();
        for (AccountHolder account : bank.getBankAccounts().values()) {
            if (account == null) {
                continue;
            }
            UUID ownerId = account.getPlayerUUID();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("accountId", account.getAccountUUID().toString());
            row.put("ownerId", ownerId == null ? "" : ownerId.toString());
            row.put("ownerName", resolvePlayerName(current, ownerId, nameHints));
            row.put("type", account.getAccountType() == null ? AccountTypes.CheckingAccount.label : account.getAccountType().label);
            row.put("balance", decimal2(account.getBalance()));
            row.put("primary", account.isPrimaryAccount());
            row.put("frozen", account.isFrozen());
            row.put("defaulted", account.isDefaulted());
            row.put("creditScore", account.getCreditScore());
            row.put("transactions", account.getTransactions().size());
            row.put("activeLoans", account.getActiveLoans().size());
            accountRows.add(row);

            if (account.getAccountType() == AccountTypes.CertificateAccount) {
                Map<String, Object> certRow = new LinkedHashMap<>();
                certRow.put("accountId", account.getAccountUUID().toString());
                certRow.put("ownerId", ownerId == null ? "" : ownerId.toString());
                certRow.put("ownerName", resolvePlayerName(current, ownerId, nameHints));
                certRow.put("tier", account.getCertificateTier());
                certRow.put("rate", round2(account.getCertificateRate()));
                certRow.put("maturityGameTime", account.getCertificateMaturityGameTime());
                certRow.put("maturitySettled", account.isCertificateMaturitySettled());
                certificateRows.add(certRow);
            }
        }
        accountRows.sort((a, b) -> Double.compare(asDouble(b.get("balance")), asDouble(a.get("balance"))));
        certificateRows.sort((a, b) -> Long.compare(asLong(a.get("maturityGameTime")), asLong(b.get("maturityGameTime"))));

        List<Map<String, Object>> offerRows = new ArrayList<>();
        for (Map.Entry<UUID, CompoundTag> entry : centralBank.getInterbankOffers().entrySet()) {
            UUID fallbackId = entry.getKey();
            CompoundTag offer = entry.getValue();
            if (offer == null) {
                continue;
            }
            UUID lenderBankId = offer.hasUUID("lenderBankId") ? offer.getUUID("lenderBankId") : null;
            UUID acceptedByBankId = offer.hasUUID("acceptedByBankId") ? offer.getUUID("acceptedByBankId") : null;
            if (!bankId.equals(lenderBankId) && !bankId.equals(acceptedByBankId)) {
                continue;
            }
            UUID offerId = offer.hasUUID("id") ? offer.getUUID("id") : fallbackId;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("offerId", offerId == null ? "" : offerId.toString());
            row.put("lenderBankId", lenderBankId == null ? "" : lenderBankId.toString());
            row.put("lenderBankName", resolveBankName(centralBank, lenderBankId));
            row.put("acceptedByBankId", acceptedByBankId == null ? "" : acceptedByBankId.toString());
            row.put("acceptedByBankName", resolveBankName(centralBank, acceptedByBankId));
            row.put("lenderIsCurrentBank", bankId.equals(lenderBankId));
            row.put("amount", decimal2(decimalOrZero(offer, "amount")));
            row.put("annualRate", round2(offer.contains("annualRate") ? offer.getDouble("annualRate") : 0.0D));
            row.put("termTicks", offer.contains("termTicks") ? offer.getLong("termTicks") : 0L);
            row.put("createdTick", offer.contains("createdTick") ? offer.getLong("createdTick") : 0L);
            row.put("expiryTick", offer.contains("expiryTick") ? offer.getLong("expiryTick") : 0L);
            row.put("status", safeStatus(offer.getString("status")));
            offerRows.add(row);
        }
        offerRows.sort((a, b) -> Long.compare(asLong(b.get("createdTick")), asLong(a.get("createdTick"))));

        List<Map<String, Object>> loanRows = new ArrayList<>();
        for (Map.Entry<UUID, CompoundTag> entry : centralBank.getInterbankLoans().entrySet()) {
            UUID fallbackId = entry.getKey();
            CompoundTag loan = entry.getValue();
            if (loan == null) {
                continue;
            }
            UUID lenderBankId = loan.hasUUID("lenderBankId") ? loan.getUUID("lenderBankId") : null;
            UUID borrowerBankId = loan.hasUUID("borrowerBankId") ? loan.getUUID("borrowerBankId") : null;
            UUID directBankId = loan.hasUUID("bankId") ? loan.getUUID("bankId") : null;
            if (!bankId.equals(lenderBankId) && !bankId.equals(borrowerBankId) && !bankId.equals(directBankId)) {
                continue;
            }
            UUID loanId = loan.hasUUID("id") ? loan.getUUID("id") : fallbackId;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("loanId", loanId == null ? "" : loanId.toString());
            row.put("type", safeString(loan.getString("type")).isBlank() ? "UNKNOWN" : safeString(loan.getString("type")).toUpperCase(Locale.ROOT));
            row.put("lenderBankId", lenderBankId == null ? "" : lenderBankId.toString());
            row.put("lenderBankName", resolveBankName(centralBank, lenderBankId));
            row.put("borrowerBankId", borrowerBankId == null ? "" : borrowerBankId.toString());
            row.put("borrowerBankName", resolveBankName(centralBank, borrowerBankId));
            row.put("principal", decimal2(decimalOrZero(loan, "principal")));
            row.put("remaining", decimal2(decimalOrZero(loan, "remaining")));
            row.put("annualRate", round2(loan.contains("annualRate") ? loan.getDouble("annualRate") : 0.0D));
            row.put("status", safeStatus(loan.getString("status")));
            row.put("createdTick", loan.contains("createdTick") ? loan.getLong("createdTick") : 0L);
            row.put("maturityTick", loan.contains("maturityTick") ? loan.getLong("maturityTick") : 0L);
            row.put("paymentIntervalTicks", loan.contains("paymentIntervalTicks") ? loan.getLong("paymentIntervalTicks") : 0L);
            row.put("paymentsRemaining", loan.contains("paymentsRemaining") ? loan.getInt("paymentsRemaining") : 0);
            row.put("nextDueTick", loan.contains("nextDueTick") ? loan.getLong("nextDueTick") : 0L);
            loanRows.add(row);
        }
        loanRows.sort((a, b) -> Long.compare(asLong(b.get("createdTick")), asLong(a.get("createdTick"))));

        Map<String, Object> bankInfo = new LinkedHashMap<>();
        bankInfo.put("bankId", bankId.toString());
        bankInfo.put("name", safeString(bank.getBankName()).isBlank() ? "Unnamed Bank" : safeString(bank.getBankName()));
        bankInfo.put("ownerId", bank.getBankOwnerId() == null ? "" : bank.getBankOwnerId().toString());
        bankInfo.put("ownerName", resolvePlayerName(current, bank.getBankOwnerId(), nameHints));
        bankInfo.put("status", status);
        bankInfo.put("ownershipModel", ownershipModel);
        bankInfo.put("color", color);
        bankInfo.put("motto", motto);
        bankInfo.put("interestRate", round2(bank.getInterestRate()));
        bankInfo.put("federalFundsRate", round2(centralBank.getFederalFundsRate()));
        bankInfo.put("deposits", decimal2(deposits));
        bankInfo.put("reserve", decimal2(reserve));
        bankInfo.put("reserveRatioPct", round2(reserveRatio.doubleValue()));
        bankInfo.put("minReserve", decimal2(minReserve));
        bankInfo.put("dailyCap", decimal2(dailyCap));
        bankInfo.put("dailyUsed", decimal2(dailyUsed));
        bankInfo.put("dailyRemaining", decimal2(dailyRemaining));
        bankInfo.put("cardIssueFee", decimal2(CreditCardService.getIssueFee(centralBank, bankId)));
        bankInfo.put("cardReplacementFee", decimal2(CreditCardService.getReplacementFee(centralBank, bankId)));
        bankInfo.put("accountsCount", bank.getBankAccounts().size());
        bankInfo.put("maxLendable", decimal2(bank.getMaxLendableAmount()));
        bankInfo.put("outstandingLoans", decimal2(bank.getOutstandingLoanBalance()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("bank", bankInfo);
        response.put("limits", Map.of(
                "singleLimit", decimal2(singleLimit),
                "dailyPlayerLimit", decimal2(dailyPlayerLimit),
                "dailyBankLimit", decimal2(dailyBankLimit),
                "tellerLimit", decimal2(tellerLimit)
        ));
        response.put("roles", roleRows);
        response.put("shares", shareRows);
        response.put("cofounders", cofounderRows);
        response.put("employees", employeeRows);
        response.put("loanProducts", loanProductRows);
        response.put("accounts", accountRows);
        response.put("certificates", certificateRows);
        response.put("interbankOffers", offerRows);
        response.put("interbankLoans", loanRows);
        return response;
    }

    private EconomySnapshot collectEconomySnapshot(MinecraftServer current, CentralBank centralBank) {
        int onlinePlayers = current != null && current.getPlayerList() != null
                ? current.getPlayerList().getPlayers().size()
                : 0;
        if (centralBank == null) {
            return new EconomySnapshot(
                    System.currentTimeMillis(),
                    0,
                    0,
                    0,
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    onlinePlayers,
                    0,
                    0L,
                    0,
                    0,
                    0,
                    new LinkedHashMap<>(),
                    new LinkedHashMap<>()
            );
        }

        int banksTotal = 0;
        int activeBanks = 0;
        int accountsTotal = 0;
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalReserves = BigDecimal.ZERO;
        Map<String, Integer> bankStatusCounts = new LinkedHashMap<>();

        for (Bank bank : centralBank.getBanks().values()) {
            if (bank == null) {
                continue;
            }
            banksTotal++;
            accountsTotal += bank.getBankAccounts().size();
            totalDeposits = totalDeposits.add(bank.getTotalDeposits());
            totalReserves = totalReserves.add(bank.getDeclaredReserve());

            String status = normalizeBankStatus(centralBank.getOrCreateBankMetadata(bank.getBankId()).getString("status"));
            bankStatusCounts.merge(status, 1, Integer::sum);
            if ("ACTIVE".equals(status)) {
                activeBanks++;
            }
        }

        List<ShopService.ShopSummary> shops = ShopService.listAllShopSummaries(centralBank);
        Map<String, Integer> shopTypeCounts = new LinkedHashMap<>();
        long shopsRevenue = 0L;
        for (ShopService.ShopSummary shop : shops) {
            if (shop == null) {
                continue;
            }
            shopTypeCounts.merge(shop.type(), 1, Integer::sum);
            shopsRevenue += Math.max(0L, shop.revenueDollars());
        }

        int issuedCards = 0;
        int activeCards = 0;
        int blockedCards = 0;
        for (CompoundTag record : centralBank.getIssuedCreditCards().values()) {
            if (record == null) {
                continue;
            }
            issuedCards++;
            if (record.getBoolean("blocked")) {
                blockedCards++;
            } else {
                activeCards++;
            }
        }

        return new EconomySnapshot(
                System.currentTimeMillis(),
                banksTotal,
                activeBanks,
                Math.max(0, banksTotal - activeBanks),
                accountsTotal,
                totalDeposits.setScale(2, RoundingMode.HALF_EVEN),
                totalReserves.setScale(2, RoundingMode.HALF_EVEN),
                totalDeposits.setScale(2, RoundingMode.HALF_EVEN),
                onlinePlayers,
                shops.size(),
                Math.max(0L, shopsRevenue),
                issuedCards,
                activeCards,
                blockedCards,
                bankStatusCounts,
                shopTypeCounts
        );
    }

    private List<Map<String, Object>> buildBankRows(MinecraftServer current, CentralBank centralBank) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (centralBank == null) {
            return rows;
        }

        for (Bank bank : centralBank.getBanks().values()) {
            if (bank == null) {
                continue;
            }
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            String status = normalizeBankStatus(metadata.getString("status"));
            BigDecimal deposits = bank.getTotalDeposits().setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal reserve = bank.getDeclaredReserve().setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal ratio = deposits.compareTo(BigDecimal.ZERO) > 0
                    ? reserve.divide(deposits, 6, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.valueOf(100);

            long interbankOffers = centralBank.getInterbankOffers().values().stream()
                    .filter(Objects::nonNull)
                    .filter(tag -> tag.hasUUID("lenderBankId") && bank.getBankId().equals(tag.getUUID("lenderBankId"))
                            || tag.hasUUID("acceptedByBankId") && bank.getBankId().equals(tag.getUUID("acceptedByBankId")))
                    .count();
            long interbankLoans = centralBank.getInterbankLoans().values().stream()
                    .filter(Objects::nonNull)
                    .filter(tag -> tag.hasUUID("lenderBankId") && bank.getBankId().equals(tag.getUUID("lenderBankId"))
                            || tag.hasUUID("borrowerBankId") && bank.getBankId().equals(tag.getUUID("borrowerBankId"))
                            || tag.hasUUID("bankId") && bank.getBankId().equals(tag.getUUID("bankId")))
                    .count();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bankId", bank.getBankId().toString());
            row.put("name", bank.getBankName());
            row.put("ownerId", bank.getBankOwnerId() == null ? "" : bank.getBankOwnerId().toString());
            row.put("ownerName", resolvePlayerName(current, bank.getBankOwnerId()));
            row.put("status", status);
            row.put("accounts", bank.getBankAccounts().size());
            row.put("deposits", round2(deposits.doubleValue()));
            row.put("reserve", round2(reserve.doubleValue()));
            row.put("reserveRatioPct", round2(ratio.doubleValue()));
            row.put("outstandingLoans", round2(bank.getOutstandingLoanBalance().doubleValue()));
            row.put("interbankOffers", interbankOffers);
            row.put("interbankLoans", interbankLoans);
            rows.add(row);
        }

        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("name")), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private List<Map<String, Object>> buildShopRows(MinecraftServer current, CentralBank centralBank) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (centralBank == null) {
            return rows;
        }

        for (ShopService.ShopSummary summary : ShopService.listAllShopSummaries(centralBank)) {
            if (summary == null || summary.shopId() == null) {
                continue;
            }
            UUID ownerId = ShopService.resolveShopOwnerId(centralBank, summary.shopId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shopId", summary.shopId().toString());
            row.put("name", summary.name());
            row.put("type", summary.type());
            row.put("level", summary.level());
            row.put("ownerId", ownerId == null ? "" : ownerId.toString());
            row.put("ownerName", resolvePlayerName(current, ownerId));
            row.put("revenueDollars", summary.revenueDollars());
            row.put("nextTargetDollars", summary.nextTargetDollars());
            row.put("usedClaimBlocks", summary.usedClaimBlocks());
            row.put("claimCapacityBlocks", summary.claimCapacityBlocks());
            row.put("claimRegions", summary.claimRegions());
            row.put("stockroomRegions", summary.stockroomRegions());
            rows.add(row);
        }

        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("name")), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private CompoundTag findShopTagById(CentralBank centralBank, UUID shopId) {
        if (centralBank == null || shopId == null) {
            return null;
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        if (!centralMeta.contains("ubs_shop_root", Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag root = centralMeta.getCompound("ubs_shop_root");
        ListTag shops = root.getList("shops", Tag.TAG_COMPOUND);
        for (Tag tag : shops) {
            if (!(tag instanceof CompoundTag shop) || !shop.contains("id")) {
                continue;
            }
            try {
                if (shopId.equals(shop.getUUID("id"))) {
                    return shop.copy();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private List<Map<String, Object>> extractShopRegionRows(ListTag regions) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (regions == null || regions.isEmpty()) {
            return rows;
        }
        int index = 1;
        for (Tag tag : regions) {
            if (!(tag instanceof CompoundTag region)) {
                continue;
            }
            int minX = region.getInt("min_x");
            int minY = region.getInt("min_y");
            int minZ = region.getInt("min_z");
            int maxX = region.getInt("max_x");
            int maxY = region.getInt("max_y");
            int maxZ = region.getInt("max_z");
            long width = Math.max(0L, (long) maxX - (long) minX + 1L);
            long height = Math.max(0L, (long) maxY - (long) minY + 1L);
            long depth = Math.max(0L, (long) maxZ - (long) minZ + 1L);
            long volume = Math.max(0L, width * height * depth);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", index++);
            row.put("dimensionId", safeString(region.getString("dim")));
            row.put("minX", minX);
            row.put("minY", minY);
            row.put("minZ", minZ);
            row.put("maxX", maxX);
            row.put("maxY", maxY);
            row.put("maxZ", maxZ);
            row.put("volumeBlocks", volume);
            rows.add(row);
        }
        return rows;
    }

    private Map<String, List<String>> parseShopReportTokens(String rawReport) {
        Map<String, List<String>> tokens = new LinkedHashMap<>();
        if (rawReport == null || rawReport.isBlank()) {
            return tokens;
        }
        String[] lines = rawReport.split("\\R");
        for (String line : lines) {
            String raw = safeString(line);
            if (!raw.startsWith("@")) {
                continue;
            }
            int idx = raw.indexOf('=');
            if (idx <= 1 || idx >= raw.length() - 1) {
                continue;
            }
            String key = safeString(raw.substring(1, idx));
            String value = safeString(raw.substring(idx + 1));
            if (key.isBlank()) {
                continue;
            }
            tokens.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return tokens;
    }

    private String firstShopToken(Map<String, List<String>> tokens, String key) {
        if (tokens == null || key == null || key.isBlank()) {
            return "";
        }
        List<String> values = tokens.get(key);
        if (values == null || values.isEmpty()) {
            return "";
        }
        return safeString(values.get(0));
    }

    private List<String[]> shopTokenRows(Map<String, List<String>> tokens, String key, int minColumns) {
        List<String[]> rows = new ArrayList<>();
        if (tokens == null || key == null || key.isBlank()) {
            return rows;
        }
        List<String> values = tokens.get(key);
        if (values == null || values.isEmpty()) {
            return rows;
        }
        for (String value : values) {
            String[] cols = value == null ? new String[0] : value.split("\\|", -1);
            if (cols.length < minColumns) {
                continue;
            }
            rows.add(cols);
        }
        return rows;
    }

    private long parseLongToken(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int parseIntToken(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private double parseDoubleToken(String raw, double fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Map<String, Object> buildUserSummaryRow(MinecraftServer current,
                                                    CentralBank centralBank,
                                                    UUID playerId,
                                                    Map<UUID, String> nameHints) {
        boolean online = current.getPlayerList().getPlayer(playerId) != null;
        String name = resolvePlayerName(current, playerId, nameHints);

        int accounts = 0;
        int ownedBanks = 0;
        int delegatedBankRoles = 0;
        int shops = 0;
        int ownedShops = 0;
        int cards = 0;
        int activeCards = 0;
        int blockedCards = 0;
        int txCount = 0;
        int creditScoreTotal = 0;
        BigDecimal totalBalance = BigDecimal.ZERO;

        if (centralBank != null) {
            for (Bank bank : centralBank.getBanks().values()) {
                if (bank == null) {
                    continue;
                }
                if (playerId.equals(bank.getBankOwnerId())) {
                    ownedBanks++;
                }
                CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
                if (decodeUuidStringMap(metadata.getString("roles")).containsKey(playerId)) {
                    delegatedBankRoles++;
                }
                if (decodeUuidList(metadata.getString("cofounders")).contains(playerId)) {
                    delegatedBankRoles++;
                }
                if (decodeEmployeeRoleMap(metadata.getString("employees")).containsKey(playerId)) {
                    delegatedBankRoles++;
                }
                if (decodeShareMap(metadata.getString("shares")).containsKey(playerId)) {
                    delegatedBankRoles++;
                }

                for (AccountHolder account : bank.getBankAccounts().values()) {
                    if (account == null || !playerId.equals(account.getPlayerUUID())) {
                        continue;
                    }
                    accounts++;
                    totalBalance = totalBalance.add(account.getBalance());
                    txCount += account.getTransactions().size();
                    creditScoreTotal += account.getCreditScore();
                }
            }

            for (CompoundTag record : centralBank.getIssuedCreditCards().values()) {
                if (record == null || !record.hasUUID("ownerPlayerId") || !playerId.equals(record.getUUID("ownerPlayerId"))) {
                    continue;
                }
                cards++;
                if (record.getBoolean("blocked")) {
                    blockedCards++;
                } else {
                    activeCards++;
                }
            }

            for (ShopService.ShopSummary summary : ShopService.listAllShopSummaries(centralBank)) {
                if (summary == null || summary.shopId() == null) {
                    continue;
                }
                Map<UUID, String> roleMap = ShopService.listShopParticipantRoles(centralBank, summary.shopId());
                String role = roleMap.get(playerId);
                if (role == null || role.isBlank()) {
                    continue;
                }
                shops++;
                if (ShopService.SHOP_ROLE_OWNER.equals(role)) {
                    ownedShops++;
                }
            }
        }

        int avgCreditScore = accounts == 0 ? 0 : Math.round((float) creditScoreTotal / (float) accounts);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("playerId", playerId.toString());
        row.put("name", name);
        row.put("online", online);
        row.put("accounts", accounts);
        row.put("totalBalance", round2(totalBalance.doubleValue()));
        row.put("cards", cards);
        row.put("activeCards", activeCards);
        row.put("blockedCards", blockedCards);
        row.put("ownedBanks", ownedBanks);
        row.put("bankRoles", delegatedBankRoles);
        row.put("shops", shops);
        row.put("ownedShops", ownedShops);
        row.put("avgCreditScore", avgCreditScore);
        row.put("transactions", txCount);
        return row;
    }

    private Map<String, Object> buildAccountRow(Bank bank, AccountHolder account) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("accountId", account.getAccountUUID().toString());
        row.put("bankId", bank.getBankId().toString());
        row.put("bankName", bank.getBankName());
        row.put("type", account.getAccountType() == null ? AccountTypes.CheckingAccount.label : account.getAccountType().label);
        row.put("balance", round2(account.getBalance().doubleValue()));
        row.put("primary", account.isPrimaryAccount());
        row.put("frozen", account.isFrozen());
        row.put("frozenReason", account.getFrozenReason());
        row.put("accessType", account.getAccountAccessType());
        row.put("businessLabel", account.getBusinessLabel());
        row.put("creditScore", account.getCreditScore());
        row.put("defaulted", account.isDefaulted());
        row.put("activeLoans", account.getActiveLoans().size());
        row.put("transactions", account.getTransactions().size());
        return row;
    }

    private Map<UUID, String> collectPlayerNameHints(MinecraftServer current, CentralBank centralBank) {
        Map<UUID, String> hints = new HashMap<>();
        if (current != null && current.getPlayerList() != null) {
            for (ServerPlayer player : current.getPlayerList().getPlayers()) {
                if (player != null) {
                    hints.put(player.getUUID(), player.getGameProfile().getName());
                }
            }
        }
        if (centralBank == null) {
            return hints;
        }
        for (CompoundTag record : centralBank.getIssuedCreditCards().values()) {
            if (record == null || !record.hasUUID("ownerPlayerId")) {
                continue;
            }
            UUID owner = record.getUUID("ownerPlayerId");
            String holderName = safeString(record.getString("holderName"));
            if (!holderName.isBlank()) {
                hints.putIfAbsent(owner, holderName);
            }
        }
        return hints;
    }

    private Set<UUID> collectKnownPlayerIds(MinecraftServer current, CentralBank centralBank) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (current != null && current.getPlayerList() != null) {
            for (ServerPlayer player : current.getPlayerList().getPlayers()) {
                if (player != null) {
                    ids.add(player.getUUID());
                }
            }
        }
        if (centralBank == null) {
            return ids;
        }

        for (Bank bank : centralBank.getBanks().values()) {
            if (bank == null) {
                continue;
            }
            if (bank.getBankOwnerId() != null) {
                ids.add(bank.getBankOwnerId());
            }
            for (AccountHolder account : bank.getBankAccounts().values()) {
                if (account != null && account.getPlayerUUID() != null) {
                    ids.add(account.getPlayerUUID());
                }
            }
            CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
            ids.addAll(decodeUuidStringMap(metadata.getString("roles")).keySet());
            ids.addAll(decodeUuidList(metadata.getString("cofounders")));
            ids.addAll(decodeShareMap(metadata.getString("shares")).keySet());
            ids.addAll(decodeEmployeeRoleMap(metadata.getString("employees")).keySet());
        }

        for (CompoundTag record : centralBank.getIssuedCreditCards().values()) {
            if (record != null && record.hasUUID("ownerPlayerId")) {
                ids.add(record.getUUID("ownerPlayerId"));
            }
        }

        for (ShopService.ShopSummary summary : ShopService.listAllShopSummaries(centralBank)) {
            if (summary == null || summary.shopId() == null) {
                continue;
            }
            ids.addAll(ShopService.listShopParticipantRoles(centralBank, summary.shopId()).keySet());
        }
        return ids;
    }

    private boolean captureHourlyEconomySnapshotIfDue() {
        MinecraftServer current = server;
        if (current == null) {
            return false;
        }
        CentralBank centralBank = BankManager.getCentralBank(current);
        if (centralBank == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        long lastSnapshot = metadata.getLong("webadminLastSnapshotMillis");
        if (lastSnapshot > 0L && (now - lastSnapshot) < HISTORY_INTERVAL_MILLIS) {
            pruneHistorySnapshots(centralBank, now);
            return false;
        }

        EconomySnapshot snapshot = collectEconomySnapshot(current, centralBank);
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", HISTORY_KIND);
        tag.putLong("capturedAtMillis", snapshot.capturedAtMillis());
        tag.putInt("banksTotal", snapshot.banksTotal());
        tag.putInt("activeBanks", snapshot.activeBanks());
        tag.putInt("accountsTotal", snapshot.accountsTotal());
        tag.putString("totalDeposits", snapshot.totalDeposits().toPlainString());
        tag.putString("totalReserves", snapshot.totalReserves().toPlainString());
        tag.putString("moneyCirculating", snapshot.moneyCirculating().toPlainString());
        tag.putInt("onlinePlayers", snapshot.onlinePlayers());
        tag.putInt("shopsTotal", snapshot.shopsTotal());
        tag.putLong("shopsRevenueDollars", snapshot.shopsRevenueDollars());
        tag.putInt("issuedCards", snapshot.issuedCards());
        tag.putInt("activeCards", snapshot.activeCards());
        tag.putInt("blockedCards", snapshot.blockedCards());

        centralBank.getReportSnapshots().put(UUID.randomUUID(), tag);
        metadata.putLong("webadminLastSnapshotMillis", now);
        centralBank.putBankMetadata(centralBank.getBankId(), metadata);
        pruneHistorySnapshots(centralBank, now);
        BankManager.markDirty();
        return true;
    }

    private void pruneHistorySnapshots(CentralBank centralBank, long now) {
        if (centralBank == null) {
            return;
        }
        long oldestAllowed = now - HISTORY_RETENTION_MILLIS;
        List<UUID> removeIds = new ArrayList<>();
        for (Map.Entry<UUID, CompoundTag> entry : centralBank.getReportSnapshots().entrySet()) {
            UUID id = entry.getKey();
            CompoundTag tag = entry.getValue();
            if (id == null || tag == null) {
                continue;
            }
            if (!HISTORY_KIND.equals(tag.getString("kind"))) {
                continue;
            }
            long captured = tag.getLong("capturedAtMillis");
            if (captured <= 0L || captured < oldestAllowed) {
                removeIds.add(id);
            }
        }
        for (UUID id : removeIds) {
            centralBank.getReportSnapshots().remove(id);
        }
    }

    private List<Map<String, Object>> readEconomyHistory(CentralBank centralBank, long now) {
        List<Map<String, Object>> points = new ArrayList<>();
        if (centralBank == null) {
            return points;
        }

        long oldestAllowed = now - HISTORY_RETENTION_MILLIS;
        for (CompoundTag tag : centralBank.getReportSnapshots().values()) {
            if (tag == null || !HISTORY_KIND.equals(tag.getString("kind"))) {
                continue;
            }
            long captured = tag.getLong("capturedAtMillis");
            if (captured <= 0L || captured < oldestAllowed) {
                continue;
            }
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("capturedAtMillis", captured);
            point.put("banksTotal", tag.getInt("banksTotal"));
            point.put("activeBanks", tag.getInt("activeBanks"));
            point.put("accountsTotal", tag.getInt("accountsTotal"));
            point.put("totalDeposits", round2(decimalOrZero(tag, "totalDeposits").doubleValue()));
            point.put("totalReserves", round2(decimalOrZero(tag, "totalReserves").doubleValue()));
            point.put("moneyCirculating", round2(decimalOrZero(tag, "moneyCirculating").doubleValue()));
            point.put("onlinePlayers", tag.getInt("onlinePlayers"));
            point.put("shopsTotal", tag.getInt("shopsTotal"));
            point.put("shopsRevenueDollars", tag.getLong("shopsRevenueDollars"));
            point.put("issuedCards", tag.getInt("issuedCards"));
            point.put("activeCards", tag.getInt("activeCards"));
            point.put("blockedCards", tag.getInt("blockedCards"));
            points.add(point);
        }
        points.sort(Comparator.comparingLong(point -> asLong(point.get("capturedAtMillis"))));

        if (points.isEmpty()) {
            EconomySnapshot snapshot = collectEconomySnapshot(server, centralBank);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("capturedAtMillis", snapshot.capturedAtMillis());
            point.put("banksTotal", snapshot.banksTotal());
            point.put("activeBanks", snapshot.activeBanks());
            point.put("accountsTotal", snapshot.accountsTotal());
            point.put("totalDeposits", round2(snapshot.totalDeposits().doubleValue()));
            point.put("totalReserves", round2(snapshot.totalReserves().doubleValue()));
            point.put("moneyCirculating", round2(snapshot.moneyCirculating().doubleValue()));
            point.put("onlinePlayers", snapshot.onlinePlayers());
            point.put("shopsTotal", snapshot.shopsTotal());
            point.put("shopsRevenueDollars", snapshot.shopsRevenueDollars());
            point.put("issuedCards", snapshot.issuedCards());
            point.put("activeCards", snapshot.activeCards());
            point.put("blockedCards", snapshot.blockedCards());
            points.add(point);
        }
        return points;
    }

    private List<Map<String, Object>> toCountRows(Map<String, Integer> counts, String labelKey) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(labelKey, entry.getKey());
            row.put("count", entry.getValue() == null ? 0 : entry.getValue());
            rows.add(row);
        }
        rows.sort((a, b) -> Integer.compare((Integer) b.get("count"), (Integer) a.get("count")));
        return rows;
    }

    private String normalizeBankStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ACTIVE";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal decimalOrZero(CompoundTag tag, String key) {
        if (tag == null || key == null || !tag.contains(key)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(tag.getString(key));
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private String decimal2(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    private double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (Exception ignored) {
            }
        }
        return 0.0D;
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeStatus(String value) {
        if (value == null || value.isBlank()) {
            return "ACTIVE";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private UUID parseUuidOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer parseIntegerOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean parseBooleanFlag(String raw) {
        if (raw == null) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        for (String value : values) {
            String cleaned = safeString(value);
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return "";
    }

    private ShopRegionBounds parseShopRegionBounds(JsonObject body) {
        if (body == null) {
            return null;
        }

        String dimensionId = firstNonBlank(
                safeString(getString(body, "dimensionId")),
                safeString(getString(body, "dimension")),
                safeString(getString(body, "dim"))
        );
        Integer minX = parseIntegerOrNull(firstNonBlank(safeString(getString(body, "minX")), safeString(getString(body, "x1"))));
        Integer minY = parseIntegerOrNull(firstNonBlank(safeString(getString(body, "minY")), safeString(getString(body, "y1"))));
        Integer minZ = parseIntegerOrNull(firstNonBlank(safeString(getString(body, "minZ")), safeString(getString(body, "z1"))));
        Integer maxX = parseIntegerOrNull(firstNonBlank(safeString(getString(body, "maxX")), safeString(getString(body, "x2"))));
        Integer maxY = parseIntegerOrNull(firstNonBlank(safeString(getString(body, "maxY")), safeString(getString(body, "y2"))));
        Integer maxZ = parseIntegerOrNull(firstNonBlank(safeString(getString(body, "maxZ")), safeString(getString(body, "z2"))));
        if (dimensionId.isBlank() || minX == null || minY == null || minZ == null || maxX == null || maxY == null || maxZ == null) {
            return null;
        }
        BlockPos first = new BlockPos(minX, minY, minZ);
        BlockPos second = new BlockPos(maxX, maxY, maxZ);
        return new ShopRegionBounds(dimensionId, first, second);
    }

    private BigDecimal parsePositiveAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String cleaned = raw.trim().replace(",", "").replace("$", "");
            BigDecimal value = new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_EVEN);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return value;
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal parseNonNegativeAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String cleaned = raw.trim().replace(",", "").replace("$", "");
            BigDecimal value = new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_EVEN);
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                return null;
            }
            return value;
        } catch (Exception ex) {
            return null;
        }
    }

    private Double parseDoubleOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String cleaned = raw.trim()
                    .replace("%", "")
                    .replace("$", "")
                    .replace(",", "");
            return Double.parseDouble(cleaned);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeEditableBankStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ACTIVE", "WARNING", "RESTRICTED", "SUSPENDED", "REVOKED", "LOCKDOWN" -> normalized;
            default -> null;
        };
    }

    private String normalizeEditableOwnershipModel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "SOLE";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        return switch (normalized) {
            case "sole", "single", "owner" -> "SOLE";
            case "fixedcofounders", "cofounders", "cofounder", "fixed" -> "FIXED_COFOUNDERS";
            case "percentageshares", "shares", "share" -> "PERCENTAGE_SHARES";
            case "rolebased", "roles", "role" -> "ROLE_BASED";
            default -> null;
        };
    }

    private String normalizeEditableBankColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.matches("^#[0-9a-fA-F]{6}$")) {
            return value.toUpperCase(Locale.ROOT);
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "blue" -> "#55AAFF";
            case "lightblue", "aqua", "cyan" -> "#55FFFF";
            case "green" -> "#55FF55";
            case "red" -> "#FF5555";
            case "gold", "orange" -> "#FFAA00";
            case "yellow" -> "#FFFF55";
            case "white" -> "#FFFFFF";
            case "gray", "grey" -> "#AAAAAA";
            case "black" -> "#000000";
            case "purple", "magenta" -> "#AA55FF";
            default -> null;
        };
    }

    /**
     * Drains queued delivery items to owners once they become online and have
     * inventory capacity.
     */
    private void flushPendingDeliveriesNow() {
        MinecraftServer current = this.server;
        if (current == null || pendingDeliveriesByPlayer.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Deque<QueuedDelivery>> entry : pendingDeliveriesByPlayer.entrySet()) {
            UUID playerId = entry.getKey();
            Deque<QueuedDelivery> queue = entry.getValue();
            if (playerId == null || queue == null || queue.isEmpty()) {
                pendingDeliveriesByPlayer.remove(playerId, queue);
                continue;
            }
            ServerPlayer online = current.getPlayerList().getPlayer(playerId);
            if (online == null) {
                continue;
            }

            int delivered = 0;
            while (true) {
                QueuedDelivery queued = queue.peekFirst();
                if (queued == null) {
                    break;
                }
                ItemStack stack = queued.stack() == null ? ItemStack.EMPTY : queued.stack().copy();
                if (stack.isEmpty()) {
                    queue.pollFirst();
                    continue;
                }
                if (!online.getInventory().add(stack)) {
                    break;
                }
                queue.pollFirst();
                delivered++;
            }

            if (delivered > 0) {
                online.containerMenu.broadcastChanges();
                online.sendSystemMessage(Component.literal(
                        "UBS: delivered " + delivered + " queued admin item" + (delivered == 1 ? "." : "s.")
                ));
            }

            if (queue.isEmpty()) {
                pendingDeliveriesByPlayer.remove(playerId, queue);
            }
        }
    }

    private void queuePendingDelivery(UUID ownerId, ItemStack stack, String reason) {
        if (ownerId == null || stack == null || stack.isEmpty()) {
            return;
        }
        Deque<QueuedDelivery> queue = pendingDeliveriesByPlayer.computeIfAbsent(ownerId, id -> new ConcurrentLinkedDeque<>());
        while (queue.size() >= MAX_PENDING_DELIVERIES_PER_PLAYER) {
            queue.pollFirst();
        }
        queue.addLast(new QueuedDelivery(stack.copy(), safeString(reason), System.currentTimeMillis()));
    }

    /**
     * Sends an item directly if the owner is online and has free slots,
     * otherwise queues it for later delivery.
     */
    private Map<String, Object> deliverItemToOwnerOrQueue(MinecraftServer current, UUID ownerId, ItemStack stack, String reason) {
        if (ownerId == null) {
            return Map.of(
                    "deliveryStatus", "failed",
                    "deliveryQueued", false,
                    "deliveryMessage", "Owner UUID is missing; item could not be delivered."
            );
        }
        if (stack == null || stack.isEmpty()) {
            return Map.of(
                    "deliveryStatus", "failed",
                    "deliveryQueued", false,
                    "deliveryMessage", "No item to deliver."
            );
        }

        ServerPlayer owner = current.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            ItemStack give = stack.copy();
            if (owner.getInventory().add(give)) {
                owner.containerMenu.broadcastChanges();
                return Map.of(
                        "deliveryStatus", "delivered",
                        "deliveryQueued", false,
                        "deliveryMessage", "Delivered to owner inventory."
                );
            }
            queuePendingDelivery(ownerId, stack, reason);
            owner.sendSystemMessage(Component.literal("UBS: your inventory is full; admin delivery was queued."));
            return Map.of(
                    "deliveryStatus", "queued_inventory_full",
                    "deliveryQueued", true,
                    "deliveryMessage", "Owner inventory is full. Delivery queued until free space is available."
            );
        }

        queuePendingDelivery(ownerId, stack, reason);
        return Map.of(
                "deliveryStatus", "queued_offline",
                "deliveryQueued", true,
                "deliveryMessage", "Owner is offline. Delivery queued for next login."
        );
    }

    private void applyCreditCardRecordToStack(ItemStack stack, UUID cardId, CompoundTag record) {
        if (stack == null || stack.isEmpty() || cardId == null || record == null) {
            return;
        }
        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
        tag.putUUID(CreditCardService.TAG_CARD_ID, cardId);
        if (record.hasUUID("accountId")) {
            tag.putUUID(CreditCardService.TAG_ACCOUNT_ID, record.getUUID("accountId"));
        }
        if (record.hasUUID("bankId")) {
            tag.putUUID(CreditCardService.TAG_BANK_ID, record.getUUID("bankId"));
        }
        if (record.hasUUID("ownerPlayerId")) {
            tag.putUUID(CreditCardService.TAG_OWNER_ID, record.getUUID("ownerPlayerId"));
        }
        tag.putString(CreditCardService.TAG_BANK_NAME, safeString(record.getString("bankName")));
        tag.putString(CreditCardService.TAG_CARD_NUMBER, safeString(record.getString("cardNumber")));
        tag.putString(CreditCardService.TAG_CVC, safeString(record.getString("cvc")));
        tag.putLong(CreditCardService.TAG_ISSUED_AT, record.getLong("issuedEpochMillis"));
        tag.putLong(CreditCardService.TAG_EXPIRY_AT, record.getLong("expiryEpochMillis"));
        boolean blocked = record.getBoolean("blocked") || "BLOCKED".equalsIgnoreCase(record.getString("status"));
        boolean privateCard = record.getBoolean(CreditCardService.RECORD_PRIVATE_BANK_CARD);
        tag.putBoolean(CreditCardService.TAG_BLOCKED, blocked);
        tag.putBoolean(CreditCardService.TAG_PRIVATE_BANK_CARD, privateCard);
        ItemStackDataCompat.setCustomData(stack, tag);

        String bankName = safeString(record.getString("bankName"));
        if (bankName.isBlank()) {
            bankName = "Unknown Bank";
        }
        String masked = CreditCardService.maskCardNumber(record.getString("cardNumber"));
        String cardTitle = privateCard ? "Private Bank Card" : "Credit Card";
        String title = blocked
                ? cardTitle + " • BLOCKED • " + bankName + " • " + masked
                : cardTitle + " • " + bankName + " • " + masked;
        ItemStackDataCompat.setCustomName(stack, Component.literal(title));
    }

    private ItemStack buildCreditCardStackFromRecord(UUID cardId, CompoundTag record) {
        ItemStack stack = new ItemStack(ModItems.CREDIT_CARD.get());
        applyCreditCardRecordToStack(stack, cardId, record);
        return stack;
    }

    private void syncCardRecordToOnlineInventories(MinecraftServer current, UUID cardId, CompoundTag record) {
        if (current == null || cardId == null || record == null) {
            return;
        }
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            boolean changed = false;
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
                if (tag == null || !tag.hasUUID(CreditCardService.TAG_CARD_ID)) {
                    continue;
                }
                if (!cardId.equals(tag.getUUID(CreditCardService.TAG_CARD_ID))) {
                    continue;
                }
                applyCreditCardRecordToStack(stack, cardId, record);
                changed = true;
            }
            if (changed) {
                player.containerMenu.broadcastChanges();
            }
        }
    }

    private Bank findBankForAccount(CentralBank centralBank, UUID accountId) {
        if (centralBank == null || accountId == null) {
            return null;
        }
        for (Bank candidate : centralBank.getBanks().values()) {
            if (candidate == null || candidate.getBankAccounts() == null) {
                continue;
            }
            if (candidate.getBankAccounts().containsKey(accountId)) {
                return candidate;
            }
        }
        return null;
    }

    private void setPrimaryAccountForOwner(CentralBank centralBank, AccountHolder targetAccount) {
        if (centralBank == null || targetAccount == null) {
            return;
        }
        UUID ownerId = targetAccount.getPlayerUUID();
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank == null || bank.getBankAccounts() == null) {
                continue;
            }
            for (AccountHolder candidate : bank.getBankAccounts().values()) {
                if (candidate == null || ownerId == null || !ownerId.equals(candidate.getPlayerUUID())) {
                    continue;
                }
                candidate.setPrimaryAccount(candidate.getAccountUUID().equals(targetAccount.getAccountUUID()));
            }
        }
    }

    private void assignFallbackPrimary(CentralBank centralBank, UUID ownerId) {
        if (centralBank == null || ownerId == null) {
            return;
        }
        AccountHolder fallback = null;
        for (Bank bank : centralBank.getBanks().values()) {
            if (bank == null || bank.getBankAccounts() == null) {
                continue;
            }
            for (AccountHolder candidate : bank.getBankAccounts().values()) {
                if (candidate == null || !ownerId.equals(candidate.getPlayerUUID())) {
                    continue;
                }
                candidate.setPrimaryAccount(false);
                if (fallback == null) {
                    fallback = candidate;
                }
            }
        }
        if (fallback != null) {
            fallback.setPrimaryAccount(true);
        }
    }

    private String resolvePlayerName(MinecraftServer minecraftServer, UUID playerId, Map<UUID, String> hints) {
        if (playerId == null) {
            return "unknown";
        }
        if (minecraftServer != null && minecraftServer.getPlayerList() != null) {
            ServerPlayer online = minecraftServer.getPlayerList().getPlayer(playerId);
            if (online != null) {
                return online.getGameProfile().getName();
            }
        }
        if (hints != null) {
            String hint = hints.get(playerId);
            if (hint != null && !hint.isBlank()) {
                return hint;
            }
        }
        return shortId(playerId);
    }

    private String shortId(UUID id) {
        if (id == null) {
            return "unknown";
        }
        String raw = id.toString();
        return raw.length() <= 12 ? raw : raw.substring(0, 8);
    }

    private Map<UUID, String> decodeUuidStringMap(String encoded) {
        Map<UUID, String> result = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank() || !raw.contains("=")) {
                continue;
            }
            String[] parts = raw.split("=", 2);
            try {
                UUID id = UUID.fromString(parts[0].trim());
                String role = parts[1].trim();
                if (!role.isBlank()) {
                    result.put(id, role.toUpperCase(Locale.ROOT));
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private Map<UUID, BigDecimal> decodeShareMap(String encoded) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank() || !raw.contains("=")) {
                continue;
            }
            String[] parts = raw.split("=", 2);
            try {
                UUID id = UUID.fromString(parts[0].trim());
                BigDecimal percent = new BigDecimal(parts[1].trim()).setScale(2, RoundingMode.HALF_EVEN);
                if (percent.compareTo(BigDecimal.ZERO) > 0) {
                    result.put(id, percent);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private String encodeShareMap(Map<UUID, BigDecimal> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(entry -> entry.getKey() + "=" + entry.getValue().setScale(2, RoundingMode.HALF_EVEN).toPlainString())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private List<UUID> decodeUuidList(String encoded) {
        List<UUID> result = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(",");
        for (String entry : entries) {
            try {
                result.add(UUID.fromString(entry.trim()));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private String encodeUuidList(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return "";
        }
        return uuids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .map(UUID::toString)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private Map<UUID, String> decodeEmployeeRoleMap(String encoded) {
        Map<UUID, String> result = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank() || !raw.contains("=") || !raw.contains(":")) {
                continue;
            }
            String[] leftRight = raw.split("=", 2);
            String[] roleSalary = leftRight[1].split(":", 2);
            if (roleSalary.length < 1) {
                continue;
            }
            try {
                UUID id = UUID.fromString(leftRight[0].trim());
                String role = roleSalary[0].trim().toUpperCase(Locale.ROOT);
                if (!role.isBlank()) {
                    result.put(id, role);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private Map<UUID, EmployeeSpec> decodeEmployeeSpecMap(String encoded) {
        Map<UUID, EmployeeSpec> result = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank() || !raw.contains("=") || !raw.contains(":")) {
                continue;
            }
            String[] uuidAndRest = raw.split("=", 2);
            String[] roleAndSalary = uuidAndRest[1].split(":", 2);
            if (roleAndSalary.length < 2) {
                continue;
            }
            try {
                UUID id = UUID.fromString(uuidAndRest[0].trim());
                String role = roleAndSalary[0].trim().toUpperCase(Locale.ROOT);
                BigDecimal salary = new BigDecimal(roleAndSalary[1].trim());
                if (salary.compareTo(BigDecimal.ZERO) < 0 || role.isBlank()) {
                    continue;
                }
                result.put(id, new EmployeeSpec(role, salary));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private List<LoanProductSpec> decodeLoanProducts(String encoded) {
        List<LoanProductSpec> products = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return products;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            String raw = entry.trim();
            if (raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split("\\|");
            if (parts.length < 4) {
                continue;
            }
            try {
                String name = parts[0].trim();
                BigDecimal max = new BigDecimal(parts[1].trim());
                double rate = Double.parseDouble(parts[2].trim());
                long duration = Long.parseLong(parts[3].trim());
                if (!name.isBlank() && max.compareTo(BigDecimal.ZERO) > 0 && rate > 0.0 && duration >= 20L) {
                    products.add(new LoanProductSpec(name, max, rate, duration));
                }
            } catch (Exception ignored) {
            }
        }
        return products;
    }

    private String encodeLoanProducts(List<LoanProductSpec> products) {
        if (products == null || products.isEmpty()) {
            return "";
        }
        return products.stream()
                .filter(product -> product != null && product.name() != null && !product.name().isBlank())
                .sorted(Comparator.comparing(LoanProductSpec::name, String.CASE_INSENSITIVE_ORDER))
                .map(product -> product.name() + "|"
                        + product.maxAmount().toPlainString() + "|"
                        + product.interestRate() + "|"
                        + product.durationTicks())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private UUID resolvePlayerId(MinecraftServer current, String raw) {
        if (raw == null || raw.isBlank() || current == null || current.getPlayerList() == null) {
            return null;
        }
        String trimmed = raw.trim();
        UUID parsed = parseUuidOrNull(trimmed);
        if (parsed != null) {
            return parsed;
        }
        for (ServerPlayer online : current.getPlayerList().getPlayers()) {
            if (online == null || online.getGameProfile() == null) {
                continue;
            }
            String onlineName = online.getGameProfile().getName();
            if (onlineName != null && onlineName.equalsIgnoreCase(trimmed)) {
                return online.getUUID();
            }
        }
        return null;
    }

    private String resolveBankName(CentralBank centralBank, UUID bankId) {
        if (centralBank == null || bankId == null) {
            return "";
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null) {
            return shortId(bankId);
        }
        String bankName = safeString(bank.getBankName());
        return bankName.isBlank() ? shortId(bankId) : bankName;
    }

    private BigDecimal computeDailyCapForBank(Bank bank, CompoundTag metadata) {
        if (bank == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        }
        BigDecimal configured = bank.getDeclaredReserve()
                .multiply(BigDecimal.valueOf(Config.BANK_DAILY_LIQUIDITY_RATIO.get()))
                .setScale(2, RoundingMode.HALF_EVEN);
        if (metadata != null && metadata.contains("dailyCapOverride")) {
            String raw = safeString(metadata.getString("dailyCapOverride"));
            if (!raw.isBlank()) {
                try {
                    BigDecimal override = new BigDecimal(raw);
                    if (override.compareTo(BigDecimal.ZERO) >= 0) {
                        return override.setScale(2, RoundingMode.HALF_EVEN);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return configured.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN);
    }

    private Map<String, Object> buildDesktopPayload(String sessionId) {
        MinecraftServer current = requireServer();
        CentralBank centralBank = BankManager.getCentralBank(current);
        if (centralBank == null) {
            return Map.of("ok", false, "message", "Bank data unavailable.");
        }
        UUID selectedPlayer = resolveSelectedPlayer(sessionId, current, true);
        if (selectedPlayer == null) {
            return Map.of("ok", false, "message", "No online players available for impersonation.");
        }

        ensureVirtualDesktopContext(centralBank, selectedPlayer);
        OwnerPcDesktopDataPayload desktop = BankOwnerPcService.buildDesktopData(centralBank, selectedPlayer);
        List<OwnerPcBankAppSummary> apps = BankOwnerPcService.listAccessibleApps(current, centralBank, selectedPlayer, true);
        return Map.of(
                "ok", true,
                "selectedPlayerId", selectedPlayer.toString(),
                "desktop", desktop,
                "apps", apps
        );
    }

    private Map<String, Object> buildBootstrapPayload(String sessionId) {
        MinecraftServer current = requireServer();
        CentralBank centralBank = BankManager.getCentralBank(current);

        List<Map<String, Object>> onlinePlayers = new ArrayList<>();
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            if (player == null) {
                continue;
            }
            onlinePlayers.add(Map.of(
                    "playerId", player.getUUID().toString(),
                    "name", player.getGameProfile().getName()
            ));
        }
        onlinePlayers.sort(Comparator.comparing(entry -> String.valueOf(entry.get("name")), String.CASE_INSENSITIVE_ORDER));

        UUID selectedPlayer = resolveSelectedPlayer(sessionId, current, true);
        List<OwnerPcBankAppSummary> apps = List.of();
        if (centralBank != null && selectedPlayer != null) {
            ensureVirtualDesktopContext(centralBank, selectedPlayer);
            apps = BankOwnerPcService.listAccessibleApps(current, centralBank, selectedPlayer, true);
        }

        List<Map<String, Object>> banks = new ArrayList<>();
        int totalAccounts = 0;
        if (centralBank != null) {
            for (Bank bank : centralBank.getBanks().values()) {
                if (bank == null) {
                    continue;
                }
                totalAccounts += bank.getBankAccounts().size();
                String status = centralBank.getOrCreateBankMetadata(bank.getBankId()).getString("status");
                String ownerName = resolvePlayerName(current, bank.getBankOwnerId());
                banks.add(Map.of(
                        "bankId", bank.getBankId().toString(),
                        "name", bank.getBankName(),
                        "ownerId", bank.getBankOwnerId().toString(),
                        "ownerName", ownerName,
                        "accounts", bank.getBankAccounts().size(),
                        "status", status == null || status.isBlank() ? "ACTIVE" : status.toUpperCase(Locale.ROOT)
                ));
            }
        }
        banks.sort(Comparator.comparing(entry -> String.valueOf(entry.get("name")), String.CASE_INSENSITIVE_ORDER));

        long gameTime = 0L;
        if (current.getLevel(Level.OVERWORLD) != null) {
            gameTime = current.getLevel(Level.OVERWORLD).getGameTime();
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("banks", banks.size());
        metrics.put("accounts", totalAccounts);
        metrics.put("onlinePlayers", onlinePlayers.size());
        metrics.put("wsClients", webSocketChannels.size());
        metrics.put("gameTime", gameTime);

        return Map.of(
                "ok", true,
                "sessionId", sessionId,
                "selectedPlayerId", selectedPlayer == null ? "" : selectedPlayer.toString(),
                "selectedPlayerName", selectedPlayer == null ? "" : resolvePlayerName(current, selectedPlayer),
                "players", onlinePlayers,
                "apps", apps,
                "banks", banks,
                "metrics", metrics,
                "webAdmin", Map.of(
                        "bindHost", bindHost,
                        "bindPort", bindPort,
                        "running", running
                )
        );
    }

    private Map<String, Object> buildHealthPayload() {
        MinecraftServer current = server;
        int onlinePlayers = 0;
        long gameTime = 0L;
        if (current != null && current.getPlayerList() != null) {
            onlinePlayers = current.getPlayerList().getPlayers().size();
        }
        if (current != null && current.getLevel(Level.OVERWORLD) != null) {
            gameTime = current.getLevel(Level.OVERWORLD).getGameTime();
        }

        Map<String, Object> performance = Map.of();
        UltimateBankingSystem mod = UltimateBankingSystem.getInstance();
        if (mod != null) {
            try {
                performance = mod.buildPerformanceSnapshot(current);
            } catch (Exception ignored) {
                performance = Map.of();
            }
        }

        double serverMspt = asDouble(performance.get("serverAvgMspt"));
        String status = "ok";
        if (serverMspt >= 50.0D) {
            status = "critical";
        } else if (serverMspt >= 40.0D) {
            status = "warn";
        }

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("uptimeSeconds", Math.max(0L, (System.currentTimeMillis() - startedAtMillis) / 1000L));
        health.put("onlinePlayers", onlinePlayers);
        health.put("gameTime", gameTime);
        health.put("wsClients", webSocketChannels.size());
        health.put("status", status);
        health.put("performance", performance);
        health.put("timestamp", Instant.now().toString());
        return health;
    }

    private void sendSnapshot(Channel channel, String sessionId) {
        if (channel == null || !channel.isActive()) {
            return;
        }
        sendEvent(channel, "snapshot", callSafely(() -> buildBootstrapPayload(sessionId)));
    }

    private void sendSnapshotToSession(String sessionId) {
        for (Channel channel : webSocketChannels) {
            if (!channel.isActive()) {
                continue;
            }
            if (sessionId.equals(sessionIdOf(channel))) {
                sendSnapshot(channel, sessionId);
            }
        }
    }

    private void sendEvent(Channel channel, String event, Object data) {
        if (channel == null || !channel.isActive()) {
            return;
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event", event);
        envelope.put("data", data);
        envelope.put("ts", System.currentTimeMillis());
        channel.writeAndFlush(new TextWebSocketFrame(gson.toJson(envelope)));
    }

    private void broadcastEvent(String event, Object data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event", event);
        envelope.put("data", data);
        envelope.put("ts", System.currentTimeMillis());
        webSocketChannels.writeAndFlush(new TextWebSocketFrame(gson.toJson(envelope)));
    }

    private void appendAudit(String category,
                             String sessionId,
                             UUID impersonatedPlayer,
                             String detail,
                             boolean success) {
        AuditEntry entry = new AuditEntry(
                Instant.now().toString(),
                category,
                sessionId,
                impersonatedPlayer == null ? "" : impersonatedPlayer.toString(),
                detail == null ? "" : detail,
                success
        );
        auditEntries.addFirst(entry);
        while (auditEntries.size() > MAX_AUDIT_ENTRIES) {
            auditEntries.pollLast();
        }
    }

    private List<AuditEntry> copyAuditEntries() {
        return new ArrayList<>(auditEntries);
    }

    private UUID resolveSelectedPlayer(String sessionId, MinecraftServer current, boolean enforceOnline) {
        UUID selected = impersonationBySession.get(sessionId);
        if (selected != null && enforceOnline && current.getPlayerList().getPlayer(selected) == null) {
            selected = null;
        }

        if (selected == null && !current.getPlayerList().getPlayers().isEmpty()) {
            ServerPlayer first = current.getPlayerList().getPlayers().get(0);
            if (first != null) {
                selected = first.getUUID();
                impersonationBySession.put(sessionId, selected);
            }
        }
        return selected;
    }

    /**
     * Virtual desktop context enables web management even if the player is not
     * currently looking at a physical bank owner PC block.
     */
    private void ensureVirtualDesktopContext(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return;
        }
        int x = 8_000_000 + (playerId.hashCode() & 0x3FFF);
        int z = 8_000_000 + ((playerId.hashCode() >>> 16) & 0x3FFF);
        BankOwnerPcService.rememberDesktopContext(centralBank, playerId, DEFAULT_DIMENSION, x, 64, z);
    }

    private String sessionIdOf(Channel channel) {
        String value = channel.attr(SESSION_ID_ATTR).get();
        if (value == null || value.isBlank()) {
            value = sanitizeSessionId(null, channel);
            channel.attr(SESSION_ID_ATTR).set(value);
        }
        return value;
    }

    private String sanitizeSessionId(String requested, Channel channel) {
        if (requested != null) {
            String cleaned = requested.trim();
            if (!cleaned.isBlank()) {
                return cleaned.length() > 96 ? cleaned.substring(0, 96) : cleaned;
            }
        }
        return "chan-" + channel.id().asShortText();
    }

    private String sanitizeSessionId(String requested, FullHttpRequest request) {
        if (requested != null) {
            String cleaned = requested.trim();
            if (!cleaned.isBlank()) {
                return cleaned.length() > 96 ? cleaned.substring(0, 96) : cleaned;
            }
        }
        return "http-" + remoteAddress(request).replace(':', '_');
    }

    private String sanitizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "0.0.0.0";
        }
        return host.trim();
    }

    private String resolvePlayerName(MinecraftServer minecraftServer, UUID playerId) {
        if (minecraftServer == null || playerId == null) {
            return "unknown";
        }
        ServerPlayer online = minecraftServer.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return playerId.toString().substring(0, 8);
    }

    private JsonObject parseBodyAsObject(FullHttpRequest request) {
        String body = request.content().toString(StandardCharsets.UTF_8);
        if (body == null || body.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception ex) {
            return new JsonObject();
        }
    }

    private String getString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ex) {
            return "";
        }
    }

    private String remoteAddress(FullHttpRequest request) {
        String forwarded = request.headers().get("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return "unknown";
    }

    private MinecraftServer requireServer() {
        if (server == null) {
            throw new IllegalStateException("Minecraft server is not available.");
        }
        return server;
    }

    /**
     * Utility for thread-safe service access from Netty worker threads.
     */
    private <T> T callSafely(Callable<T> callable) {
        MinecraftServer current = requireServer();
        if (current.isSameThread()) {
            try {
                return callable.call();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        current.execute(() -> {
            try {
                future.complete(callable.call());
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        });
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private byte[] readResource(String resourcePath) throws IOException {
        InputStream in = WebAdminService.class.getResourceAsStream(resourcePath);
        if (in == null) {
            return null;
        }
        try (in) {
            return in.readAllBytes();
        }
    }

    /**
     * Serves item textures directly from local game/mod resources for dashboard tables.
     */
    FullHttpResponse serveItemIcon(String encodedItemId) {
        String decodedId = decodeItemId(encodedItemId);
        byte[] bytes;
        try {
            bytes = callSafely(() -> resolveItemIconBytes(decodedId));
        } catch (Exception ignored) {
            bytes = null;
        }
        if (bytes == null || bytes.length == 0) {
            String fallback = fallbackItemIconSvg(decodedId);
            return buildResponse(HttpResponseStatus.OK, fallback.getBytes(StandardCharsets.UTF_8), "image/svg+xml; charset=utf-8");
        }
        return buildResponse(HttpResponseStatus.OK, bytes, "image/png");
    }

    /**
     * Exposes a resolved, local-only item model payload so the web dashboard can
     * render item geometry without reaching external services.
     */
    FullHttpResponse serveItemModel(String encodedItemId) {
        String decodedId = decodeItemId(encodedItemId);
        Map<String, Object> payload;
        try {
            payload = callSafely(() -> resolveItemModelPayload(decodedId));
        } catch (Exception ex) {
            payload = new LinkedHashMap<>();
            payload.put("ok", false);
            payload.put("message", "Failed to resolve item model: " + safeString(ex.getMessage()));
            payload.put("itemId", decodedId.isBlank() ? "minecraft:barrier" : decodedId);
            payload.put("renderMode", "fallback");
            payload.put("layers", List.of());
            payload.put("elements", List.of());
        }
        return buildJsonResponse(HttpResponseStatus.OK, gson.toJson(payload));
    }

    /**
     * Serves texture bytes from local resource packs/mod assets only.
     */
    FullHttpResponse serveItemTexture(String encodedTextureId) {
        String decodedId = decodeItemId(encodedTextureId);
        byte[] bytes;
        try {
            bytes = callSafely(() -> resolveTextureBytes(decodedId));
        } catch (Exception ignored) {
            bytes = null;
        }
        if (bytes == null || bytes.length == 0) {
            String fallback = fallbackItemIconSvg(decodedId);
            return buildResponse(HttpResponseStatus.OK, fallback.getBytes(StandardCharsets.UTF_8), "image/svg+xml; charset=utf-8");
        }
        return buildResponse(HttpResponseStatus.OK, bytes, "image/png");
    }

    private Map<String, Object> resolveItemModelPayload(String rawItemId) {
        MinecraftServer current = requireServer();
        ResourceLocation registryId = resolveCanonicalItemId(rawItemId);
        Item item = BuiltInRegistries.ITEM.get(registryId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("itemId", registryId.toString());
        payload.put("itemName", item == null ? registryId.getPath() : item.getDescription().getString());
        payload.put("fallbackIconUrl", itemIconUrl(registryId.toString()));
        payload.put("layers", List.of());
        payload.put("elements", List.of());
        payload.put("modelChain", List.of());
        payload.put("renderMode", "fallback");

        ResourceLocation modelLocation = resourceLocationOrNull(registryId.getNamespace(), "models/item/" + registryId.getPath() + ".json");
        List<LoadedModelNode> chain = loadModelChain(current, modelLocation);
        if (chain.isEmpty()) {
            ResourceLocation direct = findFirstExistingTexture(current, registryId);
            if (direct != null) {
                payload.put("layers", List.of(textureLayer("layer0", direct)));
                payload.put("renderMode", "generated");
            }
            return payload;
        }

        List<String> modelChain = new ArrayList<>();
        for (LoadedModelNode node : chain) {
            modelChain.add(node.location().toString());
        }
        payload.put("modelChain", modelChain);

        Map<String, TextureBinding> textureBindings = collectTextureBindings(chain);
        List<Map<String, Object>> layers = resolveLayerTextures(textureBindings);
        if (layers.isEmpty()) {
            ResourceLocation direct = findFirstExistingTexture(current, registryId);
            if (direct != null) {
                layers.add(textureLayer("layer0", direct));
            }
        }
        payload.put("layers", layers);

        List<Map<String, Object>> elements = resolveModelElements(chain, textureBindings);
        payload.put("elements", elements);

        boolean generatedParent = isGeneratedModel(chain);
        String mode = !elements.isEmpty() ? "elements" : (!layers.isEmpty() || generatedParent ? "generated" : "fallback");
        payload.put("renderMode", mode);
        return payload;
    }

    private ResourceLocation resolveCanonicalItemId(String rawItemId) {
        ResourceLocation requestedId = parseItemResourceLocation(rawItemId);
        if (requestedId == null) {
            requestedId = ResourceLocation.tryParse("minecraft:barrier");
        }
        if (requestedId == null) {
            return Objects.requireNonNull(ResourceLocation.tryParse("minecraft:barrier"));
        }
        Item item = BuiltInRegistries.ITEM.get(requestedId);
        if (item != null) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key != null) {
                return key;
            }
        }
        return requestedId;
    }

    private List<LoadedModelNode> loadModelChain(MinecraftServer current, ResourceLocation rootModelLocation) {
        if (current == null || rootModelLocation == null) {
            return List.of();
        }
        List<LoadedModelNode> chain = new ArrayList<>();
        Set<ResourceLocation> visited = new HashSet<>();
        ResourceLocation cursor = rootModelLocation;
        int depth = 0;
        while (cursor != null && depth < 12 && visited.add(cursor)) {
            byte[] bytes = readGameResource(current, cursor);
            if (bytes == null || bytes.length == 0) {
                break;
            }
            JsonObject json;
            try {
                json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ignored) {
                break;
            }
            chain.add(new LoadedModelNode(cursor, json));
            cursor = parseModelReference(getString(json, "parent"), cursor.getNamespace());
            depth++;
        }
        return chain;
    }

    private Map<String, TextureBinding> collectTextureBindings(List<LoadedModelNode> chain) {
        Map<String, TextureBinding> bindings = new LinkedHashMap<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            LoadedModelNode node = chain.get(i);
            JsonObject json = node.json();
            if (!json.has("textures") || !json.get("textures").isJsonObject()) {
                continue;
            }
            JsonObject textures = json.getAsJsonObject("textures");
            for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                if (entry.getValue() == null || !entry.getValue().isJsonPrimitive()) {
                    continue;
                }
                String key = safeString(entry.getKey());
                String raw = safeString(entry.getValue().getAsString());
                if (key.isBlank() || raw.isBlank()) {
                    continue;
                }
                bindings.put(key, new TextureBinding(raw, node.location().getNamespace()));
            }
        }
        return bindings;
    }

    private List<Map<String, Object>> resolveLayerTextures(Map<String, TextureBinding> textureBindings) {
        List<Map<String, Object>> layers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String slot = "layer" + i;
            ResourceLocation texture = resolveTextureFromKey(slot, textureBindings, new HashSet<>());
            if (texture != null) {
                layers.add(textureLayer(slot, texture));
            }
        }
        return layers;
    }

    private List<Map<String, Object>> resolveModelElements(List<LoadedModelNode> chain, Map<String, TextureBinding> textureBindings) {
        if (chain == null || chain.isEmpty()) {
            return List.of();
        }
        LoadedModelNode source = null;
        JsonArray elementsArray = null;
        for (LoadedModelNode node : chain) {
            JsonObject json = node.json();
            if (json.has("elements") && json.get("elements").isJsonArray()) {
                source = node;
                elementsArray = json.getAsJsonArray("elements");
                break;
            }
        }
        if (source == null || elementsArray == null || elementsArray.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> elements = new ArrayList<>();
        for (JsonElement element : elementsArray) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject elementObject = element.getAsJsonObject();
            List<Double> from = parseNumericArray(elementObject.get("from"), 3);
            List<Double> to = parseNumericArray(elementObject.get("to"), 3);
            if (from.size() != 3 || to.size() != 3) {
                continue;
            }

            Map<String, Object> elementPayload = new LinkedHashMap<>();
            elementPayload.put("from", from);
            elementPayload.put("to", to);

            Map<String, Object> facesPayload = new LinkedHashMap<>();
            if (elementObject.has("faces") && elementObject.get("faces").isJsonObject()) {
                JsonObject faces = elementObject.getAsJsonObject("faces");
                for (String direction : List.of("north", "south", "east", "west", "up", "down")) {
                    if (!faces.has(direction) || !faces.get(direction).isJsonObject()) {
                        continue;
                    }
                    JsonObject face = faces.getAsJsonObject(direction);
                    String rawTextureRef = getString(face, "texture");
                    ResourceLocation texture = resolveTextureFromRaw(rawTextureRef, source.location().getNamespace(), textureBindings, new HashSet<>());

                    Map<String, Object> facePayload = new LinkedHashMap<>();
                    facePayload.put("textureRef", rawTextureRef);
                    facePayload.put("textureId", texture == null ? "" : texture.toString());
                    facePayload.put("textureUrl", texture == null ? "" : itemTextureUrl(texture.toString()));
                    facePayload.put("uv", parseNumericArray(face.get("uv"), 4));
                    facePayload.put("rotation", parseIntOrDefault(face.get("rotation"), 0));
                    facePayload.put("tintIndex", parseIntOrDefault(face.get("tintindex"), -1));
                    facesPayload.put(direction, facePayload);
                }
            }
            elementPayload.put("faces", facesPayload);
            elements.add(elementPayload);
        }
        return elements;
    }

    private List<Double> parseNumericArray(JsonElement raw, int expectedLength) {
        if (raw == null || !raw.isJsonArray()) {
            return List.of();
        }
        JsonArray array = raw.getAsJsonArray();
        List<Double> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                return List.of();
            }
            values.add(element.getAsDouble());
        }
        if (expectedLength > 0 && values.size() != expectedLength) {
            return List.of();
        }
        return values;
    }

    private int parseIntOrDefault(JsonElement raw, int fallback) {
        if (raw == null || !raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return raw.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Map<String, Object> textureLayer(String slot, ResourceLocation texture) {
        Map<String, Object> layer = new LinkedHashMap<>();
        layer.put("slot", slot);
        layer.put("textureId", texture.toString());
        layer.put("textureUrl", itemTextureUrl(texture.toString()));
        return layer;
    }

    private ResourceLocation resolveTextureFromKey(String key, Map<String, TextureBinding> bindings, Set<String> visitedKeys) {
        if (key == null || bindings == null || visitedKeys == null || !visitedKeys.add(key)) {
            return null;
        }
        TextureBinding binding = bindings.get(key);
        if (binding == null) {
            return null;
        }
        return resolveTextureFromRaw(binding.rawReference(), binding.namespace(), bindings, visitedKeys);
    }

    private ResourceLocation resolveTextureFromRaw(String rawReference,
                                                   String defaultNamespace,
                                                   Map<String, TextureBinding> bindings,
                                                   Set<String> visitedKeys) {
        String reference = safeString(rawReference);
        if (reference.isBlank()) {
            return null;
        }
        if (reference.startsWith("#")) {
            String key = safeString(reference.substring(1));
            if (key.isBlank()) {
                return null;
            }
            return resolveTextureFromKey(key, bindings, visitedKeys);
        }
        return parseTextureReference(reference, defaultNamespace);
    }

    private boolean isGeneratedModel(List<LoadedModelNode> chain) {
        for (LoadedModelNode node : chain) {
            String parent = safeString(getString(node.json(), "parent")).toLowerCase(Locale.ROOT);
            if (parent.endsWith("item/generated")) {
                return true;
            }
            String modelPath = node.location().getPath().toLowerCase(Locale.ROOT);
            if (modelPath.endsWith("/item/generated.json") || modelPath.endsWith("item/generated.json")) {
                return true;
            }
        }
        return false;
    }

    private ResourceLocation findFirstExistingTexture(MinecraftServer current, ResourceLocation itemId) {
        Set<ResourceLocation> textureCandidates = new LinkedHashSet<>();
        addDirectTextureCandidates(itemId, textureCandidates);
        addModelTextureCandidates(current, itemId, textureCandidates, new HashSet<>(), 0);
        textureCandidates.add(ResourceLocation.tryParse("minecraft:textures/item/barrier.png"));
        textureCandidates.add(ResourceLocation.tryParse("minecraft:textures/item/paper.png"));

        for (ResourceLocation candidate : textureCandidates) {
            if (candidate == null) {
                continue;
            }
            byte[] bytes = readGameResource(current, candidate);
            if (bytes != null && bytes.length > 0) {
                return candidate;
            }
        }
        return null;
    }

    private byte[] resolveTextureBytes(String rawTextureId) {
        MinecraftServer current = requireServer();
        ResourceLocation textureLocation = parseTextureLocation(rawTextureId);
        if (textureLocation == null) {
            return null;
        }
        return readGameResource(current, textureLocation);
    }

    private ResourceLocation parseTextureLocation(String rawTextureId) {
        String cleaned = safeString(rawTextureId).toLowerCase(Locale.ROOT);
        if (cleaned.isBlank()) {
            return null;
        }

        ResourceLocation parsed = ResourceLocation.tryParse(cleaned);
        if (parsed == null && !cleaned.contains(":")) {
            parsed = ResourceLocation.tryParse("minecraft:" + cleaned);
        }
        if (parsed == null) {
            return null;
        }

        String path = parsed.getPath();
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return resourceLocationOrNull(parsed.getNamespace(), path);
    }

    private String itemTextureUrl(String textureId) {
        String encoded = URLEncoder.encode(textureId, StandardCharsets.UTF_8);
        return "/api/webadmin/item-texture/" + encoded;
    }

    private String itemIconUrl(String itemId) {
        String encoded = URLEncoder.encode(itemId, StandardCharsets.UTF_8);
        return "/api/webadmin/item-icon/" + encoded;
    }

    private String decodeItemId(String encodedItemId) {
        String raw = safeString(encodedItemId);
        if (raw.isBlank()) {
            return "";
        }
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return raw.trim();
        }
    }

    private String fallbackItemIconSvg(String itemId) {
        String text = safeString(itemId);
        if (text.isBlank()) {
            text = "item";
        }
        if (text.length() > 12) {
            text = text.substring(0, 12);
        }
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\">"
                + "<rect width=\"24\" height=\"24\" rx=\"4\" fill=\"#1b263b\"/>"
                + "<rect x=\"1\" y=\"1\" width=\"22\" height=\"22\" rx=\"3\" fill=\"none\" stroke=\"#4f6ea8\" stroke-width=\"1\"/>"
                + "<text x=\"12\" y=\"14\" text-anchor=\"middle\" font-family=\"Segoe UI, Arial, sans-serif\" font-size=\"6\" fill=\"#d7e3ff\">"
                + escaped
                + "</text></svg>";
    }

    private byte[] resolveItemIconBytes(String rawItemId) {
        MinecraftServer current = requireServer();
        ResourceLocation requestedId = parseItemResourceLocation(rawItemId);
        if (requestedId == null) {
            requestedId = ResourceLocation.tryParse("minecraft:barrier");
        }

        ResourceLocation registryId = requestedId;
        if (requestedId != null) {
            Item item = BuiltInRegistries.ITEM.get(requestedId);
            if (item != null) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                if (key != null) {
                    registryId = key;
                }
            }
        }
        if (registryId == null) {
            registryId = ResourceLocation.tryParse("minecraft:barrier");
        }
        if (registryId == null) {
            return null;
        }

        Set<ResourceLocation> textureCandidates = new LinkedHashSet<>();
        addDirectTextureCandidates(registryId, textureCandidates);
        addModelTextureCandidates(current, registryId, textureCandidates, new HashSet<>(), 0);
        textureCandidates.add(ResourceLocation.tryParse("minecraft:textures/item/barrier.png"));
        textureCandidates.add(ResourceLocation.tryParse("minecraft:textures/item/paper.png"));
        textureCandidates.add(ResourceLocation.tryParse("minecraft:textures/item/book.png"));

        for (ResourceLocation candidate : textureCandidates) {
            if (candidate == null) {
                continue;
            }
            byte[] bytes = readGameResource(current, candidate);
            if (bytes != null && bytes.length > 0) {
                return bytes;
            }
        }
        return null;
    }

    private ResourceLocation parseItemResourceLocation(String raw) {
        String cleaned = safeString(raw).toLowerCase(Locale.ROOT);
        if (cleaned.isBlank()) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(cleaned);
        if (parsed != null) {
            return parsed;
        }
        if (!cleaned.contains(":")) {
            return ResourceLocation.tryParse("minecraft:" + cleaned.replace(' ', '_'));
        }
        return null;
    }

    private void addDirectTextureCandidates(ResourceLocation itemId, Set<ResourceLocation> candidates) {
        if (itemId == null || candidates == null) {
            return;
        }
        candidates.add(resourceLocationOrNull(itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png"));
        candidates.add(resourceLocationOrNull(itemId.getNamespace(), "textures/block/" + itemId.getPath() + ".png"));
    }

    private void addModelTextureCandidates(MinecraftServer current,
                                           ResourceLocation itemId,
                                           Set<ResourceLocation> textureCandidates,
                                           Set<ResourceLocation> visitedModels,
                                           int depth) {
        if (current == null || itemId == null || textureCandidates == null || visitedModels == null || depth > 8) {
            return;
        }
        ResourceLocation modelLocation = resourceLocationOrNull(itemId.getNamespace(), "models/item/" + itemId.getPath() + ".json");
        collectModelTextures(current, modelLocation, textureCandidates, visitedModels, depth);
    }

    private void collectModelTextures(MinecraftServer current,
                                      ResourceLocation modelLocation,
                                      Set<ResourceLocation> textureCandidates,
                                      Set<ResourceLocation> visitedModels,
                                      int depth) {
        if (modelLocation == null || depth > 8 || !visitedModels.add(modelLocation)) {
            return;
        }
        byte[] modelBytes = readGameResource(current, modelLocation);
        if (modelBytes == null || modelBytes.length == 0) {
            return;
        }
        try {
            JsonObject model = JsonParser.parseString(new String(modelBytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (model.has("textures") && model.get("textures").isJsonObject()) {
                JsonObject textures = model.getAsJsonObject("textures");
                addTextureReference(textureCandidates, getString(textures, "layer0"), modelLocation.getNamespace());
                addTextureReference(textureCandidates, getString(textures, "layer1"), modelLocation.getNamespace());
                addTextureReference(textureCandidates, getString(textures, "layer2"), modelLocation.getNamespace());
                addTextureReference(textureCandidates, getString(textures, "particle"), modelLocation.getNamespace());
            }
            ResourceLocation parentModel = parseModelReference(getString(model, "parent"), modelLocation.getNamespace());
            if (parentModel != null) {
                collectModelTextures(current, parentModel, textureCandidates, visitedModels, depth + 1);
            }
        } catch (Exception ignored) {
        }
    }

    private void addTextureReference(Set<ResourceLocation> candidates, String rawReference, String defaultNamespace) {
        ResourceLocation texture = parseTextureReference(rawReference, defaultNamespace);
        if (texture != null) {
            candidates.add(texture);
        }
    }

    private ResourceLocation parseTextureReference(String rawReference, String defaultNamespace) {
        String reference = safeString(rawReference);
        if (reference.isBlank() || reference.startsWith("#")) {
            return null;
        }
        ResourceLocation parsed = reference.contains(":")
                ? ResourceLocation.tryParse(reference)
                : ResourceLocation.tryParse(defaultNamespace + ":" + reference);
        if (parsed == null) {
            return null;
        }
        String path = parsed.getPath();
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return resourceLocationOrNull(parsed.getNamespace(), path);
    }

    private ResourceLocation parseModelReference(String rawReference, String defaultNamespace) {
        String reference = safeString(rawReference);
        if (reference.isBlank() || reference.startsWith("#")) {
            return null;
        }
        ResourceLocation parsed = reference.contains(":")
                ? ResourceLocation.tryParse(reference)
                : ResourceLocation.tryParse(defaultNamespace + ":" + reference);
        if (parsed == null) {
            return null;
        }
        String path = parsed.getPath();
        if (!path.startsWith("models/")) {
            path = "models/" + path;
        }
        if (!path.endsWith(".json")) {
            path = path + ".json";
        }
        return resourceLocationOrNull(parsed.getNamespace(), path);
    }

    private ResourceLocation resourceLocationOrNull(String namespace, String path) {
        try {
            return ResourceLocation.tryParse(namespace + ":" + path);
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] readGameResource(MinecraftServer current, ResourceLocation path) {
        if (path == null) {
            return null;
        }
        try {
            var optional = current.getResourceManager().getResource(path);
            if (optional.isPresent()) {
                Resource resource = optional.get();
                try (InputStream in = resource.open()) {
                    return in.readAllBytes();
                }
            }
        } catch (Exception ignored) {
        }
        try {
            byte[] classpathBytes = readResource("/assets/" + path.getNamespace() + "/" + path.getPath());
            if (classpathBytes != null && classpathBytes.length > 0) {
                return classpathBytes;
            }
        } catch (Exception ignored) {
        }
        return readVanillaAssetFromLocalClientJar(path);
    }

    /**
     * Dedicated servers usually do not ship vanilla client textures/models.
     * This fallback loads minecraft assets from a local client jar when present.
     */
    private byte[] readVanillaAssetFromLocalClientJar(ResourceLocation path) {
        if (path == null || !"minecraft".equals(path.getNamespace())) {
            return null;
        }
        String cacheKey = path.toString();
        byte[] cached = vanillaAssetCache.get(cacheKey);
        if (cached != null) {
            return cached == NO_BYTES ? null : cached;
        }

        Path clientJar = resolveLocalMinecraftClientJar();
        if (clientJar == null || !Files.isRegularFile(clientJar)) {
            vanillaAssetCache.put(cacheKey, NO_BYTES);
            return null;
        }

        String entryName = "assets/" + path.getNamespace() + "/" + path.getPath();
        try (ZipFile zip = new ZipFile(clientJar.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                vanillaAssetCache.put(cacheKey, NO_BYTES);
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] bytes = in.readAllBytes();
                if (bytes.length == 0) {
                    vanillaAssetCache.put(cacheKey, NO_BYTES);
                    return null;
                }
                vanillaAssetCache.put(cacheKey, bytes);
                return bytes;
            }
        } catch (Exception ignored) {
            vanillaAssetCache.put(cacheKey, NO_BYTES);
            return null;
        }
    }

    private Path resolveLocalMinecraftClientJar() {
        if (localMinecraftClientJarResolved) {
            return localMinecraftClientJar;
        }

        synchronized (this) {
            if (localMinecraftClientJarResolved) {
                return localMinecraftClientJar;
            }
            localMinecraftClientJar = discoverLocalMinecraftClientJar();
            localMinecraftClientJarResolved = true;
            if (localMinecraftClientJar != null) {
                UltimateBankingSystem.LOGGER.info("[UBS WebAdmin] Using local client asset jar fallback: {}", localMinecraftClientJar);
            } else {
                UltimateBankingSystem.LOGGER.warn("[UBS WebAdmin] No local Minecraft client jar found for vanilla dashboard item rendering fallback.");
            }
            return localMinecraftClientJar;
        }
    }

    private Path discoverLocalMinecraftClientJar() {
        String userHomeRaw = System.getProperty("user.home", "");
        if (userHomeRaw == null || userHomeRaw.isBlank()) {
            return null;
        }
        Path userHome = Path.of(userHomeRaw);

        List<Path> candidates = new ArrayList<>();

        Path neoformArtifacts = userHome.resolve(".gradle").resolve("caches").resolve("neoformruntime").resolve("artifacts");
        Path neoformClient = newestMatching(neoformArtifacts, 2, p -> {
            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
            return name.startsWith("minecraft_") && name.endsWith("_client.jar");
        });
        if (neoformClient != null) {
            candidates.add(neoformClient);
        }

        Path forgeGradleRepo = userHome.resolve(".gradle").resolve("caches").resolve("forge_gradle").resolve("minecraft_repo").resolve("versions");
        Path forgeClient = newestMatching(forgeGradleRepo, 4, p -> p.getFileName().toString().equalsIgnoreCase("client.jar"));
        if (forgeClient != null) {
            candidates.add(forgeClient);
        }

        Path windowsMinecraftVersions = userHome.resolve("AppData").resolve("Roaming").resolve(".minecraft").resolve("versions");
        Path windowsClient = newestMatching(windowsMinecraftVersions, 3, this::looksLikeVersionClientJar);
        if (windowsClient != null) {
            candidates.add(windowsClient);
        }

        Path unixMinecraftVersions = userHome.resolve(".minecraft").resolve("versions");
        Path unixClient = newestMatching(unixMinecraftVersions, 3, this::looksLikeVersionClientJar);
        if (unixClient != null) {
            candidates.add(unixClient);
        }

        return newestByModified(candidates);
    }

    private boolean looksLikeVersionClientJar(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".jar")) {
            return false;
        }
        if (name.contains("server") || name.contains("installer")) {
            return false;
        }
        Path parent = path.getParent();
        if (parent == null) {
            return false;
        }
        String parentName = parent.getFileName().toString().toLowerCase(Locale.ROOT);
        String expected = parentName + ".jar";
        return name.equals(expected) || name.contains("client");
    }

    private Path newestMatching(Path root, int maxDepth, java.util.function.Predicate<Path> filter) {
        if (root == null || filter == null || !Files.isDirectory(root)) {
            return null;
        }
        try (Stream<Path> stream = Files.walk(root, Math.max(1, maxDepth))) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(filter)
                    .max(Comparator.comparingLong(this::lastModifiedSafe))
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path newestByModified(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        Path best = null;
        long bestTime = Long.MIN_VALUE;
        for (Path path : paths) {
            if (path == null || !Files.isRegularFile(path)) {
                continue;
            }
            long time = lastModifiedSafe(path);
            if (best == null || time > bestTime) {
                best = path;
                bestTime = time;
            }
        }
        return best;
    }

    private long lastModifiedSafe(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    FullHttpResponse serveStatic(String path) {
        String normalized = normalizeStaticPath(path);
        String resourcePath = "/webadmin" + normalized;
        try {
            byte[] bytes = readResource(resourcePath);
            if (bytes == null) {
                if (!"/index.html".equals(normalized)) {
                    bytes = readResource("/webadmin/index.html");
                    if (bytes != null) {
                        return buildResponse(HttpResponseStatus.OK, bytes, "text/html; charset=utf-8");
                    }
                }
                return buildJsonResponse(HttpResponseStatus.NOT_FOUND, gson.toJson(Map.of("ok", false, "message", "Not found")));
            }
            return buildResponse(HttpResponseStatus.OK, bytes, contentTypeFor(normalized));
        } catch (Exception ex) {
            return buildJsonResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    gson.toJson(Map.of("ok", false, "message", "Failed to load static asset.")));
        }
    }

    private String normalizeStaticPath(String path) {
        if (path == null || path.isBlank() || "/".equals(path) || "/ubs-admin".equals(path) || "/ubs-admin/".equals(path)) {
            return "/index.html";
        }
        if (path.startsWith("/ubs-admin/")) {
            String rest = path.substring("/ubs-admin".length());
            return rest.isBlank() ? "/index.html" : rest;
        }
        return "/index.html";
    }

    private String contentTypeFor(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (lower.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        return "text/plain; charset=utf-8";
    }

    FullHttpResponse buildJsonResponse(HttpResponseStatus status, String json) {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        return buildResponse(status, payload, "application/json; charset=utf-8");
    }

    private FullHttpResponse buildResponse(HttpResponseStatus status, byte[] payload, String contentType) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(payload)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, payload.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, X-Session-Id");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,OPTIONS");
        return response;
    }

    static final class ApiResponse {
        final HttpResponseStatus status;
        final Object payload;

        private ApiResponse(HttpResponseStatus status, Object payload) {
            this.status = status;
            this.payload = payload;
        }

        static ApiResponse ok(Object payload) {
            return new ApiResponse(HttpResponseStatus.OK, payload);
        }

        static ApiResponse badRequest(String message) {
            return new ApiResponse(HttpResponseStatus.BAD_REQUEST, Map.of("ok", false, "message", message));
        }

        static ApiResponse notFound(String message) {
            return new ApiResponse(HttpResponseStatus.NOT_FOUND, Map.of("ok", false, "message", message));
        }

        static ApiResponse methodNotAllowed(String message) {
            return new ApiResponse(HttpResponseStatus.METHOD_NOT_ALLOWED, Map.of("ok", false, "message", message));
        }

        static ApiResponse serverError(String message) {
            return new ApiResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, Map.of("ok", false, "message", message));
        }
    }

    record AuditEntry(String timestamp,
                      String category,
                      String sessionId,
                      String impersonatedPlayerId,
                      String detail,
                      boolean success) {}

    private record EmployeeSpec(String role, BigDecimal salary) {}

    private record LoanProductSpec(String name, BigDecimal maxAmount, double interestRate, long durationTicks) {}

    record QueuedDelivery(ItemStack stack, String reason, long queuedAtMillis) {}

    private record ShopRegionBounds(String dimensionId, BlockPos first, BlockPos second) {}

    /**
     * Per-shop item listing accumulator. Each shop contributes one averaged price
     * per item so world-wide averages are based on shops that sell the item.
     */
    private static final class ShopItemListingAccumulator {
        private final String itemId;
        private String itemName;
        private long priceTotalCents;
        private int listingCount;
        private boolean hasCreativeSource;
        private boolean hasNormalSource;

        private ShopItemListingAccumulator(String itemId, String itemName) {
            this.itemId = itemId == null ? "" : itemId.trim();
            String cleanedName = itemName == null ? "" : itemName.trim();
            this.itemName = cleanedName.isBlank() ? this.itemId : cleanedName;
        }

        private void addListing(long priceCents, boolean creativeSource, String candidateName) {
            priceTotalCents += Math.max(0L, priceCents);
            listingCount++;
            if (creativeSource) {
                hasCreativeSource = true;
            } else {
                hasNormalSource = true;
            }
            String cleanedName = candidateName == null ? "" : candidateName.trim();
            if (!cleanedName.isBlank() && cleanedName.length() > this.itemName.length()) {
                this.itemName = cleanedName;
            }
        }

        private String itemId() {
            return itemId;
        }

        private String itemName() {
            return itemName;
        }

        private int listingCount() {
            return listingCount;
        }

        private boolean hasCreativeSource() {
            return hasCreativeSource;
        }

        private boolean hasNormalSource() {
            return hasNormalSource;
        }

        private double averagePriceCents() {
            if (listingCount <= 0) {
                return 0.0D;
            }
            return priceTotalCents / (double) listingCount;
        }
    }

    /**
     * Per-shop aggregation used by the item detail endpoint.
     */
    private static final class ShopItemShopAggregate {
        private final UUID shopId;
        private final String shopName;
        private final String shopType;
        private long minPriceCents = Long.MAX_VALUE;
        private long maxPriceCents = Long.MIN_VALUE;
        private long totalPriceCents;
        private int listingCount;
        private double totalVelocityPerDay;
        private long stockUnits;
        private long lastSoldMillis;
        private boolean creativeSource;

        private ShopItemShopAggregate(UUID shopId, String shopName, String shopType) {
            this.shopId = shopId;
            this.shopName = shopName == null ? "Unnamed Shop" : shopName;
            this.shopType = shopType == null ? "" : shopType;
        }

        private void addListing(long priceCents,
                                int stock,
                                double velocityPerDay,
                                long lastSoldMillis,
                                boolean creativeSource) {
            long safePrice = Math.max(0L, priceCents);
            minPriceCents = Math.min(minPriceCents, safePrice);
            maxPriceCents = Math.max(maxPriceCents, safePrice);
            totalPriceCents += safePrice;
            listingCount++;
            totalVelocityPerDay += Math.max(0.0D, velocityPerDay);
            if (stock >= 0) {
                stockUnits += stock;
            }
            this.lastSoldMillis = Math.max(this.lastSoldMillis, Math.max(0L, lastSoldMillis));
            this.creativeSource = this.creativeSource || creativeSource;
        }

        private UUID shopId() {
            return shopId;
        }

        private String shopName() {
            return shopName;
        }

        private String shopType() {
            return shopType;
        }

        private int listingCount() {
            return listingCount;
        }

        private long minPriceCents() {
            return minPriceCents == Long.MAX_VALUE ? 0L : minPriceCents;
        }

        private long maxPriceCents() {
            return maxPriceCents == Long.MIN_VALUE ? 0L : maxPriceCents;
        }

        private double avgPriceCents() {
            if (listingCount <= 0) {
                return 0.0D;
            }
            return totalPriceCents / (double) listingCount;
        }

        private double totalVelocityPerDay() {
            return Math.max(0.0D, totalVelocityPerDay);
        }

        private long stockUnits() {
            return Math.max(0L, stockUnits);
        }

        private long lastSoldMillis() {
            return Math.max(0L, lastSoldMillis);
        }

        private boolean creativeSource() {
            return creativeSource;
        }
    }

    private static final class ShopItemPriceAggregate {
        private final String itemId;
        private String itemName;
        private double lowestPriceCents = Double.POSITIVE_INFINITY;
        private double highestPriceCents = Double.NEGATIVE_INFINITY;
        private double averagePriceCentsTotal;
        private int shopCount;
        private int listingCount;
        private boolean hasCreativeSources;
        private boolean hasNormalSources;
        private int creativeShopCount;
        private int normalShopCount;
        private String lowShopId = "";
        private String lowShopName = "";
        private String highShopId = "";
        private String highShopName = "";

        private ShopItemPriceAggregate(String itemId, String itemName) {
            this.itemId = itemId == null ? "" : itemId.trim();
            String cleanedName = itemName == null ? "" : itemName.trim();
            this.itemName = cleanedName.isBlank() ? this.itemId : cleanedName;
        }

        private void includeShop(UUID shopId,
                                 String shopName,
                                 double averagePriceCents,
                                 int listings,
                                 boolean creativeSource,
                                 boolean normalSource,
                                 String candidateName) {
            double safePrice = Math.max(0.0D, averagePriceCents);
            String safeShopName = shopName == null ? "" : shopName.trim();
            String safeShopId = shopId == null ? "" : shopId.toString();

            shopCount++;
            listingCount += Math.max(0, listings);
            averagePriceCentsTotal += safePrice;
            if (creativeSource) {
                hasCreativeSources = true;
                creativeShopCount++;
            }
            if (normalSource) {
                hasNormalSources = true;
                normalShopCount++;
            }

            if (safePrice <= lowestPriceCents) {
                lowestPriceCents = safePrice;
                lowShopId = safeShopId;
                lowShopName = safeShopName;
            }
            if (safePrice >= highestPriceCents) {
                highestPriceCents = safePrice;
                highShopId = safeShopId;
                highShopName = safeShopName;
            }

            String cleanedName = candidateName == null ? "" : candidateName.trim();
            if (!cleanedName.isBlank() && cleanedName.length() > this.itemName.length()) {
                this.itemName = cleanedName;
            }
        }

        private String itemId() {
            return itemId;
        }

        private String itemName() {
            return itemName;
        }

        private int shopCount() {
            return shopCount;
        }

        private int listingCount() {
            return listingCount;
        }

        private double lowestPriceCents() {
            return Double.isFinite(lowestPriceCents) ? lowestPriceCents : 0.0D;
        }

        private double highestPriceCents() {
            return Double.isFinite(highestPriceCents) ? highestPriceCents : 0.0D;
        }

        private double averagePriceCents() {
            if (shopCount <= 0) {
                return 0.0D;
            }
            return averagePriceCentsTotal / (double) shopCount;
        }

        private String lowShopId() {
            return lowShopId;
        }

        private String lowShopName() {
            return lowShopName;
        }

        private String highShopId() {
            return highShopId;
        }

        private String highShopName() {
            return highShopName;
        }

        private boolean hasCreativeSources() {
            return hasCreativeSources;
        }

        private boolean hasNormalSources() {
            return hasNormalSources;
        }

        private int creativeShopCount() {
            return creativeShopCount;
        }

        private int normalShopCount() {
            return normalShopCount;
        }
    }

    private record LoadedModelNode(ResourceLocation location, JsonObject json) {}

    private record TextureBinding(String rawReference, String namespace) {}

    private static final class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final WebAdminService service;

        private HttpHandler(WebAdminService service) {
            this.service = service;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            if (!request.decoderResult().isSuccess()) {
                writeAndFlush(ctx, request, service.buildJsonResponse(
                        HttpResponseStatus.BAD_REQUEST,
                        service.gson.toJson(Map.of("ok", false, "message", "Malformed HTTP request."))
                ));
                return;
            }

            if (request.method().equals(HttpMethod.OPTIONS)) {
                writeAndFlush(ctx, request, service.buildResponse(HttpResponseStatus.NO_CONTENT, new byte[0], "text/plain"));
                return;
            }

            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            String path = decoder.path();

            if ("/".equals(path)) {
                DefaultFullHttpResponse redirect = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
                redirect.headers().set(HttpHeaderNames.LOCATION, "/ubs-admin/");
                writeAndFlush(ctx, request, redirect);
                return;
            }

            if (path.startsWith("/api/webadmin/item-icon/")) {
                if (!request.method().equals(HttpMethod.GET)) {
                    writeAndFlush(ctx, request, service.buildJsonResponse(
                            HttpResponseStatus.METHOD_NOT_ALLOWED,
                            service.gson.toJson(Map.of("ok", false, "message", "Use GET for item icon route."))
                    ));
                    return;
                }
                String encodedItemId = path.substring("/api/webadmin/item-icon/".length());
                writeAndFlush(ctx, request, service.serveItemIcon(encodedItemId));
                return;
            }

            if (path.startsWith("/api/webadmin/item-texture/")) {
                if (!request.method().equals(HttpMethod.GET)) {
                    writeAndFlush(ctx, request, service.buildJsonResponse(
                            HttpResponseStatus.METHOD_NOT_ALLOWED,
                            service.gson.toJson(Map.of("ok", false, "message", "Use GET for item texture route."))
                    ));
                    return;
                }
                String encodedTextureId = path.substring("/api/webadmin/item-texture/".length());
                writeAndFlush(ctx, request, service.serveItemTexture(encodedTextureId));
                return;
            }

            if (path.startsWith("/api/webadmin/item-model/")) {
                if (!request.method().equals(HttpMethod.GET)) {
                    writeAndFlush(ctx, request, service.buildJsonResponse(
                            HttpResponseStatus.METHOD_NOT_ALLOWED,
                            service.gson.toJson(Map.of("ok", false, "message", "Use GET for item model route."))
                    ));
                    return;
                }
                String encodedItemId = path.substring("/api/webadmin/item-model/".length());
                writeAndFlush(ctx, request, service.serveItemModel(encodedItemId));
                return;
            }

            if (path.startsWith("/api/webadmin/")) {
                ApiResponse api = service.handleApi(request, path);
                writeAndFlush(ctx, request, service.buildJsonResponse(api.status, service.gson.toJson(api.payload)));
                return;
            }

            if (path.startsWith("/ubs-admin")) {
                writeAndFlush(ctx, request, service.serveStatic(path));
                return;
            }

            writeAndFlush(ctx, request, service.buildJsonResponse(
                    HttpResponseStatus.NOT_FOUND,
                    service.gson.toJson(Map.of("ok", false, "message", "Unknown route."))
            ));
        }

        private void writeAndFlush(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
            boolean keepAlive = HttpUtil.isKeepAlive(request);
            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                ctx.writeAndFlush(response);
            } else {
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    private static final class WebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
        private final WebAdminService service;

        private WebSocketHandler(WebAdminService service) {
            this.service = service;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete complete) {
                QueryStringDecoder query = new QueryStringDecoder(complete.requestUri());
                String sessionId = "";
                List<String> values = query.parameters().get("sessionId");
                if (values != null && !values.isEmpty()) {
                    sessionId = values.get(0);
                }
                service.registerWebSocket(ctx.channel(), sessionId);
                return;
            }
            super.userEventTriggered(ctx, evt);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
            if (frame instanceof PingWebSocketFrame) {
                ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
                return;
            }
            if (frame instanceof CloseWebSocketFrame) {
                ctx.close();
                return;
            }
            if (frame instanceof TextWebSocketFrame textFrame) {
                service.handleWebSocketText(ctx.channel(), textFrame.text());
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            service.unregisterWebSocket(ctx.channel());
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            UltimateBankingSystem.LOGGER.debug("[UBS WebAdmin] WebSocket error: {}", cause.getMessage());
            ctx.close();
        }
    }
}
