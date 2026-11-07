package com.paero.speedbuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public class buildMode {
    private static final Set<UUID> BUILD_MODE_PLAYERS = new HashSet<>();

    private static boolean isInsideBuildArea(MinecraftServer server, BlockPos pos) {
        return BuildArea.contains(server, pos);
    }

    private static String blockKey(net.minecraft.world.level.block.Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static boolean calculateSlabCombining(BlockState stateBefore, BlockItem blockItem) {
        if (stateBefore.isAir()) {
            return false;
        }
        String existingBlockId = blockKey(stateBefore.getBlock());
        String placingBlockId = blockKey(blockItem.getBlock());
        if (existingBlockId.equals(placingBlockId) && existingBlockId.endsWith("_slab")) {
            try {
                String existingType = stateBefore.getValue(BlockStateProperties.SLAB_TYPE).toString();
                return !"double".equals(existingType);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public static void registerFluidEvents() {
    }

    public static boolean isInBuildMode(ServerPlayer player) {
        return BUILD_MODE_PLAYERS.contains(player.getUUID());
    }

    /** Turns build mode on. The player's game mode and abilities are left exactly as they are. */
    public static void enable(ServerPlayer player) {
        if (BUILD_MODE_PLAYERS.contains(player.getUUID())) {
            return;
        }
        BUILD_MODE_PLAYERS.add(player.getUUID());
        player.displayClientMessage(Theme.lit(player, "\u00a7aBuild Mode enabled"), false);
    }

    /** Leaves build mode. The game mode is never changed. */
    public static void disable(ServerPlayer player) {
        boolean wasInBuildMode = BUILD_MODE_PLAYERS.remove(player.getUUID());
        if (!wasInBuildMode) {
            return;
        }
        player.displayClientMessage(Theme.lit(player, "\u00a7cBuild Mode disabled!"), false);
    }

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(buildMode::onBlockBreak);
        // Runs last, and only if nobody cancelled the break.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, buildMode::onBlockBroken);
        NeoForge.EVENT_BUS.addListener(buildMode::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(buildMode::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(buildMode::onHarvestCheck);
    }

    // Replaces the old client/common Player mixins (getBlockBreakingSpeed / canHarvest)
    private static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            if (BuildModeClient.isInBuildMode()) {
                event.setNewSpeed(40.0f);
            }
        } else if (player instanceof ServerPlayer serverPlayer && isInBuildMode(serverPlayer)) {
            event.setNewSpeed(40.0f);
        }
    }

    private static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            if (BuildModeClient.isInBuildMode()) {
                event.setCanHarvest(true);
            }
        } else if (player instanceof ServerPlayer serverPlayer && isInBuildMode(serverPlayer)) {
            event.setCanHarvest(true);
        }
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!isInBuildMode(serverPlayer)) {
            return;
        }
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        if (state.getBlock() == Blocks.AIR) {
            event.setCanceled(true);
            return;
        }
        if (BuildArea.isBelowFloor(serverPlayer.getServer(), pos)) {
            serverPlayer.displayClientMessage(Theme.lit(serverPlayer, "\u00a7cCan't break blocks at or below Y = " + (BuildArea.minPos(serverPlayer.getServer()).getY() - 1) + "!"), false);
            event.setCanceled(true);
            return;
        }
        boolean refund = !(buildLoader.isPreviewing(serverPlayer) || SpeedBuilderConfig.isPaero(serverPlayer.getUUID()));
        boolean creative = serverPlayer.isCreative();
        int amount = 1;
        if (!creative) {
            // Survival / Adventure: break the block ourselves without vanilla drops, so the block is only
            // given back once (and not at all while previewing or on Paero difficulty).
            if (blockKey(state.getBlock()).endsWith("_slab")) {
                try {
                    if ("double".equals(state.getValue(BlockStateProperties.SLAB_TYPE).toString())) {
                        amount = 2;
                    }
                } catch (Exception ignored) {
                }
            }
            event.setCanceled(true);
            serverPlayer.serverLevel().destroyBlock(pos, false, serverPlayer);
        }
        if (!refund) {
            return;
        }
        ItemStack toGive = new ItemStack(state.getBlock().asItem(), amount);
        MinecraftServer server = serverPlayer.getServer();
        if (server != null && !toGive.isEmpty() && toGive.getItem() != Items.AIR) {
            server.execute(() -> {
                boolean inserted = serverPlayer.getInventory().add(toGive);
                if (!inserted) {
                    serverPlayer.drop(toGive, false);
                }
            });
        }
    }

    private static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!isInBuildMode(serverPlayer)) {
            return;
        }
        Level level = serverPlayer.level();
        if (level.isClientSide || buildLoader.isPreviewing(serverPlayer) || SpeedBuilderConfig.isPaero(serverPlayer.getUUID())) {
            return;
        }
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        String blockId = blockKey(state.getBlock());
        if (blockId.endsWith("_slab")) {
            try {
                String slabType = state.getValue(BlockStateProperties.SLAB_TYPE).toString();
                if ("double".equals(slabType)) {
                    ItemStack extraSlab = new ItemStack(state.getBlock(), 1);
                    boolean inserted = serverPlayer.getInventory().add(extraSlab);
                    if (!inserted) {
                        ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, extraSlab);
                        level.addFreshEntity(itemEntity);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void deny(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!isInBuildMode(serverPlayer)) {
            return;
        }
        Level world = event.getLevel();
        if (world.isClientSide) {
            return;
        }
        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }
        InteractionHand hand = event.getHand();
        BlockHitResult hitResult = event.getHitVec();
        ItemStack itemInHand = serverPlayer.getItemInHand(hand);

        if (itemInHand.getItem() == Items.BUCKET || itemInHand.getItem() == Items.WATER_BUCKET || itemInHand.getItem() == Items.LAVA_BUCKET) {
            if (itemInHand.getItem() == Items.WATER_BUCKET) {
                BlockPos targetPos = hitResult.getBlockPos().relative(hitResult.getDirection());
                BlockState targetState = world.getBlockState(targetPos);
                if (!isInsideBuildArea(server, targetPos)) {
                    serverPlayer.displayClientMessage(Theme.lit(serverPlayer, "\u00a7cYou cannot place blocks outside the build area!"), false);
                    deny(event);
                    return;
                }
                if (AutoBuild.isLocked(serverPlayer) && !AutoBuild.isBuildSpot(serverPlayer, targetPos)) {
                    serverPlayer.displayClientMessage(Theme.lit(serverPlayer, "\u00a7cSuperEasy: you can only place where the build needs a block!"), true);
                    deny(event);
                    return;
                }
                if (targetState.getBlock() == Blocks.WATER || targetState.getFluidState().getType() == Fluids.WATER) {
                    deny(event);
                    return;
                }
            }
            if (serverPlayer.isCreative()) {
                // Creative players need Survival rules for a tick so buckets actually place/pick up fluids.
                boolean wasFlying = serverPlayer.getAbilities().flying;
                boolean wasInvulnerable = serverPlayer.getAbilities().invulnerable;
                serverPlayer.setGameMode(GameType.SURVIVAL);
                server.execute(() -> server.execute(() -> {
                    serverPlayer.setGameMode(GameType.CREATIVE);
                    serverPlayer.getAbilities().flying = wasFlying;
                    serverPlayer.getAbilities().mayfly = true;
                    serverPlayer.getAbilities().invulnerable = wasInvulnerable;
                    serverPlayer.onUpdateAbilities();
                }));
            }
            return;
        }

        // SuperEasy: right-click a block face and the block the build needs there is placed for you
        // (the hotbar switches to it, or it is pulled in from the inventory).
        if (SpeedBuilderConfig.isSuperEasy(serverPlayer.getUUID())) {
            if (hand == InteractionHand.MAIN_HAND) {
                InteractionResult auto = AutoBuild.tryAutoPlace(serverPlayer, serverPlayer.serverLevel(), hitResult);
                if (auto != null) {
                    event.setCanceled(true);
                    event.setCancellationResult(auto);
                    return;
                }
            }
            // Auto place did not place anything. A normal placement would drop the block wherever vanilla puts it
            // (also from the off hand), so while a build is running SuperEasy never lets a block item through.
            if (itemInHand.getItem() instanceof BlockItem && AutoBuild.isLocked(serverPlayer)) {
                serverPlayer.displayClientMessage(Theme.lit(serverPlayer, "\u00a7cSuperEasy: you can only place where the build needs a block!"), true);
                deny(event);
                return;
            }
        }

        Item heldItem = itemInHand.getItem();
        if (!(heldItem instanceof BlockItem blockItem)) {
            return;
        }
        if (itemInHand.isEmpty() || itemInHand.getCount() <= 0) {
            serverPlayer.displayClientMessage(Theme.lit(serverPlayer, "\u00a7cYou're out of " + itemInHand.getItem().getDescription().getString() + "!"), true);
            deny(event);
            return;
        }
        final boolean paero = SpeedBuilderConfig.isPaero(serverPlayer.getUUID());
        // Stack size before the click. In Survival vanilla already takes the block, so we only take one
        // ourselves when the stack is still untouched afterwards (Creative / build mode).
        final int countBefore = itemInHand.getCount();
        final BlockPos targetPos = hitResult.getBlockPos().relative(hitResult.getDirection());
        final BlockState stateBefore = world.getBlockState(targetPos);
        if (!isInsideBuildArea(server, targetPos)) {
            serverPlayer.displayClientMessage(Theme.lit(serverPlayer, "\u00a7cYou cannot place blocks outside the build area!"), false);
            deny(event);
            return;
        }
        if (SpeedBuilderConfig.isAutoOrient(serverPlayer.getUUID())) {
            // Auto Orient: once the click has been handled, turn whatever was placed to the way the build needs it.
            final BlockPos clickedSpot = hitResult.getBlockPos();
            final BlockState clickedBefore = world.getBlockState(clickedSpot);
            final net.minecraft.server.level.ServerLevel serverLevel = serverPlayer.serverLevel();
            server.execute(() -> server.execute(() -> AutoBuild.orientAfterPlacement(serverPlayer, serverLevel, targetPos, stateBefore, clickedSpot, clickedBefore)));
        }
        boolean isSlabItem = blockItem.getBlock().toString().contains("slab");
        if (isSlabItem) {
            BlockPos clickedPos = hitResult.getBlockPos();
            BlockState clickedState = world.getBlockState(clickedPos);
            boolean combineAtClicked = calculateSlabCombining(clickedState, blockItem);
            boolean combineAtTarget = calculateSlabCombining(stateBefore, blockItem);
            final boolean willCreateDoubleSlab;
            final BlockPos actualPlacementPos;
            final BlockState actualStateBefore;
            if (combineAtClicked) {
                willCreateDoubleSlab = true;
                actualPlacementPos = clickedPos;
                actualStateBefore = clickedState;
            } else if (combineAtTarget) {
                willCreateDoubleSlab = true;
                actualPlacementPos = targetPos;
                actualStateBefore = stateBefore;
            } else {
                willCreateDoubleSlab = false;
                actualPlacementPos = targetPos;
                actualStateBefore = stateBefore;
            }
            if (!isInsideBuildArea(server, actualPlacementPos)) {
                serverPlayer.displayClientMessage(Theme.lit(serverPlayer, "\u00a7cYou cannot place blocks outside the build area!"), false);
                deny(event);
                return;
            }
            if (!actualStateBefore.canBeReplaced() && !willCreateDoubleSlab) {
                return;
            }
            if (paero) {
                restoreStack(server, serverPlayer, hand, blockItem, countBefore);
                return;
            }
            server.execute(() -> {
                if (willCreateDoubleSlab) {
                    server.execute(() -> checkSlabPlacementAfterDelay(serverPlayer, hand, blockItem, actualPlacementPos, actualStateBefore, true, countBefore));
                } else {
                    checkSlabPlacementAfterDelay(serverPlayer, hand, blockItem, actualPlacementPos, actualStateBefore, false, countBefore);
                }
            });
        } else {
            if (!stateBefore.canBeReplaced()) {
                return;
            }
            if (paero) {
                restoreStack(server, serverPlayer, hand, blockItem, countBefore);
                return;
            }
            server.execute(() -> {
                BlockState stateAfter = world.getBlockState(targetPos);
                boolean blockWasPlaced = !stateAfter.equals(stateBefore) && stateAfter.getBlock() == blockItem.getBlock();
                if (blockWasPlaced) {
                    ItemStack currentStack = serverPlayer.getItemInHand(hand);
                    if (!currentStack.isEmpty() && currentStack.getItem() == blockItem && currentStack.getCount() >= countBefore) {
                        if (currentStack.getCount() > 1) {
                            currentStack.shrink(1);
                        } else {
                            serverPlayer.setItemInHand(hand, ItemStack.EMPTY);
                        }
                    }
                }
            });
        }
    }

    /** Paero difficulty: the inventory is never touched, so whatever vanilla took for a placement is put back. */
    private static void restoreStack(MinecraftServer server, ServerPlayer player, InteractionHand hand, BlockItem blockItem, int countBefore) {
        if (player.isCreative()) {
            return;
        }
        server.execute(() -> server.execute(() -> {
            ItemStack current = player.getItemInHand(hand);
            if (current.isEmpty()) {
                player.setItemInHand(hand, new ItemStack(blockItem, countBefore));
            } else if (current.getItem() == blockItem && current.getCount() < countBefore) {
                current.setCount(countBefore);
            }
        }));
    }

    private static void checkSlabPlacementAfterDelay(ServerPlayer serverPlayer, InteractionHand hand, BlockItem blockItem, BlockPos actualPlacementPos, BlockState actualStateBefore, boolean willCreateDoubleSlab, int countBefore) {
        ServerLevelHolder h = new ServerLevelHolder(serverPlayer);
        BlockState stateAfter = h.level().getBlockState(actualPlacementPos);
        boolean slabWasPlaced = false;
        if (!stateAfter.isAir() && stateAfter.getBlock() == blockItem.getBlock()) {
            try {
                String newType = stateAfter.getValue(BlockStateProperties.SLAB_TYPE).toString();
                if (willCreateDoubleSlab) {
                    if (!"double".equals(newType)) {
                        h.level().getServer().execute(() -> {
                            BlockState finalState = h.level().getBlockState(actualPlacementPos);
                            try {
                                String finalType = finalState.getValue(BlockStateProperties.SLAB_TYPE).toString();
                                if ("double".equals(finalType)) {
                                    consumeSlabItem(serverPlayer, hand, blockItem, countBefore);
                                } else if (!finalState.isAir() && finalState.getBlock() == blockItem.getBlock()) {
                                    consumeSlabItem(serverPlayer, hand, blockItem, countBefore);
                                }
                            } catch (Exception e) {
                                if (!finalState.isAir() && finalState.getBlock() == blockItem.getBlock()) {
                                    consumeSlabItem(serverPlayer, hand, blockItem, countBefore);
                                }
                            }
                        });
                        return;
                    }
                    slabWasPlaced = true;
                } else if ("bottom".equals(newType) || "top".equals(newType)) {
                    slabWasPlaced = true;
                }
            } catch (Exception e) {
                if (stateAfter.equals(actualStateBefore)) {
                    return;
                }
                slabWasPlaced = true;
            }
        }
        if (!slabWasPlaced) {
            return;
        }
        consumeSlabItem(serverPlayer, hand, blockItem, countBefore);
    }

    private record ServerLevelHolder(ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level() {
            return player.serverLevel();
        }
    }

    private static void consumeSlabItem(ServerPlayer serverPlayer, InteractionHand hand, BlockItem blockItem, int countBefore) {
        ItemStack currentStack = serverPlayer.getItemInHand(hand);
        // Already reduced by vanilla (Survival) -> don't take a second one
        if (!currentStack.isEmpty() && currentStack.getItem() == blockItem && currentStack.getCount() >= countBefore) {
            if (currentStack.getCount() > 1) {
                currentStack.shrink(1);
            } else {
                serverPlayer.setItemInHand(hand, ItemStack.EMPTY);
            }
        }
    }
}
