package com.homies.tickalign.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TickAlign Client v3 — 26.2 (Mojmap)
 *
 * v3: reduced log spam (logs lock once), proper cleanup,
 * sprint/slot alignment, connection quality grading.
 */
public class TickAlignClient implements ClientModInitializer {

    public static final String MOD_ID = "tickalign";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier CHANNEL_ID = Identifier.parse("tickalign:sync");

    private static TickAlignConfig config;
    private static volatile ServerTickModel tickModel;
    private static volatile PacketScheduler packetScheduler;
    private static KeyMapping toggleKey;
    private static volatile boolean activeOnServer = false;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("tickalign", "tickalign")
    );

    @Override
    public void onInitializeClient() {
        config = TickAlignConfig.load();
        tickModel = new ServerTickModel();
        packetScheduler = new PacketScheduler(tickModel);

        toggleKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.tickalign.toggle",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, CATEGORY)
        );

        PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payload.TYPE, Payload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, context) -> {
            if (!activeOnServer) return;
            byte[] data = payload.data();
            if (data.length < 1) return;

            switch (Protocol.type(data)) {
                case Protocol.TICK_SYNC -> {
                    var s = Protocol.decodeTickSync(data);
                    tickModel.onTickSync(s.tickNumber(), System.nanoTime());

                    // Log phase-lock once
                    if (tickModel.shouldLogLock()) {
                        LOGGER.info("[TickAlign] Phase-locked on server!");
                    }
                }
                case Protocol.PING_REQUEST -> {
                    var p = Protocol.decodePingRequest(data);
                    byte[] pong = Protocol.pongResponse(p.pingId(), p.serverSendNanos());
                    context.responseSender().sendPacket(new Payload(pong));
                }
                case Protocol.SYNC_STATE -> {
                    var st = Protocol.decodeSyncState(data);
                    tickModel.onSyncState(st.rttNanos(), st.tickIntervalNanos());
                }
                case Protocol.DISABLE -> {
                    var d = Protocol.decodeDisable(data);
                    tickModel.onServerDisable(d.disabled());
                    LOGGER.info("[TickAlign] Server {} alignment",
                        d.disabled() ? "disabled" : "enabled");
                }
                case Protocol.SERVER_CONFIG -> {
                    var cfg = Protocol.decodeServerConfig(data);
                    LOGGER.info("[TickAlign] Server MSPT: {:.1f} ms"
                        .replace("{:.1f}", "%.1f")
                        .formatted(cfg.msptNanos() / 1e6));
                }
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var serverData = Minecraft.getInstance().getCurrentServer();
            String address = serverData != null ? serverData.ip : "unknown";
            activeOnServer = config.isServerAllowed(address);
            LOGGER.info("[TickAlign] Server '{}' — {}",
                address, activeOnServer ? "whitelisted" : "not whitelisted, alignment off");
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            tickModel.reset();
            packetScheduler.shutdown();
            packetScheduler = new PacketScheduler(tickModel);
            activeOnServer = false;
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggleKey.consumeClick()) {
                boolean newState = !packetScheduler.isEnabled();
                packetScheduler.setEnabled(newState);
                if (mc.player != null) {
                    mc.player.sendSystemMessage(
                        Component.literal("[TickAlign] " + (newState ? "Enabled" : "Disabled"))
                    );
                }
            }
        });

        TickAlignHud.register();
        LOGGER.info("[TickAlign] v3.0 initialized (26.2). Toggle: \\");
    }

    public static TickAlignConfig getConfig()         { return config; }
    public static ServerTickModel getTickModel()      { return tickModel; }
    public static PacketScheduler getPacketScheduler(){ return packetScheduler; }
    public static boolean isActiveOnServer()          { return activeOnServer; }

    public record Payload(byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Payload> TYPE =
            new CustomPacketPayload.Type<>(CHANNEL_ID);
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC =
            StreamCodec.of(
                (buf, p) -> buf.writeBytes(p.data()),
                buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new Payload(bytes);
                }
            );
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
