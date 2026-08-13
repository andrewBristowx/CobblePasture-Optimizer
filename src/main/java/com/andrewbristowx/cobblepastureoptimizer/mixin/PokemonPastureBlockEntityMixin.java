package com.andrewbristowx.cobblepastureoptimizer.mixin;

import com.andrewbristowx.cobblepastureoptimizer.service.VirtualPastureService;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PokemonPastureBlockEntity.class, priority = 2000)
public abstract class PokemonPastureBlockEntityMixin {

    @Inject(method = "TICKER$lambda$0", at = @At("HEAD"), order = 900)
    private static void cobblePastureOptimizer$virtualTick(
            Level world,
            BlockPos pos,
            BlockState state,
            PokemonPastureBlockEntity blockEntity,
            CallbackInfo ci
    ) {
        if (world.isClientSide || !(world instanceof ServerLevel serverLevel)) {
            return;
        }

        VirtualPastureService.tick(serverLevel, pos, blockEntity);
    }
}
