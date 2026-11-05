package com.paero.speedbuilder.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public class FlowableFluidMixin {
    // Yarn "flow" == Mojang "spreadTo"
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void paerospeedbuilder$preventFlow(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluidState, CallbackInfo ci) {
        if (paerospeedbuilder$isInBuildArea(pos)) {
            ci.cancel();
        }
    }

    private static boolean paerospeedbuilder$isInBuildArea(BlockPos pos) {
        return pos.getX() >= -10 && pos.getX() <= 10 && pos.getZ() >= -10 && pos.getZ() <= 10 && pos.getY() >= 0 && pos.getY() <= 50;
    }
}
