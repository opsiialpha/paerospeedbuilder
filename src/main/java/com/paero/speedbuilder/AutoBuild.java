package com.paero.speedbuilder;

import com.sk89q.worldedit.math.BlockVector3;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Helpers that look at the build the player is working on:
 *
 * - SuperEasy auto place: right-click the face of a block and the block the build needs at the spot next to it is
 *   placed for you, switching your hotbar to that block first (or pulling it out of your inventory, like a middle click).
 * - Auto Orient: a block that was just placed is turned to the facing / half / axis the build needs.
 *
 * Both work at the spot where the build preview was shown (the session's world origin).
 */
public final class AutoBuild {
    /** Properties that describe how a block is turned. */
    private static final Set<String> ORIENT_PROPERTIES = Set.of("facing", "half", "axis", "rotation", "face", "type");

    private AutoBuild() {
    }

    // ---------------------------------------------------------------- build lookup

    /** The running build of this player, or null when there is none (or it is just being previewed / is finished). */
    private static spbAccuracy.BuildSession activeSession(ServerPlayer player) {
        if (buildLoader.isPreviewing(player)) {
            return null;
        }
        spbAccuracy.BuildSession session = spbAccuracy.ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null || session.isCompleted) {
            return null;
        }
        return session;
    }

    /** The block the build wants at this world position, or null when it wants air there (or the spot is outside the build). */
    private static com.sk89q.worldedit.world.block.BlockState expectedAt(spbAccuracy.BuildSession session, BlockPos pos) {
        BlockVector3 origin = session.worldOrigin;
        int sx = session.min.x() + (pos.getX() - origin.x());
        int sy = session.min.y() + (pos.getY() - origin.y());
        int sz = session.min.z() + (pos.getZ() - origin.z());
        if (sx < session.min.x() || sx > session.max.x() || sy < session.min.y() || sy > session.max.y() || sz < session.min.z() || sz > session.max.z()) {
            return null;
        }
        com.sk89q.worldedit.world.block.BlockState expected = session.clipboard.getBlock(BlockVector3.at(sx, sy, sz));
        return expected.getBlockType().getId().equals("minecraft:air") ? null : expected;
    }

    private static Block blockOf(com.sk89q.worldedit.world.block.BlockState expected) {
        ResourceLocation id = ResourceLocation.tryParse(expected.getBlockType().getId());
        return id == null ? null : BuiltInRegistries.BLOCK.get(id);
    }

    /** True when the build wants something at this spot that is not there yet (an air check, not a loose match). */
    private static boolean isPending(com.sk89q.worldedit.world.block.BlockState expected, BlockState actual) {
        String expectedId = expected.getBlockType().getId();
        if (expectedId.endsWith("_slab")) {
            // Slabs are matched loosely by the finish check, but a double slab really needs a second slab.
            String expectedType = spbAccuracy.weProperties(expected).get("type");
            if (actual.isAir()) {
                return true;
            }
            if ("double".equalsIgnoreCase(expectedType) && actual.hasProperty(BlockStateProperties.SLAB_TYPE)
                && actual.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE
                && BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString().equals(expectedId)) {
                return true;
            }
        }
        return !spbAccuracy.withinBlockMatch(expectedId, expected, actual);
    }

    // ---------------------------------------------------------------- SuperEasy auto place

    /**
     * Tries to place the block the build needs where the player right-clicked.
     *
     * @return null when nothing was done (the click is handled normally), otherwise the result the click must end with
     */
    public static InteractionResult tryAutoPlace(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
        spbAccuracy.BuildSession session = activeSession(player);
        if (session == null || player.isSpectator()) {
            return null;
        }
        BlockPos clicked = hit.getBlockPos();
        // Only a real block can be clicked on: nothing is ever placed in mid-air.
        if (level.getBlockState(clicked).isAir()) {
            return null;
        }
        // The spot is decided by the clicked block and the face that was clicked, nothing else, so a block inside the
        // player's own head or feet is never picked by accident.
        BlockPos adjacent = clicked.relative(hit.getDirection());
        BlockPos[] candidates = new BlockPos[]{adjacent, clicked};

        Item missing = null;
        boolean blocked = false;
        for (BlockPos spot : candidates) {
            com.sk89q.worldedit.world.block.BlockState expected = expectedAt(session, spot);
            if (expected == null || !isPending(expected, level.getBlockState(spot))) {
                continue;
            }
            // The clicked block itself is only a target when a slab there needs its second half.
            if (spot.equals(clicked) && !expected.getBlockType().getId().endsWith("_slab")) {
                continue;
            }
            Block block = blockOf(expected);
            if (block == null) {
                continue;
            }
            Item item = block.asItem();
            if (!(item instanceof BlockItem blockItem)) {
                continue;
            }
            int slot = findSlot(player.getInventory(), item);
            if (slot < 0) {
                missing = item;
                continue;
            }
            ItemStack probeStack = player.getInventory().getItem(slot).copyWithCount(1);
            BlockPlaceContext probe = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, probeStack, hit);
            if (!probe.getClickedPos().equals(spot)) {
                continue; // vanilla would put this block somewhere else for this click
            }
            if (!BuildArea.contains(level.getServer(), spot) || !probe.canPlace()) {
                blocked = true;
                continue;
            }
            selectSlot(player, slot);
            // A copy is placed, so the stack in the inventory is never used up.
            ItemStack placeStack = player.getInventory().getItem(player.getInventory().selected).copyWithCount(1);
            BlockPlaceContext context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, placeStack, hit);
            InteractionResult result = blockItem.place(context);
            if (result.consumesAction()) {
                orient(level, spot, session);
                // The client already "used up" a block while predicting the click; send the real inventory back.
                player.inventoryMenu.sendAllDataToRemote();
                return InteractionResult.SUCCESS;
            }
            blocked = true;
        }
        if (missing != null) {
            player.displayClientMessage(Theme.lit(player, "\u00a7cYou don't have " + missing.getDescription().getString() + " in your inventory!"), true);
            return InteractionResult.FAIL;
        }
        return blocked ? InteractionResult.FAIL : null;
    }

    /** Hotbar / inventory slot holding the item: the selected slot first, then the hotbar, then the rest of the inventory. */
    private static int findSlot(Inventory inventory, Item item) {
        ItemStack selected = inventory.getItem(inventory.selected);
        if (!selected.isEmpty() && selected.is(item)) {
            return inventory.selected;
        }
        for (int i = 0; i < 36; ++i) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                return i;
            }
        }
        return -1;
    }

    /** Switches the hotbar to the slot; a block from the main inventory is brought to the hotbar like a middle click does. */
    private static void selectSlot(ServerPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        if (slot == inventory.selected) {
            return;
        }
        if (slot < 9) {
            inventory.selected = slot;
        } else {
            inventory.pickSlot(slot);
        }
        player.connection.send(new ClientboundSetCarriedItemPacket(inventory.selected));
        player.inventoryMenu.broadcastChanges();
    }

    // ---------------------------------------------------------------- Auto Orient

    /**
     * Auto Orient for normal placing: after the click has been handled, any block that just appeared at the
     * target (or clicked) spot is turned to the way the build needs it.
     */
    public static void orientAfterPlacement(ServerPlayer player, ServerLevel level, BlockPos target, BlockState targetBefore, BlockPos clicked, BlockState clickedBefore) {
        spbAccuracy.BuildSession session = activeSession(player);
        if (session == null) {
            return;
        }
        if (level.getBlockState(target) != targetBefore) {
            orient(level, target, session);
        }
        if (!clicked.equals(target) && level.getBlockState(clicked) != clickedBefore) {
            orient(level, clicked, session);
        }
    }

    /** Turns the block at the position to the build's facing / half / axis. Does nothing when it is a different block. */
    private static void orient(ServerLevel level, BlockPos pos, spbAccuracy.BuildSession session) {
        com.sk89q.worldedit.world.block.BlockState expected = expectedAt(session, pos);
        if (expected == null) {
            return;
        }
        BlockState actual = level.getBlockState(pos);
        Block expectedBlock = blockOf(expected);
        if (expectedBlock == null || actual.getBlock() != expectedBlock) {
            return;
        }
        // Two-block and bed blocks are left alone: turning only one half would break them.
        if (actual.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || actual.hasProperty(BlockStateProperties.BED_PART)) {
            return;
        }
        Map<String, String> wanted = spbAccuracy.weProperties(expected);
        BlockState result = actual;
        for (Property<?> property : actual.getProperties()) {
            String name = property.getName();
            if (!ORIENT_PROPERTIES.contains(name)) {
                continue;
            }
            String value = wanted.get(name);
            if (value == null) {
                continue;
            }
            if ("half".equals(name) && property.getValueClass() != Half.class) {
                continue;
            }
            if ("type".equals(name)) {
                // Only slabs: top / bottom, never into or out of a double slab.
                if (property.getValueClass() != SlabType.class || "double".equalsIgnoreCase(value)
                    || actual.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
                    continue;
                }
            }
            result = withValue(result, property, value.trim().toLowerCase(Locale.ROOT));
        }
        if (result == actual) {
            return;
        }
        result = Block.updateFromNeighbourShapes(result, level, pos);
        if (!result.canSurvive(level, pos)) {
            return;
        }
        level.setBlock(pos, result, 3);
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.isPresent() ? state.setValue(property, parsed.get()) : state;
    }
}
