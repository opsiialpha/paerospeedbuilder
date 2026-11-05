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
import java.io.InputStream;
import net.minecraft.commands.CommandSourceStack;
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
    public static int setupWorld(CommandSourceStack source) {
        ServerPlayer player;
        ServerLevel world = source.getLevel();
        try {
            player = source.getPlayer();
        }
        catch (Exception e) {
            source.sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }
        try {
            player.connection.teleport(0.0, -60.0, 0.0, player.getYRot(), player.getXRot());
            player.displayClientMessage(Component.literal("\u00a72Teleported to staging area"), false);
            player.displayClientMessage(Component.literal("\u00a72Cleared stone and cobblestone"), false);
            worldSetup.replaceNear(world, new BlockPos(0, -60, 0), 100, Blocks.STONE, Blocks.AIR);
            worldSetup.replaceNear(world, new BlockPos(0, -60, 0), 100, Blocks.COBBLESTONE, Blocks.AIR);
            player.displayClientMessage(Component.literal("\u00a72Placed setup schematic"), false);
            if (!worldSetup.placeSetupSchematic(world, BlockVector3.at(-100, 0, 100))) {
                player.displayClientMessage(Component.literal("\u00a7cFailed to place setup schematic!"), false);
                return 0;
            }
            player.connection.teleport(0.0, 0.0, 0.0, player.getYRot(), player.getXRot());
            player.displayClientMessage(Component.literal("\u00a7aSetup complete! The green terracotta and quartz can be replaced with whatever you want"), false);
            return 1;
        }
        catch (Exception e) {
            source.sendFailure(Component.literal(("Failed to setup world: " + e.getMessage())));
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * /sb area - builds a 15x15 platform in the ground under the player's feet:
     * outer ring cyan terracotta, next ring light gray concrete, the inside gray concrete and one
     * upright quartz pillar in the middle. The 7 blocks above the platform are cleared to air.
     */
    public static int buildPlatform(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayer();
        } catch (Exception e) {
            source.sendFailure(Component.literal("You must be a player to use this command!"));
            return 0;
        }
        if (player == null) {
            source.sendFailure(Component.literal("You must be a player to use this command!"));
            return 0;
        }
        ServerLevel world = player.serverLevel();
        BlockPos feet = player.blockPosition();
        int floorY = feet.getY() - 1;
        int cx = feet.getX();
        int cz = feet.getZ();
        BlockState outer = Blocks.CYAN_TERRACOTTA.defaultBlockState();
        BlockState ring = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        BlockState fill = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -7; dx <= 7; ++dx) {
            for (int dz = -7; dz <= 7; ++dz) {
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
                for (int dy = 1; dy <= 7; ++dy) {
                    world.setBlock(new BlockPos(cx + dx, floorY + dy, cz + dz), air, 3);
                }
            }
        }
        source.sendSuccess(() -> Component.literal("\u00a7aBuilt a 15x15 area under you \u00a77(7 blocks of air above it)"), false);
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

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private static boolean placeSetupSchematic(ServerLevel world, BlockVector3 pastePos) {
        String resourcePath = "/assets/paerospeedbuilder/builds/setup.schem";
        try (InputStream stream = worldSetup.class.getResourceAsStream(resourcePath);){
            if (stream == null) {
                SpeedBuilderMod.LOGGER.error("Could not find setup schematic at: " + resourcePath);
                boolean bl = false;
                return bl;
            }
            ClipboardFormat format = ClipboardFormats.findByAlias("schem");
            if (format == null) {
                SpeedBuilderMod.LOGGER.error("Schematic format 'schem' is not supported!");
                boolean bl = false;
                return bl;
            }
            ClipboardReader reader = format.getReader(stream);
            Clipboard clipboard = reader.read();
            com.sk89q.worldedit.world.World weWorld = NeoForgeAdapter.adapt(world);
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            Operation operation = holder.createPaste(weWorld).to(pastePos).ignoreAirBlocks(false).build();
            Operations.complete(operation);
            SpeedBuilderMod.LOGGER.info("[SpeedBuilder] Pasted setup schematic at " + String.valueOf(pastePos));
            boolean bl = true;
            return bl;
        }
        catch (Exception e) {
            SpeedBuilderMod.LOGGER.error("Failed to place setup schematic:");
            e.printStackTrace();
            return false;
        }
    }

    public static int replaceNearCommand(CommandSourceStack source, BlockPos center, int radius, String fromBlockId, String toBlockId) {
        ServerLevel world = source.getLevel();
        try {
            Block fromBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(fromBlockId));
            Block toBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(toBlockId));
            if (fromBlock == null || toBlock == null) {
                source.sendFailure(Component.literal(("Invalid block ID(s): " + fromBlockId + " or " + toBlockId)));
                return 0;
            }
            worldSetup.replaceNear(world, center, radius, fromBlock, toBlock);
            source.sendSuccess(() -> Component.literal(("\u00a7aReplaced " + fromBlockId + " with " + toBlockId + " within " + radius + " blocks")), false);
            return 1;
        }
        catch (Exception e) {
            source.sendFailure(Component.literal(("Failed to replace blocks: " + e.getMessage())));
            return 0;
        }
    }
}

