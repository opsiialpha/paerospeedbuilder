package com.paero.speedbuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

public class resetBuild {
    public static int hardReset(CommandSourceStack source) {
        ServerLevel world = source.getLevel();
        BlockPos min = BuildArea.minPos(source.getServer());
        for (int x = 0; x < BuildArea.SIZE_XZ; ++x) {
            for (int y = 0; y <= BuildArea.HEIGHT; ++y) {
                for (int z = 0; z < BuildArea.SIZE_XZ; ++z) {
                    world.setBlock(min.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        source.sendSuccess(() -> Theme.lit(source, "\u00a7aArea cleared"), false);
        return 1;
    }
}
