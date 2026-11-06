package com.paero.speedbuilder;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class grindMode {
    private static final Set<UUID> ACTIVE_GRIND = new HashSet<UUID>();
    private static final Set<UUID> PENDING_GRIND_PREVIEW = new HashSet<UUID>();
    private static final Map<UUID, Integer> CURRENT_SESSION_COUNT = new HashMap<UUID, Integer>();

    private static InputStream getBuildStream(String buildName) {
        InputStream customStream = customBuilds.getCustomBuildStream(buildName);
        if (customStream != null) {
            return customStream;
        }
        return grindMode.class.getResourceAsStream("/assets/paerospeedbuilder/builds/" + buildName + ".schem");
    }

    public static void enable(ServerPlayer player, String buildName) {
        UUID uuid = player.getUUID();
        CURRENT_SESSION_COUNT.put(uuid, 0);
        PENDING_GRIND_PREVIEW.add(uuid);
        buildLoader.previewSchematicWithCallback(player, buildName, () -> {
            PENDING_GRIND_PREVIEW.remove(uuid);
            ACTIVE_GRIND.add(uuid);
            buildMode.enable(player);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 10, 5));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Theme.lit(player, ("\u00a77Build selected: " + buildName))));
            SpeedBuilderMod.LOGGER.debug("[DEBUG] Sent 'Build selected' subtitle for grind mode");
            grindMode.startGrindSession(player, buildName);
            MinecraftServer server = player.getServer();
            buildLoader.schedule(server, 20, () -> {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 10, 5));
                player.connection.send(new ClientboundSetTitleTextPacket(Theme.countdownTitle(player, 3)));
                Sfx.countdownTick(player, false);
            }, player.getUUID().toString());
            buildLoader.schedule(server, 40, () -> {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 10, 5));
                player.connection.send(new ClientboundSetTitleTextPacket(Theme.countdownTitle(player, 2)));
                Sfx.countdownTick(player, false);
            }, player.getUUID().toString());
            buildLoader.schedule(server, 60, () -> {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 10, 5));
                player.connection.send(new ClientboundSetTitleTextPacket(Theme.countdownTitle(player, 1)));
                Sfx.countdownTick(player, true);
            }, player.getUUID().toString());
            buildLoader.schedule(server, 80, () -> {
                Sfx.buildStart(player);
                buildLoader.giveBlocks(player, buildName);
                try (InputStream stream = grindMode.getBuildStream(buildName);){
                    ClipboardFormat format;
                    if (stream != null && (format = ClipboardFormats.findByAlias("schem")) != null) {
                        ClipboardReader reader = format.getReader(stream);
                        Clipboard clipboard = reader.read();
                        BlockVector3 min = clipboard.getMinimumPoint();
                        BlockVector3 max = clipboard.getMaximumPoint();
                        BlockVector3 worldOrigin = BuildArea.origin(player.getServer());
                        long now = System.currentTimeMillis();
                        spbAccuracy.ACTIVE_SESSIONS.put(player.getUUID(), new spbAccuracy.BuildSession(clipboard, min, max, worldOrigin, now, buildName, true));
                        spbAccuracy.GRIND_ACCURACY_ENABLED.add(player.getUUID());
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }, player.getUUID().toString());
            buildLoader.schedule(server, 90, () -> spbAccuracy.GRIND_ACCURACY_ENABLED.add(player.getUUID()), player.getUUID().toString());
        });
    }

    public static void continueGrindSession(ServerPlayer player, String buildName) {
        if (!grindMode.isGrinding(player)) {
            return;
        }
        try (InputStream stream = grindMode.getBuildStream(buildName);){
            if (stream == null) {
                player.displayClientMessage(Theme.lit(player, ("\u00a7cCould not find schematic: " + buildName)), false);
                return;
            }
            ClipboardFormat format = ClipboardFormats.findByAlias("schem");
            if (format == null) {
                player.displayClientMessage(Theme.lit(player, "\u00a7cSchematic format 'schem' is not supported!"), false);
                return;
            }
            ClipboardReader reader = format.getReader(stream);
            Clipboard clipboard = reader.read();
            BlockVector3 min = clipboard.getMinimumPoint();
            BlockVector3 max = clipboard.getMaximumPoint();
            BlockVector3 worldOrigin = BuildArea.origin(player.getServer());
            buildLoader.clearBuildArea(player.serverLevel(), min, max, worldOrigin);
            buildLoader.clearInventory(player);
            buildLoader.giveBlocks(player, buildName);
            player.displayClientMessage(Theme.lit(player, ("\u00a7aBlocks replenished for " + buildName)), false);
            long now = System.currentTimeMillis();
            spbAccuracy.ACTIVE_SESSIONS.put(player.getUUID(), new spbAccuracy.BuildSession(clipboard, min, max, worldOrigin, now, buildName, true));
            spbAccuracy.GRIND_ACCURACY_ENABLED.add(player.getUUID());
            SpeedBuilderMod.LOGGER.debug("[DEBUG] Re-enabled grind accuracy for continuation round");
        }
        catch (Exception e) {
            player.displayClientMessage(Theme.lit(player, ("\u00a7cFailed to continue grind session for " + buildName)), false);
            e.printStackTrace();
        }
    }

    public static boolean isGrinding(ServerPlayer player) {
        return ACTIVE_GRIND.contains(player.getUUID());
    }

    public static void disable(ServerPlayer player) {
        ACTIVE_GRIND.remove(player.getUUID());
        PENDING_GRIND_PREVIEW.remove(player.getUUID());
        CURRENT_SESSION_COUNT.remove(player.getUUID());
        spbAccuracy.GRIND_ACCURACY_ENABLED.remove(player.getUUID());
        buildLoader.clearInventory(player);
        player.displayClientMessage(Theme.lit(player, SpeedBuilderConfig.isPaero(player.getUUID()) ? "\u00a7cGrind Mode disabled." : "\u00a7cGrind Mode disabled. Your inventory has been cleared."), false);
    }

    public static void startGrindSession(ServerPlayer player, String buildName) {
        try (InputStream stream = grindMode.getBuildStream(buildName);){
            if (stream == null) {
                player.displayClientMessage(Theme.lit(player, ("\u00a7cCould not find schematic: " + buildName)), false);
                return;
            }
            ClipboardFormat format = ClipboardFormats.findByAlias("schem");
            if (format == null) {
                player.displayClientMessage(Theme.lit(player, "\u00a7cSchematic format 'schem' is not supported!"), false);
                return;
            }
            ClipboardReader reader = format.getReader(stream);
            Clipboard clipboard = reader.read();
            BlockVector3 min = clipboard.getMinimumPoint();
            BlockVector3 max = clipboard.getMaximumPoint();
            BlockVector3 worldOrigin = BuildArea.origin(player.getServer());
            buildLoader.clearBuildArea(player.serverLevel(), min, max, worldOrigin);
            buildLoader.clearInventory(player);
        }
        catch (Exception e) {
            player.displayClientMessage(Theme.lit(player, ("\u00a7cFailed to start grind session for " + buildName)), false);
            e.printStackTrace();
        }
    }

    public static int incrementSessionCount(UUID playerUuid) {
        int current = CURRENT_SESSION_COUNT.getOrDefault(playerUuid, 0);
        CURRENT_SESSION_COUNT.put(playerUuid, current + 1);
        return current + 1;
    }

    public static int getSessionCount(UUID playerUuid) {
        return CURRENT_SESSION_COUNT.getOrDefault(playerUuid, 0);
    }

    public static void onBuildComplete(ServerPlayer player, String buildName) {
        if (!grindMode.isGrinding(player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        buildLoader.schedule(server, 60, () -> grindMode.continueGrindSession(player, buildName));
        player.displayClientMessage(Theme.lit(player, "\u00a76Build complete, resetting area in 3 seconds."), false);
    }
}

