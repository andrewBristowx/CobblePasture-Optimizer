package com.andrewbristowx.cobblepastureoptimizer.service;

import com.andrewbristowx.cobblepastureoptimizer.config.OptimizerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Enforces the lightweight Pasture display appearance after the alpha.3 renderer updates its text.
 *
 * Keeping this separate from the content renderer makes alpha.4 a small, low-risk visual change:
 * the existing Pokemon grouping/breeding/hopper logic remains untouched while the display becomes
 * fixed in the world and transparent by default.
 */
public final class PastureDisplayAppearanceService {
    private static final String DISPLAY_TAG = "cpo_pasture_display";
    private static final String POSITION_TAG_PREFIX = "cpo_pasture_pos:";

    private PastureDisplayAppearanceService() {
    }

    public static void apply(ServerLevel world, BlockPos pasturePos) {
        OptimizerConfig config = OptimizerConfig.get();
        if (!config.floatingDisplay) {
            return;
        }

        String positionTag = positionTag(pasturePos);
        AABB searchBox = new AABB(pasturePos).inflate(2.0D, 6.0D, 2.0D);
        List<Display.TextDisplay> displays = world.getEntitiesOfClass(
                Display.TextDisplay.class,
                searchBox,
                display -> display.getTags().contains(DISPLAY_TAG)
                        && display.getTags().contains(positionTag)
        );

        if (displays.isEmpty()) {
            return;
        }

        String desiredBillboard = config.facePlayer ? "center" : "fixed";
        int desiredBackground = config.showBackground ? config.backgroundArgb : 0;

        for (Display.TextDisplay display : displays) {
            CompoundTag tag = display.saveWithoutId(new CompoundTag());
            boolean nbtChanged = false;

            if (!desiredBillboard.equals(tag.getString("billboard"))) {
                tag.putString("billboard", desiredBillboard);
                nbtChanged = true;
            }

            if (tag.getInt("background") != desiredBackground) {
                tag.putInt("background", desiredBackground);
                nbtChanged = true;
            }

            if (tag.getBoolean("shadow") != config.textShadow) {
                tag.putBoolean("shadow", config.textShadow);
                nbtChanged = true;
            }

            // Never allow the vanilla default background to reappear when the custom background is disabled.
            if (tag.getBoolean("default_background")) {
                tag.putBoolean("default_background", false);
                nbtChanged = true;
            }

            if (nbtChanged) {
                display.load(tag);
            }

            if (!config.facePlayer) {
                orientLikePasture(world, pasturePos, display);
            }
        }
    }

    private static void orientLikePasture(
            ServerLevel world,
            BlockPos pasturePos,
            Display.TextDisplay display
    ) {
        BlockState state = world.getBlockState(pasturePos);
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return;
        }

        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        float targetYaw = facing.toYRot();

        if (Math.abs(Mth.wrapDegrees(display.getYRot() - targetYaw)) > 0.1F) {
            display.setYRot(targetYaw);
        }
        if (Math.abs(display.getXRot()) > 0.1F) {
            display.setXRot(0.0F);
        }
    }

    private static String positionTag(BlockPos pos) {
        return POSITION_TAG_PREFIX + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
