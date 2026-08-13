package com.andrewbristowx.cobblepastureoptimizer.service;

import com.andrewbristowx.cobblepastureoptimizer.CobblePastureOptimizer;
import com.cobblemon.mod.common.api.drop.DropEntry;
import com.cobblemon.mod.common.api.drop.DropTable;
import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.dremixam.pastureLoot.Config;
import com.dremixam.pastureLoot.PastureLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps the logical Cobblemon pasture link but removes the expensive PokemonEntity.
 * Pasture Loot is evaluated against the stored Pokemon and its FormData drop table.
 * When a hopper is directly below the Pasture, generated loot is inserted into it
 * before falling back to normal world ItemEntities.
 */
public final class VirtualPastureService {
    private VirtualPastureService() {
    }

    public static void tick(ServerLevel world, BlockPos pasturePos, PokemonPastureBlockEntity pasture) {
        Config pastureLootConfig = getPastureLootConfig();
        if (pastureLootConfig == null) {
            return;
        }

        // Copy so a Cobblemon consistency check can safely mutate the underlying list later in the same tick.
        List<PokemonPastureBlockEntity.Tethering> tetherings = List.copyOf(pasture.getTetheredPokemon());
        for (PokemonPastureBlockEntity.Tethering tethering : tetherings) {
            try {
                Pokemon pokemon = tethering.getPokemon();
                if (pokemon == null || pokemon.isFainted()) {
                    continue;
                }

                // Ignore stale pasture links. Cobblemon will clean these up during checkPokemon().
                if (pokemon.getTetheringId() == null || !pokemon.getTetheringId().equals(tethering.getTetheringId())) {
                    continue;
                }

                unloadPhysicalEntity(pokemon);
                attemptVirtualDrop(world, pasturePos, pokemon, pastureLootConfig);
            } catch (Exception exception) {
                CobblePastureOptimizer.LOGGER.error("Error while processing a virtual pasture Pokemon at {}", pasturePos, exception);
            }
        }
    }

    private static Config getPastureLootConfig() {
        PastureLoot instance = PastureLoot.INSTANCE;
        return instance == null ? null : instance.getConfig();
    }

    private static void unloadPhysicalEntity(Pokemon pokemon) {
        PokemonEntity entity = pokemon.getEntity();
        if (entity == null) {
            return;
        }

        // UNLOADED_TO_CHUNK is deliberately non-destructive. Cobblemon's PokemonEntity.remove()
        // therefore switches the Pokemon back to an inactive state without clearing tetheringId.
        entity.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
    }

    private static void attemptVirtualDrop(
            ServerLevel world,
            BlockPos pasturePos,
            Pokemon pokemon,
            Config config
    ) {
        if (Math.random() >= config.getDropChancePerTick()) {
            return;
        }

        DropTable dropTable = pokemon.getForm().getDrops();
        List<DropEntry> drops = dropTable.getDrops(dropTable.getAmount(), pokemon);
        if (drops.isEmpty()) {
            return;
        }

        DropEntry selected = drops.get(ThreadLocalRandom.current().nextInt(drops.size()));
        if (!(selected instanceof ItemDropEntry itemDropEntry)) {
            return;
        }

        if (Arrays.asList(config.getItemBlacklist()).contains(itemDropEntry.getItem().toString())) {
            return;
        }

        Vec3 dropPosition = new Vec3(
                pasturePos.getX() + 0.5D,
                pasturePos.getY() + 1.25D,
                pasturePos.getZ() + 0.5D
        );

        Container connectedHopper = getConnectedHopper(world, pasturePos);

        if (config.legacyFlattenItemQuantity()) {
            ItemStack legacyStack = createLegacySingleItem(world, itemDropEntry);
            if (legacyStack.isEmpty()) {
                return;
            }

            if (connectedHopper != null) {
                ItemStack remainder = insertIntoContainer(connectedHopper, legacyStack);
                if (remainder.isEmpty()) {
                    return;
                }
                spawnItem(world, dropPosition, remainder);
                return;
            }

            spawnItem(world, dropPosition, legacyStack);
            return;
        }

        if (connectedHopper == null) {
            // Normal Pasture Loot behavior when there is no hopper.
            itemDropEntry.drop(null, world, dropPosition, null);
            return;
        }

        // ItemDropEntry is responsible for its exact quantity/components. To preserve that behavior
        // without reimplementing Cobblemon internals, capture only the ItemEntities created by this
        // specific drop call, insert them immediately, and discard successfully transferred entities
        // before the world gets a chance to tick/move them.
        captureDropIntoHopper(world, dropPosition, itemDropEntry, connectedHopper);
    }

    private static Container getConnectedHopper(ServerLevel world, BlockPos pasturePos) {
        BlockEntity blockEntity = world.getBlockEntity(pasturePos.below());
        return blockEntity instanceof HopperBlockEntity hopper ? hopper : null;
    }

    private static void captureDropIntoHopper(
            ServerLevel world,
            Vec3 dropPosition,
            ItemDropEntry itemDropEntry,
            Container hopper
    ) {
        AABB captureBox = new AABB(
                dropPosition.x - 0.75D,
                dropPosition.y - 0.75D,
                dropPosition.z - 0.75D,
                dropPosition.x + 0.75D,
                dropPosition.y + 0.75D,
                dropPosition.z + 0.75D
        );

        Set<UUID> existingEntities = new HashSet<>();
        for (ItemEntity entity : world.getEntitiesOfClass(ItemEntity.class, captureBox)) {
            existingEntities.add(entity.getUUID());
        }

        itemDropEntry.drop(null, world, dropPosition, null);

        for (ItemEntity entity : world.getEntitiesOfClass(ItemEntity.class, captureBox)) {
            if (existingEntities.contains(entity.getUUID()) || !entity.isAlive()) {
                continue;
            }

            ItemStack remainder = insertIntoContainer(hopper, entity.getItem());
            if (remainder.isEmpty()) {
                entity.discard();
            } else {
                // Hopper full/partially full: retain the uninserted remainder as a normal world drop.
                entity.setItem(remainder);
            }
        }
    }

    private static ItemStack insertIntoContainer(Container container, ItemStack input) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = input.copy();

        // Merge with existing stacks first.
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty() || !container.canPlaceItem(slot, remaining)) {
                continue;
            }

            if (!ItemStack.isSameItemSameComponents(existing, remaining)) {
                continue;
            }

            int maxStackSize = Math.min(container.getMaxStackSize(), existing.getMaxStackSize());
            int capacity = maxStackSize - existing.getCount();
            if (capacity <= 0) {
                continue;
            }

            int moved = Math.min(capacity, remaining.getCount());
            existing.grow(moved);
            remaining.shrink(moved);
        }

        // Then use empty slots.
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() || !container.canPlaceItem(slot, remaining)) {
                continue;
            }

            int moved = Math.min(container.getMaxStackSize(), Math.min(remaining.getMaxStackSize(), remaining.getCount()));
            ItemStack inserted = remaining.copy();
            inserted.setCount(moved);
            container.setItem(slot, inserted);
            remaining.shrink(moved);
        }

        if (remaining.getCount() != input.getCount()) {
            container.setChanged();
        }

        return remaining;
    }

    private static ItemStack createLegacySingleItem(ServerLevel world, ItemDropEntry itemDropEntry) {
        Item item = world.registryAccess()
                .registryOrThrow(Registries.ITEM)
                .get(itemDropEntry.getItem());

        if (item == null) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item, 1);
    }

    private static void spawnItem(ServerLevel world, Vec3 position, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        world.addFreshEntity(new ItemEntity(world, position.x, position.y, position.z, stack));
    }
}
