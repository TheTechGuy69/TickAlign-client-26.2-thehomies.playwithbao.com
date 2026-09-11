package com.homies.tickalign.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TickAlignConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "tickalign.json");

    // Config fields
    public boolean enabled = true;
    public boolean showHud = true;
    public int hudX = 5;
    public int hudY = 5;
    public List<String> allowedServers = new ArrayList<>(List.of(
        "thehomies.playwithbao.com"
    ));

    /* ---- load / save ---- */

    public static TickAlignConfig load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                TickAlignConfig cfg = GSON.fromJson(json, TickAlignConfig.class);
                if (cfg != null) return cfg;
            }
        } catch (Exception e) {
            TickAlignClient.LOGGER.warn("[TickAlign] Failed to load config, using defaults", e);
        }
        TickAlignConfig cfg = new TickAlignConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            TickAlignClient.LOGGER.warn("[TickAlign] Failed to save config", e);
        }
    }

    /* ---- server whitelist check ---- */

    public boolean isServerAllowed(String serverAddress) {
        if (allowedServers.isEmpty()) return true; // empty = allow all
        String addr = serverAddress.toLowerCase().trim();
        for (String allowed : allowedServers) {
            String a = allowed.toLowerCase().trim();
            // Match hostname (ignore port differences)
            if (addr.equals(a) || addr.startsWith(a + ":") || addr.contains(a)) {
                return true;
            }
        }
        return false;
    }

    /* ---- Cloth Config screen ---- */

    public Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("TickAlign Settings"))
            .setSavingRunnable(this::save);

        ConfigEntryBuilder entry = builder.entryBuilder();

        // General category
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        general.addEntry(entry.startBooleanToggle(Component.literal("Enabled"), enabled)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Enable/disable tick alignment"))
            .setSaveConsumer(v -> enabled = v)
            .build());

        general.addEntry(entry.startBooleanToggle(Component.literal("Show HUD"), showHud)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Show tick alignment status on screen"))
            .setSaveConsumer(v -> showHud = v)
            .build());

        general.addEntry(entry.startIntField(Component.literal("HUD X Position"), hudX)
            .setDefaultValue(5)
            .setMin(0).setMax(2000)
            .setSaveConsumer(v -> hudX = v)
            .build());

        general.addEntry(entry.startIntField(Component.literal("HUD Y Position"), hudY)
            .setDefaultValue(5)
            .setMin(0).setMax(2000)
            .setSaveConsumer(v -> hudY = v)
            .build());

        // Server whitelist category
        ConfigCategory servers = builder.getOrCreateCategory(Component.literal("Server Whitelist"));

        servers.addEntry(entry.startStrList(Component.literal("Allowed Servers"), allowedServers)
            .setDefaultValue(List.of("thehomies.playwithbao.com"))
            .setTooltip(Component.literal("Servers where tick alignment is active. Empty = allow all."))
            .setSaveConsumer(v -> allowedServers = new ArrayList<>(v))
            .build());

        return builder.build();
    }
}
