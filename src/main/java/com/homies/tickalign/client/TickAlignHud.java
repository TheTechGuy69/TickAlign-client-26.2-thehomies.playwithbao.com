package com.homies.tickalign.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * v3 HUD: shows quality grade, RTT, safety, and aligned packet count.
 * Example: "TA: 42ms ✓ EXCELLENT | 347 aligned"
 */
public class TickAlignHud {

    public static void register() {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("tickalign", "hud"),
            TickAlignHud::render
        );
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker dt) {
        TickAlignConfig config = TickAlignClient.getConfig();
        if (!config.showHud) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getDebugOverlay().showDebugScreen()) return;

        ServerTickModel model = TickAlignClient.getTickModel();
        PacketScheduler sched = TickAlignClient.getPacketScheduler();

        if (!TickAlignClient.isActiveOnServer()) {
            graphics.text(mc.font, "TA: not on whitelist", config.hudX, config.hudY, 0xFF666666, true);
            return;
        }

        String text;
        int color;

        if (!config.enabled || !sched.isEnabled()) {
            text = "TA: OFF";
            color = 0xFF888888;
        } else if (model.isServerDisabled()) {
            text = "TA: SERVER OFF";
            color = 0xFFFF5555;
        } else if (!model.isLocked()) {
            text = "TA: syncing...";
            color = 0xFFFFAA00;
        } else {
            ServerTickModel.Quality q = model.getQuality();
            String qStr = switch (q) {
                case EXCELLENT -> "\u2713 EXCELLENT";
                case GOOD      -> "\u2713 GOOD";
                case FAIR      -> "~ FAIR";
                case POOR      -> "\u2717 POOR";
                default        -> "?";
            };
            color = switch (q) {
                case EXCELLENT -> 0xFF55FF55;
                case GOOD      -> 0xFF88FF88;
                case FAIR      -> 0xFFFFAA00;
                case POOR      -> 0xFFFF5555;
                default        -> 0xFF888888;
            };

            long aligned = sched.getPacketsAligned();
            text = String.format("TA: %.0fms %s | %d aligned",
                model.getRttNanos() / 1e6, qStr, aligned);
        }

        graphics.text(mc.font, text, config.hudX, config.hudY, color, true);

        // Second line: safety margin + MSPT when locked
        if (model.isLocked() && model.isActive()) {
            String detail = String.format("  safety: %.1fms | tick: %.1fms",
                model.getSafetyMarginNanos() / 1e6,
                model.getTickIntervalNanos() / 1e6);
            graphics.text(mc.font, detail, config.hudX, config.hudY + 10, 0xFF888888, true);
        }
    }
}
