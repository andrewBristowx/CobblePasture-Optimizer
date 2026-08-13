package com.andrewbristowx.cobblepastureoptimizer.mixin;

import com.andrewbristowx.cobblepastureoptimizer.config.OptimizerConfig;
import com.andrewbristowx.cobblepastureoptimizer.service.PastureDisplayService;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = PokemonPastureBlockEntity.class, priority = 1990)
public abstract class PokemonPastureDisplayMixin {

    @Inject(method = "TICKER$lambda$0", at = @At("HEAD"), order = 910)
    private static void cobblePastureOptimizer$floatingDisplayTick(
            Level world,
            BlockPos pos,
            BlockState state,
            PokemonPastureBlockEntity blockEntity,
            CallbackInfo ci
    ) {
        if (world.isClientSide || !(world instanceof ServerLevel serverLevel)) {
            return;
        }

        OptimizerConfig config = OptimizerConfig.get();
        if (config.floatingDisplay) {
            int interval = config.displayUpdateIntervalTicks;
            long phase = Math.floorMod(pos.asLong(), interval);
            if (Math.floorMod(serverLevel.getGameTime(), interval) != phase) {
                return;
            }
        }

        PastureDisplayService.tick(
                serverLevel,
                pos,
                blockEntity,
                List.copyOf(blockEntity.getTetheredPokemon())
        );
    }
}
