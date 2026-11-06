package com.paero.speedbuilder;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockState;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class buildLoader {
    private static final Set<UUID> PLAYERS_RECEIVING_BLOCKS = new HashSet<>();
    private static final PriorityQueue<ScheduledTask> queue = new PriorityQueue<>();
    // Players currently in the preview/countdown phase (blocks they break give nothing back).
    private static final Set<UUID> PREVIEWING = new HashSet<>();

    public static boolean isPreviewing(ServerPlayer player) {
        return PREVIEWING.contains(player.getUUID());
    }

    private static InputStream getBuildStream(String buildName) {
        InputStream customStream = customBuilds.getCustomBuildStream(buildName);
        if (customStream != null) {
            return customStream;
        }
        return buildLoader.class.getResourceAsStream("/assets/paerospeedbuilder/builds/" + buildName + ".schem");
    }

    public static void schedule(MinecraftServer server, int ticksFromNow, Runnable action, String ownerId) {
        queue.add(new ScheduledTask(server.getTickCount() + ticksFromNow, action, ownerId));
    }

    public static void schedule(MinecraftServer server, int ticksFromNow, Runnable action) {
        schedule(server, ticksFromNow, action, null);
    }

    public static void registerScheduler() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> {
            MinecraftServer server = event.getServer();
            while (!queue.isEmpty() && queue.peek().targetTick <= (long) server.getTickCount()) {
                ScheduledTask task = queue.poll();
                if (task.cancelled) continue;
                task.action.run();
            }
        });
    }

    public static void cancelScheduledTasks(String ownerId) {
        synchronized (queue) {
            for (ScheduledTask task : queue) {
                if (!ownerId.equals(task.ownerId)) continue;
                task.cancelled = true;
            }
        }
    }

    public static void cancelPlayerTasks(ServerPlayer player) {
        PREVIEWING.remove(player.getUUID());
        cancelScheduledTasks(player.getUUID().toString());
    }

    public static void cancelAllTasks() {
        synchronized (queue) {
            for (ScheduledTask task : queue) {
                task.cancelled = true;
            }
        }
    }

    public static void registerAccuracyPoller() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            MinecraftServer server = event.getServer();
            for (UUID uuid : new ArrayList<>(spbAccuracy.ACTIVE_SESSIONS.keySet())) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player == null) continue;
                spbAccuracy.BuildSession session = spbAccuracy.ACTIVE_SESSIONS.get(uuid);
                if (session != null && !session.isCompleted) {
                    long elapsed = Math.max(0L, System.currentTimeMillis() - session.startTime);
                    player.displayClientMessage(FinishDisplay.timerComponent(uuid, FinishDisplay.formatTime(elapsed)), true);
                }
                spbAccuracy.checkAuto(player, player.serverLevel());
            }
        });
    }

    private static Clipboard readClipboard(InputStream stream) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByAlias("schem");
        if (format == null) {
            throw new IllegalStateException("Schematic format 'schem' is not supported!");
        }
        ClipboardReader reader = format.getReader(stream);
        return reader.read();
    }

    public static int placeSchematic(CommandSourceStack source, String buildName) {
        ServerLevel world = source.getLevel();
        try (InputStream stream = getBuildStream(buildName)) {
            if (stream == null) {
                source.sendFailure(Theme.lit(source, "Could not find schematic: " + buildName));
                return 0;
            }
            ClipboardFormat format = ClipboardFormats.findByAlias("schem");
            if (format == null) {
                source.sendFailure(Theme.lit(source, "Schematic format 'schem' is not supported!"));
                return 0;
            }
            Clipboard clipboard = format.getReader(stream).read();
            BlockVector3 pastePos = BlockVector3.at(-3, 0, 3);
            com.sk89q.worldedit.world.World weWorld = NeoForgeAdapter.adapt(world);
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            Operation operation = holder.createPaste(weWorld).to(pastePos).ignoreAirBlocks(false).build();
            Operations.complete(operation);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Theme.lit(source, "Failed to place build: " + buildName));
            e.printStackTrace();
            return 0;
        }
    }

    private static String stateProperty(BlockState state, String name, String fallback) {
        return state.getStates().entrySet().stream()
            .filter(entry -> name.equals(entry.getKey().getName()))
            .map(entry -> entry.getValue().toString())
            .findFirst().orElse(fallback);
    }

    private static void addCount(Map<String, Integer> counts, String key, int amount) {
        counts.put(key, counts.getOrDefault(key, 0) + amount);
    }

    /** Counts blocks in a clipboard as speed builders usually do (doors, slabs, fluids, buttons...). */
    private static Map<String, Integer> countBlocks(Clipboard clipboard, boolean includeTallPlants) {
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        Map<String, Integer> blockCounts = new HashMap<>();
        for (int y = min.y(); y <= max.y(); ++y) {
            for (int x = min.x(); x <= max.x(); ++x) {
                for (int z = min.z(); z <= max.z(); ++z) {
                    BlockVector3 pos = BlockVector3.at(x, y, z);
                    BlockState state = clipboard.getBlock(pos);
                    String id = state.getBlockType().getId();
                    if (id.equals("minecraft:air")) continue;
                    if (id.endsWith("_door")) {
                        String half = stateProperty(state, "half", null);
                        if ("lower".equals(half)) addCount(blockCounts, id, 1);
                        continue;
                    }
                    if (id.endsWith("_slab")) {
                        String slabType = stateProperty(state, "type", "bottom");
                        addCount(blockCounts, id, "double".equals(slabType) ? 2 : 1);
                        continue;
                    }
                    if (id.equals("minecraft:water")) {
                        addCount(blockCounts, "water_bucket", 1);
                        continue;
                    }
                    if (id.equals("minecraft:lava")) {
                        addCount(blockCounts, "lava_bucket", 1);
                        continue;
                    }
                    if (id.equals("minecraft:lever") || id.endsWith("_button")) {
                        String key = id + "_" + pos.x() + "_" + pos.y() + "_" + pos.z();
                        if (blockCounts.containsKey(key)) continue;
                        addCount(blockCounts, id, 1);
                        blockCounts.put(key, 1);
                        continue;
                    }
                    if (includeTallPlants && (id.equals("minecraft:tall_grass") || id.equals("minecraft:rose_bush") || id.equals("minecraft:peony") || id.equals("minecraft:lilac") || id.equals("minecraft:sunflower"))) {
                        String half = stateProperty(state, "half", null);
                        if ("lower".equals(half)) addCount(blockCounts, id, 1);
                        continue;
                    }
                    addCount(blockCounts, id, 1);
                }
            }
        }
        blockCounts.entrySet().removeIf(entry -> {
            String k = entry.getKey();
            if (k.contains("_button_") || k.contains("_lever_")) return true;
            return includeTallPlants && (k.contains("_tall_grass_") || k.contains("_rose_bush_") || k.contains("_peony_") || k.contains("_lilac_") || k.contains("_sunflower_"));
        });
        return blockCounts;
    }

    private static void giveStacks(ServerPlayer player, ItemStack stackToGive, int count) {
        int toGive;
        for (int remaining = count; remaining > 0; remaining -= toGive) {
            toGive = Math.min(remaining, stackToGive.getMaxStackSize());
            ItemStack stack = stackToGive.copy();
            stack.setCount(toGive);
            boolean inserted = player.getInventory().add(stack);
            if (inserted) continue;
            player.drop(stack, false);
        }
    }

    private static ItemStack stackFor(String blockId) {
        if (blockId.equals("water_bucket")) return new ItemStack(Items.WATER_BUCKET, 1);
        if (blockId.equals("lava_bucket")) return new ItemStack(Items.LAVA_BUCKET, 1);
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(blockId));
        if (block == null || block == Blocks.AIR) return null;
        return new ItemStack(block, 1);
    }

    /** Gives every non-fluid block (shuffled) first, then buckets last. */
    private static void giveCounted(ServerPlayer player, Map<String, Integer> blockCounts) {
        List<Map.Entry<String, Integer>> regular = new ArrayList<>();
        List<Map.Entry<String, Integer>> fluids = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
            String id = entry.getKey();
            if (id.equals("water_bucket") || id.equals("lava_bucket")) fluids.add(entry);
            else regular.add(entry);
        }
        Collections.shuffle(regular);
        List<Map.Entry<String, Integer>> all = new ArrayList<>(regular);
        all.addAll(fluids);
        for (Map.Entry<String, Integer> entry : all) {
            ItemStack stackToGive = stackFor(entry.getKey());
            if (stackToGive == null) continue;
            giveStacks(player, stackToGive, entry.getValue());
        }
    }

    /** Clears the inventory unless the player is on the Paero difficulty (inventory is never touched there). */
    public static void clearInventory(ServerPlayer player) {
        if (SpeedBuilderConfig.isPaero(player.getUUID())) {
            return;
        }
        player.getInventory().clearContent();
    }

    public static void giveBlocks(ServerPlayer player, String buildName) {
        if (SpeedBuilderConfig.isPaero(player.getUUID())) {
            return;
        }
        if (setHotbars.hasSpecificHotbar(buildName)) {
            setHotbars.giveSpecificHotbar(player, buildName);
            return;
        }
        try (InputStream stream = getBuildStream(buildName)) {
            if (stream == null) {
                player.displayClientMessage(Theme.lit(player, "\u00a7cCould not find schematic for block giving: " + buildName), false);
                return;
            }
            Clipboard clipboard = readClipboard(stream);
            giveCounted(player, countBlocks(clipboard, true));
        } catch (Exception e) {
            player.displayClientMessage(Theme.lit(player, "\u00a7cFailed to give required blocks for: " + buildName), false);
            e.printStackTrace();
        }
    }

    public static int previewSchematic(CommandSourceStack source, String buildName) {
        ServerLevel world = source.getLevel();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Theme.lit(source, "Must be run by a player"));
            return 0;
        }
        String playerId = player.getUUID().toString();
        cancelPlayerTasks(player);
        spbAccuracy.ACTIVE_SESSIONS.remove(player.getUUID());
        try (InputStream stream = getBuildStream(buildName)) {
            if (stream == null) {
                source.sendFailure(Theme.lit(source, "Could not find schematic: " + buildName));
                return 0;
            }
            PREVIEWING.add(player.getUUID());
            final Clipboard clipboard = readClipboard(stream);
            final BlockVector3 min = clipboard.getMinimumPoint();
            final BlockVector3 max = clipboard.getMaximumPoint();
            final BlockVector3 worldOrigin = BlockVector3.at(-3, 0, -3);
            MinecraftServer server = world.getServer();
            // Only animate the layers that really contain blocks: empty (air) layers at the bottom or top
            // of the schematic region must not count, otherwise small builds appear in a blink.
            int firstY = Integer.MAX_VALUE;
            int lastY = Integer.MIN_VALUE;
            for (int y = min.y(); y <= max.y(); ++y) {
                boolean hasBlock = false;
                for (int x = min.x(); x <= max.x() && !hasBlock; ++x) {
                    for (int z = min.z(); z <= max.z(); ++z) {
                        if (!NeoForgeAdapter.adapt(clipboard.getBlock(BlockVector3.at(x, y, z))).isAir()) {
                            hasBlock = true;
                            break;
                        }
                    }
                }
                if (hasBlock) {
                    firstY = Math.min(firstY, y);
                    lastY = Math.max(lastY, y);
                }
            }
            if (firstY > lastY) {
                firstY = min.y();
                lastY = max.y();
            }
            final int animMinY = firstY;
            final int animMaxY = lastY;
            final int layers = animMaxY - animMinY + 1;
            // Build-up animation always takes 3 seconds (60 ticks) in total, spread over the real height of the build.
            final int buildUpDuration = 60;
            final double buildUpStep = (double) buildUpDuration / (double) layers;
            int previewTimeSeconds = SpeedBuilderConfig.getPreviewTime(player.getUUID());
            int holdDuration = previewTimeSeconds == 0 ? 20 : previewTimeSeconds * 20;
            int countdownStart = buildUpDuration + holdDuration;
            // Disappear animation covers the last 2 seconds: it starts when the "2" shows and ends at BUILD!.
            int teardownStart = countdownStart + 60;
            int blocksGiveTime = countdownStart + 100;

            for (int y = animMinY; y <= animMaxY; ++y) {
                int localY = y - animMinY;
                int delayTicks = (int) Math.floor(localY * buildUpStep);
                final int py = y;
                schedule(server, delayTicks, () -> {
                    for (int x = min.x(); x <= max.x(); ++x) {
                        for (int z = min.z(); z <= max.z(); ++z) {
                            BlockState weBlockState = clipboard.getBlock(BlockVector3.at(x, py, z));
                            BlockPos mcPos = new BlockPos(worldOrigin.x() + (x - min.x()), worldOrigin.y() + (py - min.y()), worldOrigin.z() + (z - min.z()));
                            world.setBlock(mcPos, NeoForgeAdapter.adapt(weBlockState), 3);
                        }
                    }
                }, playerId);
            }

            // Chat countdown during the pause (last 5 seconds before the on-screen countdown)
            for (int n = 5; n >= 1; --n) {
                int chatTick = countdownStart - n * 20;
                if (chatTick < 0) continue;
                final int secondsLeft = n;
                schedule(server, chatTick, () -> {
                    String color = Theme.countdownCode(secondsLeft);
                    player.displayClientMessage(Theme.lit(player, "\u00a7eStarting in " + color + secondsLeft + "\u00a7e..."), false);
                    Sfx.chatTick(player);
                }, playerId);
            }

            int[] countdownTicks = new int[]{0, 20, 40, 60, 80, 100};
            for (int i = 0; i < countdownTicks.length; ++i) {
                final int number = 5 - i; // 5, 4, 3, 2, 1, 0 (0 = BUILD!)
                final boolean isViewOver = number == 0;
                schedule(server, countdownStart + countdownTicks[i], () -> {
                    player.connection.send(new ClientboundSetTitlesAnimationPacket(10, isViewOver ? 20 : 15, isViewOver ? 10 : 5));
                    player.connection.send(new ClientboundSetTitleTextPacket(isViewOver ? Theme.buildTitle(player) : Theme.countdownTitle(player, number)));
                    if (isViewOver) {
                        Sfx.buildStart(player);
                    } else {
                        Sfx.countdownTick(player, number == 1);
                    }
                }, playerId);
            }

            // 2 -> 0 is 40 ticks; spread the layer removal over the first 38 of them so it is done right at BUILD!
            double teardownSpan = 38.0;
            for (int i = 0; i < layers; ++i) {
                final int py = animMaxY - i;
                int delayTicks = teardownStart + (layers == 1 ? (int) teardownSpan : (int) Math.round((double) i * teardownSpan / (double) (layers - 1)));
                schedule(server, delayTicks, () -> {
                    for (int x = min.x(); x <= max.x(); ++x) {
                        for (int z = min.z(); z <= max.z(); ++z) {
                            BlockPos mcPos = new BlockPos(worldOrigin.x() + (x - min.x()), worldOrigin.y() + (py - min.y()), worldOrigin.z() + (z - min.z()));
                            world.setBlock(mcPos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }, playerId);
            }

            schedule(server, blocksGiveTime, () -> {
                ServerPlayer currentPlayer = source.getPlayer();
                if (currentPlayer == null) {
                    return;
                }
                if (PLAYERS_RECEIVING_BLOCKS.contains(currentPlayer.getUUID())) {
                    return;
                }
                PLAYERS_RECEIVING_BLOCKS.add(currentPlayer.getUUID());
                PREVIEWING.remove(currentPlayer.getUUID());
                clearInventory(currentPlayer);
                try {
                    if (SpeedBuilderConfig.isPaero(currentPlayer.getUUID())) {
                        // Paero difficulty: inventory stays exactly as it is
                    } else if (setHotbars.hasSpecificHotbar(buildName)) {
                        setHotbars.giveSpecificHotbar(currentPlayer, buildName);
                    } else {
                        Map<String, Integer> counts = countBlocks(clipboard, false);
                        giveCounted(currentPlayer, counts);
                        long now = System.currentTimeMillis();
                        spbAccuracy.ACTIVE_SESSIONS.put(currentPlayer.getUUID(), new spbAccuracy.BuildSession(clipboard, min, max, worldOrigin, now, buildName, false));
                        currentPlayer.displayClientMessage(Theme.lit(currentPlayer, "\u00a7aYou have been given blocks!"), false);
                    }
                    if (SpeedBuilderConfig.isPaero(currentPlayer.getUUID())) {
                        currentPlayer.displayClientMessage(Theme.lit(currentPlayer, "\u00a7aGo build!"), false);
                    }
                    long now = System.currentTimeMillis();
                    spbAccuracy.ACTIVE_SESSIONS.put(currentPlayer.getUUID(), new spbAccuracy.BuildSession(clipboard, min, max, worldOrigin, now, buildName, false));
                } finally {
                    PLAYERS_RECEIVING_BLOCKS.remove(currentPlayer.getUUID());
                }
            }, playerId);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Theme.lit(source, "Failed to preview build: " + buildName));
            e.printStackTrace();
            return 0;
        }
    }

    public static void clearDroppedItems(ServerLevel world, BlockVector3 min, BlockVector3 max, BlockVector3 origin) {
        int minX = origin.x();
        int maxX = origin.x() + (max.x() - min.x());
        int minY = origin.y();
        int maxY = origin.y() + (max.y() - min.y());
        int minZ = origin.z();
        int maxZ = origin.z() + (max.z() - min.z());
        world.getEntitiesOfClass(ItemEntity.class, new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1), entity -> true).forEach(ItemEntity::discard);
    }

    public static void previewSchematicWithCallback(ServerPlayer player, String buildName, Runnable afterBlocksGiven) {
        if (afterBlocksGiven != null) {
            afterBlocksGiven.run();
        }
    }

    public static void clearBuildArea(ServerLevel world, BlockVector3 min, BlockVector3 max, BlockVector3 origin) {
        for (int y = min.y(); y <= max.y(); ++y) {
            for (int x = min.x(); x <= max.x(); ++x) {
                for (int z = min.z(); z <= max.z(); ++z) {
                    BlockPos blockPos = new BlockPos(origin.x() + (x - min.x()), origin.y() + (y - min.y()), origin.z() + (z - min.z()));
                    world.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        world.getServer().execute(() -> clearDroppedItems(world, min, max, origin));
    }

    static class ScheduledTask implements Comparable<ScheduledTask> {
        final long targetTick;
        final Runnable action;
        final String ownerId;
        boolean cancelled = false;

        ScheduledTask(long targetTick, Runnable action, String ownerId) {
            this.targetTick = targetTick;
            this.action = action;
            this.ownerId = ownerId;
        }

        @Override
        public int compareTo(ScheduledTask o) {
            return Long.compare(this.targetTick, o.targetTick);
        }
    }
}
