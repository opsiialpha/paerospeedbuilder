package com.paero.speedbuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

public class resetBuild {
    public static int hardReset(CommandSourceStack source) {
        ServerLevel world = source.getLevel();
        for (int x = -3; x <= 3; ++x) {
            for (int y = 0; y <= 16; ++y) {
                for (int z = -3; z <= 3; ++z) {
                    world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        source.sendSuccess(() -> Theme.lit(source, "\u00a7aArea cleared"), false);
        return 1;
    }
}
