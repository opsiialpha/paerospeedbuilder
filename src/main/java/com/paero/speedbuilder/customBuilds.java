package com.paero.speedbuilder;

import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.regions.CuboidRegion;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

public class customBuilds {
    private static final String CUSTOM_BUILDS_FOLDER = FMLPaths.CONFIGDIR.get().resolve("paerospeedbuilder").resolve("custom_builds").toString() + File.separator;
    private static final Set<String> customBuildNames = new HashSet<String>();

    private static void loadExistingCustomBuilds() {
        try {
            Path customBuildsPath = Paths.get(CUSTOM_BUILDS_FOLDER);
            if (Files.exists(customBuildsPath) && Files.isDirectory(customBuildsPath)) {
                Files.list(customBuildsPath).filter(path -> path.toString().endsWith(".schem")).forEach(path -> {
                    String fileName = path.getFileName().toString();
                    String buildName = fileName.substring(0, fileName.length() - 6);
                    customBuildNames.add(buildName);
                });
                SpeedBuilderMod.LOGGER.info("Loaded " + customBuildNames.size() + " custom builds");
            }
        }
        catch (IOException e) {
            SpeedBuilderMod.LOGGER.error("Failed to load existing custom builds: " + e.getMessage());
        }
    }

    /*
     * Enabled aggressive exception aggregation
     */
    public static int createCustomBuild(CommandSourceStack source, String buildName) {
        ServerPlayer player;
        try {
            player = source.getPlayer();
        }
        catch (Exception e) {
            source.sendFailure(Theme.lit(source, "You must be a player to create custom builds!"));
            return 0;
        }
        if (buildName.isEmpty() || !buildName.matches("[a-zA-Z0-9_-]+")) {
            player.displayClientMessage(Theme.lit(player, "\u00a7cBuild name must contain only letters, numbers, underscores, and hyphens!"), false);
            return 0;
        }
        if (customBuilds.isCustomBuild(buildName) || commands.isValidBuildName(buildName)) {
            player.displayClientMessage(Theme.lit(player, ("\u00a7cBuild '" + buildName + "' already exists!")), false);
            return 0;
        }
        try {
            ServerLevel world = player.serverLevel();
            BlockVector3 areaMin = BuildArea.origin(source.getServer());
            BlockVector3 areaMax = areaMin.add(BuildArea.SIZE_XZ - 1, BuildArea.HEIGHT - 1, BuildArea.SIZE_XZ - 1);
            int minY = 999;
            int maxY = -999;
            boolean hasNonAirBlocks = false;
            for (int y = areaMin.y(); y <= areaMax.y(); ++y) {
                for (int x = areaMin.x(); x <= areaMax.x(); ++x) {
                    for (int z = areaMin.z(); z <= areaMax.z(); ++z) {
                        BlockPos mcPos = new BlockPos(x, y, z);
                        net.minecraft.world.level.block.state.BlockState mcBlockState = world.getBlockState(mcPos);
                        if (mcBlockState.isAir()) continue;
                        hasNonAirBlocks = true;
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            if (!hasNonAirBlocks) {
                player.displayClientMessage(Theme.lit(player, "\u00a7cThe build area is empty! Build something in the area first."), false);
                player.displayClientMessage(Theme.lit(player, "\u00a77Build area: " + BuildArea.rangeText(source.getServer())), false);
                return 0;
            }
            CuboidRegion region = new CuboidRegion(BlockVector3.at(areaMin.x(), minY, areaMin.z()), BlockVector3.at(areaMax.x(), maxY, areaMax.z()));
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
            clipboard.setOrigin(BlockVector3.at(areaMin.x(), minY, areaMin.z()));
            int blockCount = 0;
            for (int y = minY; y <= maxY; ++y) {
                for (int x = areaMin.x(); x <= areaMax.x(); ++x) {
                    for (int z = areaMin.z(); z <= areaMax.z(); ++z) {
                        BlockPos mcPos = new BlockPos(x, y, z);
                        net.minecraft.world.level.block.state.BlockState mcBlockState = world.getBlockState(mcPos);
                        com.sk89q.worldedit.world.block.BlockState weBlockState = NeoForgeAdapter.adapt(mcBlockState);
                        BlockVector3 clipboardPos = BlockVector3.at(x, y, z);
                        clipboard.setBlock(clipboardPos, weBlockState);
                        if (weBlockState.getBlockType().getId().equals("minecraft:air")) continue;
                        ++blockCount;
                    }
                }
            }
            File customBuildsDir = new File(CUSTOM_BUILDS_FOLDER);
            if (!customBuildsDir.exists()) {
                customBuildsDir.mkdirs();
            }
            File schematicFile = new File(customBuildsDir, buildName + ".schem");
            ClipboardFormat format = ClipboardFormats.findByAlias("schem");
            if (format == null) {
                player.displayClientMessage(Theme.lit(player, "\u00a7cSchematic format 'schem' is not supported!"), false);
                return 0;
            }
            try (FileOutputStream fos = new FileOutputStream(schematicFile);
                 ClipboardWriter writer = format.getWriter(fos)) {
                writer.write(clipboard);
                customBuildNames.add(buildName);
                player.displayClientMessage(Theme.lit(player, "\u00a7aCustom build '" + buildName + "' created successfully!"), false);
                player.displayClientMessage(Theme.lit(player, "\u00a77Blocks: " + blockCount + " | Height: Y" + minY + " to Y" + maxY + " (" + (maxY - minY + 1) + " layers)"), false);
                player.displayClientMessage(Theme.lit(player, "\u00a77Size: " + (areaMax.x() - areaMin.x() + 1) + "x" + (maxY - minY + 1) + "x" + (areaMax.z() - areaMin.z() + 1)), false);
                player.displayClientMessage(Theme.lit(player, "\u00a77Use \u00a7e/pb " + buildName + " \u00a77to practice this build!"), false);
                return 1;
            }
        }
        catch (Exception e) {
            player.displayClientMessage(Theme.lit(player, ("\u00a7cFailed to create custom build: " + e.getMessage())), false);
            e.printStackTrace();
            return 0;
        }
    }

    public static boolean isCustomBuild(String buildName) {
        return customBuildNames.contains(buildName);
    }

    public static InputStream getCustomBuildStream(String buildName) {
        if (!customBuilds.isCustomBuild(buildName)) {
            return null;
        }
        File schematicFile = new File(CUSTOM_BUILDS_FOLDER + buildName + ".schem");
        if (!schematicFile.exists()) {
            return null;
        }
        try {
            return new FileInputStream(schematicFile);
        }
        catch (Exception e) {
            SpeedBuilderMod.LOGGER.error("Failed to open custom build stream for " + buildName + ": " + e.getMessage());
            return null;
        }
    }

    public static int deleteCustomBuild(CommandSourceStack source, String buildName) {
        ServerPlayer player;
        try {
            player = source.getPlayer();
        }
        catch (Exception e) {
            source.sendFailure(Theme.lit(source, "You must be a player to delete custom builds!"));
            return 0;
        }
        if (!customBuilds.isCustomBuild(buildName)) {
            player.displayClientMessage(Theme.lit(player, ("\u00a7cCustom build '" + buildName + "' does not exist!")), false);
            return 0;
        }
        try {
            File schematicFile = new File(CUSTOM_BUILDS_FOLDER + buildName + ".schem");
            if (schematicFile.exists() && schematicFile.delete()) {
                customBuildNames.remove(buildName);
                player.displayClientMessage(Theme.lit(player, ("\u00a7aCustom build '" + buildName + "' deleted successfully!")), false);
                return 1;
            }
            player.displayClientMessage(Theme.lit(player, "\u00a7cFailed to delete custom build file!"), false);
            return 0;
        }
        catch (Exception e) {
            player.displayClientMessage(Theme.lit(player, ("\u00a7cFailed to delete custom build: " + e.getMessage())), false);
            return 0;
        }
    }

    public static int listCustomBuilds(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayer();
        }
        catch (Exception e) {
            source.sendFailure(Theme.lit(source, "You must be a player to list custom builds!"));
            return 0;
        }
        if (customBuildNames.isEmpty()) {
            player.displayClientMessage(Theme.lit(player, "\u00a7eNo custom builds found."), false);
            player.displayClientMessage(Theme.lit(player, "\u00a77Build something in the area " + BuildArea.rangeText(source.getServer()) + " \u00a77then use \u00a7a/pb create <name>"), false);
            return 0;
        }
        player.displayClientMessage(Theme.lit(player, ("\u00a76Custom Builds (" + customBuildNames.size() + "):")), false);
        for (String buildName : customBuildNames) {
            player.displayClientMessage(Theme.lit(player, ("\u00a77- \u00a7e" + buildName)), false);
        }
        return 1;
    }

    public static int showBuildArea(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayer();
        }
        catch (Exception e) {
            source.sendFailure(Theme.lit(source, "You must be a player to use this command!"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        player.displayClientMessage(Theme.lit(player, "\u00a7eBuild Area Information:"), false);
        player.displayClientMessage(Theme.lit(player, "\u00a77From: " + BuildArea.rangeText(server)), false);
        player.displayClientMessage(Theme.lit(player, "\u00a77Size: \u00a7e" + BuildArea.SIZE_XZ + "x" + BuildArea.HEIGHT + "x" + BuildArea.SIZE_XZ + " \u00a77blocks"), false);
        if (BuildArea.isSet(server)) {
            player.displayClientMessage(Theme.lit(player, "\u00a77Dimension: \u00a7e" + BuildArea.dimension(server)), false);
        } else {
            player.displayClientMessage(Theme.lit(player, "\u00a77No setup yet, this is the default area. Use \u00a7a/pb setup \u00a77to place one where you stand."), false);
        }
        player.displayClientMessage(Theme.lit(player, "\u00a77Build your structure in this area, then use \u00a7a/pb create <name> \u00a77to save it!"), false);
        return 1;
    }

    static {
        try {
            Path customBuildsPath = Paths.get(CUSTOM_BUILDS_FOLDER);
            Files.createDirectories(customBuildsPath);
            customBuilds.loadExistingCustomBuilds();
        }
        catch (IOException e) {
            SpeedBuilderMod.LOGGER.error("Failed to create custom builds directory: " + e.getMessage());
        }
    }
}

