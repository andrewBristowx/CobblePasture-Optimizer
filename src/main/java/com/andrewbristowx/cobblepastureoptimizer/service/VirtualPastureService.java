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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps the logical Cobblemon pasture link but removes the expensive PokemonEntity.
 * Pasture Loot is then evaluated against the stored Pokemon and its FormData drop table.
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

        if (config.legacyFlattenItemQuantity()) {
            dropLegacySingleItem(world, dropPosition, itemDropEntry);
        } else {
            // Cobblemon's ItemDropEntry explicitly accepts a nullable entity. When it is null,
            // ON_ENTITY falls back to the supplied position, preserving quantities/components.
            itemDropEntry.drop(null, world, dropPosition, null);
        }
    }

    private static void dropLegacySingleItem(ServerLevel world, Vec3 position, ItemDropEntry itemDropEntry) {
        Item item = world.registryAccess()
                .registryOrThrow(Registries.ITEM)
                .get(itemDropEntry.getItem());

        if (item == null) {
            return;
        }

        ItemStack stack = new ItemStack(item, 1);
        world.addFreshEntity(new ItemEntity(world, position.x, position.y, position.z, stack));
    }
}
