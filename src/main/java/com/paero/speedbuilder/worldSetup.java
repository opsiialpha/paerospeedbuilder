package com.paero.speedbuilder;

import com.mojang.brigadier.CommandDispatcher;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

public class worldSetup {
    private static final int HALF = 7;          // platform is 15x15 (-7..7 around the player)
    private static final int AIR_ABOVE = BuildArea.HEIGHT; // cleared above the platform: the whole build area

    /** /pb setup: builds the platform + build area where you stand. Refuses if a setup already exists. */
    public static int setupWorld(CommandSourceStack source) {
        return setup(source, false);
    }

    /** /pb resetup: removes the existing setup (if any) and builds a new one where you stand. */
    public static int resetup(CommandSourceStack source) {
        return setup(source, true);
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pb")
            .then(Commands.literal("resetup").executes(ctx -> worldSetup.resetup(ctx.getSource())))
            .then(Commands.literal("area").executes(ctx -> customBuilds.showBuildArea(ctx.getSource()))));
    }

    private static boolean isPlatformBlock(BlockState state) {
        return state.is(Blocks.CYAN_TERRACOTTA) || state.is(Blocks.LIGHT_GRAY_CONCRETE)
            || state.is(Blocks.GRAY_CONCRETE) || state.is(Blocks.QUARTZ_PILLAR);
    }

    private static ServerLevel levelFor(MinecraftServer server, String dimensionId, ServerLevel fallback) {
        ResourceLocation id = dimensionId == null || dimensionId.isEmpty() ? null : ResourceLocation.tryParse(dimensionId);
        if (id != null) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (level != null) {
                return level;
            }
        }
        return fallback;
    }

    /** Deletes the previous setup: the platform blocks it placed and everything inside its build area. */
    private static void removeExisting(MinecraftServer server, ServerLevel fallback) {
        ServerLevel old = levelFor(server, BuildArea.dimension(server), fallback);
        BlockPos min = BuildArea.minPos(server);
        int centerX = min.getX() + BuildArea.SIZE_XZ / 2;
        int centerZ = min.getZ() + BuildArea.SIZE_XZ / 2;
        int floorY = min.getY() - 1;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -HALF; dx <= HALF; ++dx) {
            for (int dz = -HALF; dz <= HALF; ++dz) {
                BlockPos floor = new BlockPos(centerX + dx, floorY, centerZ + dz);
                if (isPlatformBlock(old.getBlockState(floor))) {
                    old.setBlock(floor, air, 3);
                }
            }
        }
        for (int dx = 0; dx < BuildArea.SIZE_XZ; ++dx) {
            for (int dy = 0; dy <= BuildArea.HEIGHT; ++dy) {
                for (int dz = 0; dz < BuildArea.SIZE_XZ; ++dz) {
                    old.setBlock(min.offset(dx, dy, dz), air, 3);
                }
            }
        }
        buildLoader.clearDroppedItems(old, BlockVector3.at(0, 0, 0), BlockVector3.at(BuildArea.SIZE_XZ - 1, BuildArea.HEIGHT, BuildArea.SIZE_XZ - 1), BuildArea.origin(server));
    }

    /**
     * Builds a 15x15 platform in the ground under the player's feet:
     * outer ring cyan terracotta, next ring light gray concrete, the inside gray concrete and one
     * upright quartz pillar in the middle. The 16 blocks above the platform are cleared to air and the
     * 15x16x15 build area is placed on top of it (its bottom layer is the layer you stand in).
     */
    private static int setup(CommandSourceStack source, boolean replace) {
        ServerPlayer player;
        try {
            player = source.getPlayer();
        } catch (Exception e) {
            source.sendFailure(Theme.lit(source, "You must be a player to use this command!"));
            return 0;
        }
        if (player == null) {
            source.sendFailure(Theme.lit(source, "You must be a player to use this command!"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        ServerLevel world = player.serverLevel();
        boolean existed = BuildArea.isSet(server);
        if (existed && !replace) {
            MutableComponent message = Component.literal("\u00a7cA setup already exists. \u00a77Type ")
                .append(Component.literal("/pb resetup").withStyle(s -> s.withColor(ChatFormatting.YELLOW)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/pb resetup"))))
                .append(Component.literal(" \u00a77to delete it and create a new one where you are standing. Use \u00a7e/pb area \u00a77to see where it is."));
            source.sendFailure(Theme.wrap(source, message));
            return 0;
        }
        if (existed) {
            removeExisting(server, world);
        }

        BlockPos feet = player.blockPosition();
        int floorY = feet.getY() - 1;
        int cx = feet.getX();
        int cz = feet.getZ();
        BlockState outer = Blocks.CYAN_TERRACOTTA.defaultBlockState();
        BlockState ring = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        BlockState fill = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -HALF; dx <= HALF; ++dx) {
            for (int dz = -HALF; dz <= HALF; ++dz) {
                int edge = Math.max(Math.abs(dx), Math.abs(dz));
                BlockState floor;
                if (edge == 7) {
                    floor = outer;
                } else if (edge == 6) {
                    floor = ring;
                } else if (dx == 0 && dz == 0) {
                    floor = pillar;
                } else {
                    floor = fill;
                }
                world.setBlock(new BlockPos(cx + dx, floorY, cz + dz), floor, 3);
                for (int dy = 1; dy <= AIR_ABOVE; ++dy) {
                    world.setBlock(new BlockPos(cx + dx, floorY + dy, cz + dz), air, 3);
                }
            }
        }

        // The 15x15 build area sits exactly on top of the platform, centred on where the command was typed.
        BuildArea.set(server, world.dimension().location().toString(), new BlockPos(cx - BuildArea.SIZE_XZ / 2, floorY + 1, cz - BuildArea.SIZE_XZ / 2));

        // Anything still running was tied to the old area position.
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            buildLoader.cancelPlayerTasks(online);
            spbAccuracy.ACTIVE_SESSIONS.remove(online.getUUID());
        }

        String headline = existed
            ? "\u00a7aSetup replaced: the old setup was removed and a new 15x15 area was built under you"
            : "\u00a7aSetup complete: built a 15x15 area under you \u00a77(" + AIR_ABOVE + " blocks of air above it)";
        String area = "\u00a77Build area: " + BuildArea.rangeText(server);
        source.sendSuccess(() -> Theme.lit(source, headline), false);
        source.sendSuccess(() -> Theme.lit(source, area), false);
        return 1;
    }

    public static void replaceNear(ServerLevel world, BlockPos center, int radius, Block fromBlock, Block toBlock) {
        int replaced = 0;
        for (int x = center.getX() - radius; x <= center.getX() + radius; ++x) {
            for (int y = center.getY() - radius; y <= center.getY() + radius; ++y) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; ++z) {
                    BlockPos pos = new BlockPos(x, y, z);
                    double distance = Math.sqrt(Math.pow(x - center.getX(), 2.0) + Math.pow(y - center.getY(), 2.0) + Math.pow(z - center.getZ(), 2.0));
                    if (!(distance <= (double)radius) || world.getBlockState(pos).getBlock() != fromBlock) continue;
                    world.setBlock(pos, toBlock.defaultBlockState(), 3);
                    ++replaced;
                }
            }
        }
        SpeedBuilderMod.LOGGER.debug("[DEBUG] Replaced " + replaced + " blocks of " + String.valueOf(fromBlock) + " with " + String.valueOf(toBlock));
    }

    public static int replaceNearCommand(CommandSourceStack source, BlockPos center, int radius, String fromBlockId, String toBlockId) {
        ServerLevel world = source.getLevel();
        try {
            Block fromBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(fromBlockId));
            Block toBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(toBlockId));
            if (fromBlock == null || toBlock == null) {
                source.sendFailure(Theme.lit(source, ("Invalid block ID(s): " + fromBlockId + " or " + toBlockId)));
                return 0;
            }
            worldSetup.replaceNear(world, center, radius, fromBlock, toBlock);
            source.sendSuccess(() -> Theme.lit(source, ("\u00a7aReplaced " + fromBlockId + " with " + toBlockId + " within " + radius + " blocks")), false);
            return 1;
        }
        catch (Exception e) {
            source.sendFailure(Theme.lit(source, ("Failed to replace blocks: " + e.getMessage())));
            return 0;
        }
    }
}

