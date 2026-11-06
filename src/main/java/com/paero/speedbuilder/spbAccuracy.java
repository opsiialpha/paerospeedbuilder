package com.paero.speedbuilder;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

public class spbAccuracy {
    public static final Set<UUID> GRIND_ACCURACY_ENABLED = new HashSet<UUID>();
    public static final Map<UUID, BuildSession> ACTIVE_SESSIONS = new HashMap<UUID, BuildSession>();
    private static final Set<String> IGNORED_KEYS = Set.of("triggered", "powered", "lit");

    private static String filterBlockStateString(String blockStateStr) {
        if (!blockStateStr.contains("[")) {
            return blockStateStr;
        }
        String baseBlock = blockStateStr.substring(0, blockStateStr.indexOf(91));
        String propertiesStr = blockStateStr.substring(blockStateStr.indexOf(91) + 1, blockStateStr.lastIndexOf(93));
        if (propertiesStr.isEmpty()) {
            return baseBlock;
        }
        StringBuilder filteredProps = new StringBuilder();
        String[] props = propertiesStr.split(",");
        boolean first = true;
        for (String prop : props) {
            String[] keyValue = prop.split("=");
            if (keyValue.length != 2 || IGNORED_KEYS.contains(keyValue[0])) continue;
            if (!first) {
                filteredProps.append(",");
            }
            filteredProps.append(prop);
            first = false;
        }
        if (filteredProps.length() == 0) {
            return baseBlock;
        }
        return baseBlock + "[" + filteredProps.toString() + "]";
    }

    private static String formatTime(long ms) {
        int sec = (int)(ms / 1000L);
        int hund = (int)(ms % 1000L / 10L);
        return sec + "." + (hund < 10 ? "0" : "") + hund + "s";
    }

    public static int check(CommandSourceStack source, Clipboard clipboard, BlockVector3 min, BlockVector3 max, BlockVector3 worldOrigin) {
        ServerPlayer player;
        ServerLevel world = source.getLevel();
        try {
            player = source.getPlayer();
        }
        catch (Exception e) {
            source.sendFailure(Theme.lit(source, "You must be a player to check accuracy!"));
            return 0;
        }
        int total = 0;
        int correct = 0;
        for (int y = min.y(); y <= max.y(); ++y) {
            for (int x = min.x(); x <= max.x(); ++x) {
                for (int z = min.z(); z <= max.z(); ++z) {
                    BlockVector3 schemPos = BlockVector3.at((int)x, (int)y, (int)z);
                    BlockState expected = clipboard.getBlock(schemPos);
                    String expectedId = expected.getBlockType().getId();
                    if (expectedId.equals("minecraft:air")) continue;
                    BlockPos worldPos = new BlockPos(worldOrigin.x() + (x - min.x()), worldOrigin.y() + (y - min.y()), worldOrigin.z() + (z - min.z()));
                    net.minecraft.world.level.block.state.BlockState actual = world.getBlockState(worldPos);
                    if (spbAccuracy.withinBlockMatch(expectedId, expected, actual)) {
                        ++correct;
                    }
                    ++total;
                }
            }
        }
        double accuracy = total == 0 ? 0.0 : 100.0 * (double)correct / (double)total;
        player.displayClientMessage(Theme.lit(player, ("\u00a7eBuild Accuracy: \u00a7a" + String.format("%.1f", accuracy) + "% \u00a77(" + correct + "/" + total + ")")), false);
        return correct == total ? 1 : 0;
    }

    static boolean withinBlockMatch(String expectedId, BlockState expected, net.minecraft.world.level.block.state.BlockState actual) {
        String actualId = BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString();
        if (expectedId.equals("minecraft:dispenser") || expectedId.equals("minecraft:dropper") || expectedId.equals("minecraft:observer") || expectedId.equals("minecraft:hopper") || expectedId.equals("minecraft:pumpkin") || expectedId.equals("minecraft:carved_pumpkin") || expectedId.equals("minecraft:jack_o_lantern") || expectedId.equals("minecraft:chest") || expectedId.equals("minecraft:furnace") || expectedId.equals("minecraft:blast_furnace") || expectedId.equals("minecraft:smoker")) {
            if (!expectedId.equals(actualId)) {
                return false;
            }
            return StateMatch.matches(spbAccuracy.weProperties(expected), spbAccuracy.mcProperties(actual));
        }
        if (!expectedId.equals(actualId)) {
            return spbAccuracy.isGrassDirtMatch(expectedId, actualId);
        }
        if (expectedId.endsWith("_fence_gate")) {
            try {
                Direction actualFacing = actual.getValue(BlockStateProperties.HORIZONTAL_FACING);
                boolean actualOpen = actual.getValue(BlockStateProperties.OPEN);
                String expectedFacingStr = expected.getStates().entrySet().stream().filter(entry -> "facing".equals(entry.getKey().getName())).map(entry -> entry.getValue().toString()).findFirst().orElse(null);
                String expectedOpenStr = expected.getStates().entrySet().stream().filter(entry -> "open".equals(entry.getKey().getName())).map(entry -> entry.getValue().toString()).findFirst().orElse("false");
                if (expectedFacingStr != null) {
                    Direction expectedDirection = Direction.byName(expectedFacingStr);
                    boolean expectedOpen = "true".equals(expectedOpenStr);
                    if (expectedDirection != null && !actualFacing.equals(expectedDirection)) {
                        return false;
                    }
                    if (actualOpen != expectedOpen) {
                        return false;
                    }
                }
                return true;
            }
            catch (Exception e) {
                return false;
            }
        }
        if (expectedId.endsWith("_trapdoor")) {
            try {
                Direction actualFacing = actual.getValue(BlockStateProperties.HORIZONTAL_FACING);
                Half actualHalf = actual.getValue(BlockStateProperties.HALF);
                boolean actualOpen = actual.getValue(BlockStateProperties.OPEN);
                String expectedFacingStr = expected.getStates().entrySet().stream().filter(entry -> "facing".equals(entry.getKey().getName())).map(entry -> entry.getValue().toString()).findFirst().orElse(null);
                String expectedHalfStr = expected.getStates().entrySet().stream().filter(entry -> "half".equals(entry.getKey().getName())).map(entry -> entry.getValue().toString()).findFirst().orElse("bottom");
                String expectedOpenStr = expected.getStates().entrySet().stream().filter(entry -> "open".equals(entry.getKey().getName())).map(entry -> entry.getValue().toString()).findFirst().orElse("false");
                if (expectedFacingStr != null) {
                    Direction expectedDirection = Direction.byName(expectedFacingStr);
                    Half expectedHalf = "top".equals(expectedHalfStr) ? Half.TOP : Half.BOTTOM;
                    boolean expectedOpen = "true".equals(expectedOpenStr);
                    if (expectedDirection != null && !actualFacing.equals(expectedDirection)) {
                        SpeedBuilderMod.LOGGER.debug("[DEBUG] Trapdoor facing mismatch: expected " + String.valueOf(expectedDirection) + ", got " + String.valueOf(actualFacing));
                        return false;
                    }
                    if (!actualHalf.equals(expectedHalf)) {
                        SpeedBuilderMod.LOGGER.debug("[DEBUG] Trapdoor half mismatch: expected " + String.valueOf(expectedHalf) + ", got " + String.valueOf(actualHalf));
                        return false;
                    }
                    if (actualOpen != expectedOpen) {
                        SpeedBuilderMod.LOGGER.debug("[DEBUG] Trapdoor open state mismatch: expected " + expectedOpen + ", got " + actualOpen);
                        return false;
                    }
                }
                return true;
            }
            catch (Exception e) {
                SpeedBuilderMod.LOGGER.debug("[DEBUG] Error checking trapdoor properties: " + e.getMessage());
                return false;
            }
        }
        if (expectedId.endsWith("_leaves")) {
            return true;
        }
        if (expectedId.endsWith("_log") || expectedId.endsWith("_wood") || expectedId.contains("_log") && !expectedId.contains("slab")) {
            return true;
        }
        if (expectedId.endsWith("_chest")) {
            return true;
        }
        if (expectedId.equals("minecraft:brewing_stand")) {
            return true;
        }
        if (expectedId.equals("minecraft:grass_block") || expectedId.equals("minecraft:short_grass") || expectedId.equals("minecraft:tall_grass") || expectedId.equals("minecraft:fern") || expectedId.equals("minecraft:large_fern") || expectedId.equals("minecraft:rose_bush") || expectedId.equals("minecraft:peony") || expectedId.equals("minecraft:lilac") || expectedId.equals("minecraft:sunflower") || expectedId.equals("minecraft:poppy") || expectedId.equals("minecraft:dandelion") || expectedId.equals("minecraft:blue_orchid") || expectedId.equals("minecraft:allium") || expectedId.equals("minecraft:azure_bluet") || expectedId.equals("minecraft:red_tulip") || expectedId.equals("minecraft:orange_tulip") || expectedId.equals("minecraft:white_tulip") || expectedId.equals("minecraft:pink_tulip") || expectedId.equals("minecraft:oxeye_daisy") || expectedId.equals("minecraft:cornflower") || expectedId.equals("minecraft:lily_of_the_valley") || expectedId.equals("minecraft:wither_rose")) {
            return true;
        }
        if (expectedId.equals("minecraft:end_portal_frame")) {
            try {
                Direction expectedDirection;
                Direction actualFacing = actual.getValue(BlockStateProperties.HORIZONTAL_FACING);
                String expectedFacingStr = expected.getStates().entrySet().stream().filter(entry -> "facing".equals(entry.getKey().getName())).map(entry -> entry.getValue().toString()).findFirst().orElse(null);
                if (expectedFacingStr != null && (expectedDirection = Direction.byName(expectedFacingStr)) != null && !actualFacing.equals(expectedDirection)) {
                    SpeedBuilderMod.LOGGER.debug("[DEBUG] Direction mismatch for end_portal_frame: expected " + String.valueOf(expectedDirection) + ", got " + String.valueOf(actualFacing));
                    return false;
                }
                return true;
            }
            catch (Exception e) {
                SpeedBuilderMod.LOGGER.debug("[DEBUG] Error checking end portal frame properties: " + e.getMessage());
            }
        }
        if (expectedId.endsWith("_stairs")) {
            return spbAccuracy.matchStairsFlexible(expected, actual);
        }
        if (expectedId.endsWith("_slab")) {
            return true;
        }
        if (expectedId.equals("minecraft:rail") || expectedId.equals("minecraft:powered_rail") || expectedId.equals("minecraft:detector_rail") || expectedId.equals("minecraft:activator_rail")) {
            return true;
        }
        if (expectedId.endsWith("_wall")) {
            return true;
        }
        if (expectedId.contains("_bars") || expectedId.equals("minecraft:iron_bars")) {
            return true;
        }
        if (expectedId.endsWith("_fence") || expectedId.equals("minecraft:nether_brick_fence")) {
            return true;
        }
        if (expectedId.equals("minecraft:note_block")) {
            return true;
        }
        if (expectedId.endsWith("_pressure_plate")) {
            return true;
        }
        if (expectedId.endsWith("_glass_pane") || expectedId.equals("minecraft:glass_pane")) {
            return true;
        }
        if (expectedId.endsWith("_sign") || expectedId.endsWith("_wall_sign")) {
            return true;
        }
        if (expectedId.endsWith("_door")) {
            return true;
        }
        if (expectedId.equals("minecraft:water")) {
            return true;
        }
        if (expectedId.equals("minecraft:lava")) {
            return true;
        }
        if (spbAccuracy.isSkull(expectedId)) {
            return true;
        }
        if (spbAccuracy.isButtonOrLever(expectedId)) {
            return true;
        }
        if (expectedId.endsWith("_torch") || expectedId.endsWith("_wall_torch")) {
            return true;
        }
        return spbAccuracy.statesStrictEqual(expected, actual);
    }

    private static boolean matchStairsFlexible(BlockState expected, net.minecraft.world.level.block.state.BlockState actual) {
        try {
            boolean validRotation;
            String actualHalf = (actual.getValue(BlockStateProperties.HALF)).toString();
            String actualFacing = (actual.getValue(BlockStateProperties.HORIZONTAL_FACING)).toString();
            String expectedHalf = null;
            String expectedFacing = null;
            for (Map.Entry<com.sk89q.worldedit.registry.state.Property<?>, Object> entry : expected.getStates().entrySet()) {
                String propName = entry.getKey().getName();
                String propValue = entry.getValue().toString();
                if ("half".equals(propName)) {
                    expectedHalf = propValue;
                    continue;
                }
                if (!"facing".equals(propName)) continue;
                expectedFacing = propValue;
            }
            if (expectedHalf == null) {
                return true;
            }
            if (!Objects.equals(expectedHalf, actualHalf)) {
                return false;
            }
            return expectedFacing == null || actualFacing == null || (validRotation = spbAccuracy.isValidStairRotation(expectedFacing, actualFacing));
        }
        catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }

    private static boolean isValidStairRotation(String expected, String actual) {
        expected = expected.toLowerCase();
        actual = actual.toLowerCase();
        String[] rotations = new String[]{"north", "east", "south", "west"};
        int expectedIndex = -1;
        int actualIndex = -1;
        for (int i = 0; i < rotations.length; ++i) {
            if (rotations[i].equals(expected)) {
                expectedIndex = i;
            }
            if (!rotations[i].equals(actual)) continue;
            actualIndex = i;
        }
        if (expectedIndex == -1 || actualIndex == -1) {
            return expected.equals(actual);
        }
        int diff = Math.abs(expectedIndex - actualIndex);
        boolean isValid = (diff = Math.min(diff, 4 - diff)) <= 1;
        return isValid;
    }

    private static Map<String, String> parseBlockProperties(String blockString) {
        HashMap<String, String> properties = new HashMap<String, String>();
        int startBracket = blockString.indexOf(91);
        int endBracket = blockString.indexOf(93);
        if (startBracket != -1 && endBracket != -1) {
            String[] pairs;
            String propsStr = blockString.substring(startBracket + 1, endBracket);
            for (String pair : pairs = propsStr.split(",")) {
                String[] keyValue = pair.split("=");
                if (keyValue.length != 2) continue;
                properties.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return properties;
    }

    private static boolean isButtonOrLever(String id) {
        return id.endsWith("_button") || id.equals("minecraft:lever");
    }

    private static boolean statesStrictEqual(BlockState expected, net.minecraft.world.level.block.state.BlockState actual) {
        // Compared by property name/value, not as raw strings: WorldEdit and Minecraft list properties in a
        // different order and WorldEdit may use another letter case (facing=NORTH vs facing=north).
        return StateMatch.matches(spbAccuracy.weProperties(expected), spbAccuracy.mcProperties(actual));
    }

    private static Map<String, String> weProperties(BlockState state) {
        HashMap<String, String> props = new HashMap<String, String>();
        for (Map.Entry<com.sk89q.worldedit.registry.state.Property<?>, Object> entry : state.getStates().entrySet()) {
            props.put(entry.getKey().getName(), String.valueOf(entry.getValue()));
        }
        return props;
    }

    private static Map<String, String> mcProperties(net.minecraft.world.level.block.state.BlockState state) {
        HashMap<String, String> props = new HashMap<String, String>();
        for (Map.Entry<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            props.put(entry.getKey().getName(), String.valueOf(entry.getValue()));
        }
        return props;
    }

    /** Readable text for /pb finish debug. */
    static String describeExpected(BlockState state) {
        return spbAccuracy.weBlockStateToString(state);
    }

    /** Readable text for /pb finish debug. */
    static String describeActual(net.minecraft.world.level.block.state.BlockState state) {
        return spbAccuracy.mcBlockStateToString(state);
    }

    private static boolean isGrassDirtMatch(String expectedId, String actualId) {
        Set<String> grassTypes = Set.of("minecraft:grass_block");
        if (grassTypes.contains(expectedId) && actualId.equals("minecraft:dirt")) {
            return true;
        }
        return grassTypes.contains(expectedId) && grassTypes.contains(actualId);
    }

    private static boolean isSkull(String id) {
        return id.equals("minecraft:skeleton_skull");
    }

    private static String weBlockStateToString(BlockState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(state.getBlockType().getId());
        if (!state.getStates().isEmpty()) {
            sb.append("[");
            boolean first = true;
            for (Map.Entry<com.sk89q.worldedit.registry.state.Property<?>, Object> entry : state.getStates().entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }
            sb.append("]");
        }
        return sb.toString();
    }

    private static String mcBlockStateToString(net.minecraft.world.level.block.state.BlockState state) {
        StringBuilder sb = new StringBuilder();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        sb.append(id.toString());
        if (!state.getProperties().isEmpty()) {
            sb.append("[");
            boolean first = true;
            for (Map.Entry<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(entry.getKey().getName()).append("=").append(entry.getValue());
            }
            sb.append("]");
        }
        return sb.toString();
    }

    public static void checkAuto(ServerPlayer player, ServerLevel world) {
        BuildSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (session.isGrindSession && !GRIND_ACCURACY_ENABLED.contains(player.getUUID())) {
            return;
        }
        Clipboard clipboard = session.clipboard;
        BlockVector3 min = session.min;
        BlockVector3 max = session.max;
        BlockVector3 origin = FinishScan.resolve(world, session);
        int total = 0;
        int correct = 0;
        for (int y = min.y(); y <= max.y(); ++y) {
            for (int x = min.x(); x <= max.x(); ++x) {
                for (int z = min.z(); z <= max.z(); ++z) {
                    BlockVector3 schemPos = BlockVector3.at((int)x, (int)y, (int)z);
                    BlockState expected = clipboard.getBlock(schemPos);
                    String expectedId = expected.getBlockType().getId();
                    if (expectedId.equals("minecraft:air")) continue;
                    ++total;
                    BlockPos worldPos = new BlockPos(origin.x() + (x - min.x()), origin.y() + (y - min.y()), origin.z() + (z - min.z()));
                    net.minecraft.world.level.block.state.BlockState actual = world.getBlockState(worldPos);
                    if (!spbAccuracy.withinBlockMatch(expectedId, expected, actual)) continue;
                    ++correct;
                }
            }
        }
        if (total > 0) {
            double accuracy = 100.0 * (double)correct / (double)total;
            if (correct == total) {
                boolean isNewBest;
                if (session.isCompleted) {
                    return;
                }
                session.isCompleted = true;
                long finish = System.currentTimeMillis();
                long elapsedMs = Math.max(0L, finish - session.startTime);
                int seconds = (int)(elapsedMs / 1000L);
                int millisRemainder = (int)(elapsedMs % 1000L);
                String buildName = session.buildName;
                UUID uuid = player.getUUID();
                Long prevBest = SpeedBuilderConfig.getBestTime(uuid, buildName);
                boolean bl = isNewBest = prevBest == null || elapsedMs < prevBest;
                if (isNewBest) {
                    SpeedBuilderConfig.setBestTime(uuid, buildName, elapsedMs);
                }
                if (session.isGrindSession) {
                    int sessionCount = grindMode.incrementSessionCount(uuid);
                    player.displayClientMessage(Theme.lit(player, ("\u00a7aBuild complete! \u00a7eAccuracy: \u00a7a100.0% (" + correct + "/" + total + ") \u00a7bTime: \u00a7f" + (seconds > 0 ? seconds + "." + String.format("%03d", millisRemainder).substring(0, 2) + "s" : elapsedMs + "ms") + " \u00a7a\u2714" + (String)(isNewBest ? " \u00a76(Personal Best!)" : (prevBest != null ? " \u00a77(Best: " + spbAccuracy.formatTime(prevBest) + ")" : "")) + " \u00a7d| Times completed: " + sessionCount)), false);
                } else {
                    player.displayClientMessage(Theme.lit(player, ("\u00a7aBuild complete! \u00a7eAccuracy: \u00a7a100.0% (" + correct + "/" + total + ") \u00a7bTime: \u00a7f" + (seconds > 0 ? seconds + "." + String.format("%03d", millisRemainder).substring(0, 2) + "s" : elapsedMs + "ms") + " \u00a7a\u2714" + (String)(isNewBest ? " \u00a76(Personal Best!)" : (prevBest != null ? " \u00a77(Best: " + spbAccuracy.formatTime(prevBest) + ")" : "")))), false);
                }
                FinishDisplay.show(player, elapsedMs);
                Sfx.buildFinish(player);
                if (!session.isGrindSession && buildMode.isInBuildMode(player)) {
                    // Build finished -> leave build mode (grind mode stays in it until /pb grind stop)
                    buildMode.disable(player);
                }
                player.displayClientMessage(Theme.lit(player, "\u00a7eArea will reset in 3 seconds..."), false);
                buildLoader.schedule(player.getServer(), 60, () -> {
                    resetBuild.hardReset(player.getServer().createCommandSourceStack().withPermission(2));
                    if (session.isGrindSession && grindMode.isGrinding(player)) {
                        buildLoader.schedule(player.getServer(), 5, () -> grindMode.continueGrindSession(player, session.buildName), player.getUUID().toString());
                        return;
                    }
                    ACTIVE_SESSIONS.remove(player.getUUID());
                }, player.getUUID().toString());
            }
        }
    }

    public static class BuildSession {
        public final Clipboard clipboard;
        public final BlockVector3 min;
        public final BlockVector3 max;
        public final BlockVector3 worldOrigin;
        public final long startTime;
        public final String buildName;
        public final boolean isGrindSession;
        public boolean isCompleted = false;

        public BuildSession(Clipboard clipboard, BlockVector3 min, BlockVector3 max, BlockVector3 worldOrigin, long startTime, String buildName, boolean isGrindSession) {
            this.clipboard = clipboard;
            this.min = min;
            this.max = max;
            this.worldOrigin = worldOrigin;
            this.startTime = startTime;
            this.buildName = buildName;
            this.isGrindSession = isGrindSession;
        }
    }
}

