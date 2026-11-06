package com.paero.speedbuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpeedBuilderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("paerospeedbuilder.json");
    private static ConfigData config = new ConfigData();
    private static final Map<UUID, Boolean> PENDING_CUSTOM_INPUT = new HashMap<UUID, Boolean>();

    public static void resetBestTime(UUID player, String buildName) {
        PlayerSettings settings = SpeedBuilderConfig.getPlayerSettings(player);
        settings.bestTimes.remove(buildName);
        SpeedBuilderConfig.saveConfig();
    }

    public static synchronized void loadConfig() {
        try {
            Path source = CONFIG_PATH;
            if (!Files.exists(source)) {
                Path legacy = CONFIG_PATH.getParent().resolve("spbtrainer.json");
                if (!Files.exists(legacy)) {
                    return;
                }
                source = legacy;
            }
            try (BufferedReader reader = Files.newBufferedReader(source)) {
                ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
                if (loaded != null) {
                    config = loaded;
                }
            }
        } catch (Exception e) {
            SpeedBuilderMod.LOGGER.error("Failed to load PaeroBuilder config", e);
        }
    }

    public static synchronized void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH);){
                GSON.toJson((Object)config, (Appendable)writer);
            }
        }
        catch (Exception e) {
            SpeedBuilderMod.LOGGER.error("Failed to save PaeroBuilder config", e);
        }
    }

    public static PlayerSettings getPlayerSettings(UUID uuid) {
        return SpeedBuilderConfig.config.players.computeIfAbsent(uuid, k -> new PlayerSettings());
    }

    public static GlobalSettings getGlobalSettings() {
        return SpeedBuilderConfig.config.globalSettings;
    }

    public static void setPreview(UUID uuid, boolean enabled) {
        SpeedBuilderConfig.getPlayerSettings(uuid).previewEnabled = enabled;
        SpeedBuilderConfig.saveConfig();
    }

    public static void setPreviewEnabled(UUID uuid, boolean enabled) {
        SpeedBuilderConfig.setPreview(uuid, enabled);
    }

    public static boolean togglePreview(UUID uuid) {
        PlayerSettings s = SpeedBuilderConfig.getPlayerSettings(uuid);
        s.previewEnabled = !s.previewEnabled;
        SpeedBuilderConfig.saveConfig();
        return s.previewEnabled;
    }

    public static boolean isPreviewEnabled(UUID uuid) {
        return SpeedBuilderConfig.getPlayerSettings(uuid).previewEnabled;
    }

    public static Long getBestTime(UUID player, String buildName) {
        return SpeedBuilderConfig.getPlayerSettings(player).bestTimes.get(buildName);
    }

    public static void setBestTime(UUID player, String buildName, long timeMs) {
        SpeedBuilderConfig.getPlayerSettings(player).bestTimes.put(buildName, timeMs);
        SpeedBuilderConfig.saveConfig();
    }

    public static int getPreviewTime(UUID playerUuid) {
        return Math.max(0, Math.min(60, SpeedBuilderConfig.getPlayerSettings(playerUuid).previewTime));
    }

    public static void setPreviewTime(UUID playerUuid, int seconds) {
        SpeedBuilderConfig.getPlayerSettings(playerUuid).previewTime = Math.max(0, Math.min(60, seconds));
        SpeedBuilderConfig.saveConfig();
    }

    /** Stopwatch colour above the hotbar: "#RRGGBB" or a colour name. */
    public static String getTimerColor(UUID uuid) {
        String c = SpeedBuilderConfig.getPlayerSettings(uuid).timerColor;
        return c == null ? "#FFFF55" : c;
    }

    public static void setTimerColor(UUID uuid, String color) {
        SpeedBuilderConfig.getPlayerSettings(uuid).timerColor = color;
        SpeedBuilderConfig.saveConfig();
    }

    /** Colour of the time shown under the finish title. */
    public static String getFinishTimeColor(UUID uuid) {
        String c = SpeedBuilderConfig.getPlayerSettings(uuid).finishTimeColor;
        return c == null ? "#FFFFFF" : c;
    }

    public static void setFinishTimeColor(UUID uuid, String color) {
        SpeedBuilderConfig.getPlayerSettings(uuid).finishTimeColor = color;
        SpeedBuilderConfig.saveConfig();
    }

    /** 1 = original sounds, 2 = build-start tune also plays on finish. */
    public static int getSoundTrack(UUID uuid) {
        int t = SpeedBuilderConfig.getPlayerSettings(uuid).soundTrack;
        return t == 2 ? 2 : 1;
    }

    public static void setSoundTrack(UUID uuid, int track) {
        SpeedBuilderConfig.getPlayerSettings(uuid).soundTrack = track == 2 ? 2 : 1;
        SpeedBuilderConfig.saveConfig();
    }

    public static final String DIFFICULTY_DEFAULT = "default";
    public static final String DIFFICULTY_PAERO = "paero";
    public static final int MIN_FINISH_TIME = 1;
    public static final int MAX_FINISH_TIME = 60;

    /** "default" or "paero". Paero never touches the player's inventory when a build starts. */
    public static String getDifficulty(UUID uuid) {
        String d = SpeedBuilderConfig.getPlayerSettings(uuid).difficulty;
        return DIFFICULTY_PAERO.equalsIgnoreCase(d) ? DIFFICULTY_PAERO : DIFFICULTY_DEFAULT;
    }

    public static void setDifficulty(UUID uuid, String difficulty) {
        SpeedBuilderConfig.getPlayerSettings(uuid).difficulty = DIFFICULTY_PAERO.equalsIgnoreCase(difficulty) ? DIFFICULTY_PAERO : DIFFICULTY_DEFAULT;
        SpeedBuilderConfig.saveConfig();
    }

    /** UI theme: "default", "dark" or "paero" (see Theme). */
    public static String getTheme(UUID uuid) {
        String t = SpeedBuilderConfig.getPlayerSettings(uuid).uiTheme;
        if (Theme.DARK.equalsIgnoreCase(t)) return Theme.DARK;
        if (Theme.PAERO.equalsIgnoreCase(t)) return Theme.PAERO;
        return Theme.DEFAULT;
    }

    public static void setTheme(UUID uuid, String theme) {
        String t = Theme.DARK.equalsIgnoreCase(theme) ? Theme.DARK : (Theme.PAERO.equalsIgnoreCase(theme) ? Theme.PAERO : Theme.DEFAULT);
        SpeedBuilderConfig.getPlayerSettings(uuid).uiTheme = t;
        SpeedBuilderConfig.saveConfig();
    }

    public static boolean isPaero(UUID uuid) {
        return DIFFICULTY_PAERO.equals(getDifficulty(uuid));
    }

    /** How many seconds the finish title stays on screen after a build is completed. */
    public static int getFinishTitleTime(UUID uuid) {
        return Math.max(MIN_FINISH_TIME, Math.min(MAX_FINISH_TIME, SpeedBuilderConfig.getPlayerSettings(uuid).finishTitleTime));
    }

    public static void setFinishTitleTime(UUID uuid, int seconds) {
        SpeedBuilderConfig.getPlayerSettings(uuid).finishTitleTime = Math.max(MIN_FINISH_TIME, Math.min(MAX_FINISH_TIME, seconds));
        SpeedBuilderConfig.saveConfig();
    }

    public static boolean isPendingCustomInput(UUID playerUuid) {
        return PENDING_CUSTOM_INPUT.getOrDefault(playerUuid, false);
    }

    public static void setPendingCustomInput(UUID playerUuid, boolean pending) {
        if (pending) {
            PENDING_CUSTOM_INPUT.put(playerUuid, true);
        } else {
            PENDING_CUSTOM_INPUT.remove(playerUuid);
        }
    }

    public static boolean isSignSuppressionEnabled(UUID playerUuid) {
        return SpeedBuilderConfig.getGlobalSettings().enableSignSuppression && SpeedBuilderConfig.getPlayerSettings(playerUuid).signTextSuppression;
    }

    public static void setSignSuppression(UUID playerUuid, boolean enabled) {
        SpeedBuilderConfig.getPlayerSettings(playerUuid).signTextSuppression = enabled;
        SpeedBuilderConfig.saveConfig();
    }

    public static boolean toggleSignSuppression(UUID playerUuid) {
        PlayerSettings s = SpeedBuilderConfig.getPlayerSettings(playerUuid);
        s.signTextSuppression = !s.signTextSuppression;
        SpeedBuilderConfig.saveConfig();
        return s.signTextSuppression;
    }

    public static void setGlobalSignSuppression(boolean enabled) {
        SpeedBuilderConfig.getGlobalSettings().enableSignSuppression = enabled;
        SpeedBuilderConfig.saveConfig();
    }

    public static boolean isGlobalSignSuppressionEnabled() {
        return SpeedBuilderConfig.getGlobalSettings().enableSignSuppression;
    }

    public static String getFinishTitle(UUID uuid) {
        String t = SpeedBuilderConfig.getPlayerSettings(uuid).finishTitle;
        return t == null ? "Superb!" : t;
    }

    public static String getFinishColor(UUID uuid) {
        String c = SpeedBuilderConfig.getPlayerSettings(uuid).finishColor;
        return c == null ? "green" : c;
    }

    public static void setFinishTitle(UUID uuid, String text) {
        SpeedBuilderConfig.getPlayerSettings(uuid).finishTitle = text;
        SpeedBuilderConfig.saveConfig();
    }

    public static void setFinishColor(UUID uuid, String color) {
        SpeedBuilderConfig.getPlayerSettings(uuid).finishColor = color;
        SpeedBuilderConfig.saveConfig();
    }

    public static class PlayerSettings {
        public boolean previewEnabled = true;
        public boolean showTutorial = true;
        public String customColor = "green";
        public Map<String, Long> bestTimes = new HashMap<String, Long>();
        public boolean signTextSuppression = true;
        public String finishTitle = "Superb!";
        public String finishColor = "green";
        public String finishTimeColor = "#FFFFFF";
        public String timerColor = "#FFFF55";
        public int soundTrack = 1;
        public int previewTime = 5;
        public int finishTitleTime = 5;
        public String difficulty = "default";
        public String uiTheme = "default";
    }

    public static class ConfigData {
        public Map<UUID, PlayerSettings> players = new HashMap<UUID, PlayerSettings>();
        public GlobalSettings globalSettings = new GlobalSettings();
    }

    public static class GlobalSettings {
        public String preferredBuild = "spbtest";
        public boolean announcePlacements = true;
        public boolean enableSignSuppression = true;
    }
}

