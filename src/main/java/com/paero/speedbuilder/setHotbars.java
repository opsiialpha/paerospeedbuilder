package com.paero.speedbuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class setHotbars {
    private static final Map<String, List<ItemStack>> HOTBAR_LAYOUTS = new HashMap<>();

    private static void initializeHotbarLayouts() {
        HOTBAR_LAYOUTS.put("paintingver", Arrays.asList(
            new ItemStack(Blocks.SPRUCE_PLANKS, 18), new ItemStack(Blocks.YELLOW_WOOL, 3),
            new ItemStack(Blocks.BLUE_WOOL, 3), new ItemStack(Blocks.LIGHT_BLUE_WOOL, 5),
            new ItemStack(Blocks.GOLD_BLOCK, 1), null, null, null, null));
        HOTBAR_LAYOUTS.put("colors", Arrays.asList(
            new ItemStack(Blocks.LIME_WOOL, 9), new ItemStack(Blocks.RED_WOOL, 10),
            new ItemStack(Blocks.YELLOW_WOOL, 10), new ItemStack(Blocks.PURPLE_WOOL, 6),
            new ItemStack(Blocks.BLUE_WOOL, 9), new ItemStack(Blocks.ORANGE_WOOL, 6), null, null, null));
    }

    public static boolean hasSpecificHotbar(String buildName) {
        return HOTBAR_LAYOUTS.containsKey(buildName);
    }

    public static List<ItemStack> getHotbarLayout(String buildName) {
        return HOTBAR_LAYOUTS.get(buildName);
    }

    public static void giveSpecificHotbar(ServerPlayer player, String buildName) {
        if (!hasSpecificHotbar(buildName)) {
            return;
        }
        List<ItemStack> hotbarLayout = getHotbarLayout(buildName);
        player.getInventory().clearContent();
        for (int i = 0; i < Math.min(hotbarLayout.size(), 9); ++i) {
            ItemStack item = hotbarLayout.get(i);
            if (item == null) continue;
            player.getInventory().setItem(i, item.copy());
        }
        player.getInventory().setChanged();
    }

    public static void addHotbarLayout(String buildName, List<ItemStack> layout) {
        HOTBAR_LAYOUTS.put(buildName, layout);
    }

    public static Set<String> getBuildsWithHotbars() {
        return HOTBAR_LAYOUTS.keySet();
    }

    static {
        initializeHotbarLayouts();
    }
}
