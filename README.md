# CobblePasture Optimizer

**Experimental server-side Fabric addon for Minecraft 1.21.1.**

Target stack for `0.1.0-alpha.1`:

- Minecraft 1.21.1
- Java 21
- Fabric Loader 0.17.2+
- Cobblemon 1.7.3
- Cobblemon Pasture Loot 1.0.5+1.21.1

## What alpha.1 does

Cobblemon normally keeps every pastured Pokemon as a live `PokemonEntity`. Large multiplayer farms can therefore create a high entity/AI/tick cost.

This addon keeps Cobblemon's normal pasture tethering and PC-backed Pokemon data, but unloads the physical pasture entity with a non-destructive removal reason. It then calculates Pasture Loot from the stored Pokemon's form/drop table without needing a live Pokemon entity.

In this first alpha:

- Pastured Pokemon are virtualized (no persistent physical Pokemon entity).
- Pasture Loot keeps using its own `config/PastureLoot.json` values.
- `drop_chance_per_minute`, `tick_per_minute`, `item_blacklist`, and `legacy_flatten_item_quantity` are respected through Pasture Loot's loaded config.
- Cobblemon's normal drop table quantities/components are retained when legacy flattening is disabled.
- Virtual loot appears just above the Pasture block because there is no Pokemon entity position anymore.

## Important alpha warning

This is an experimental compatibility build. Test it on a staging server/world backup before production use. The main validation points are:

1. Existing pastured Pokemon remain listed in the Pasture after their entities disappear.
2. Removing a Pokemon from the Pasture still returns/releases it normally.
3. Restarting the server preserves the pasture links.
4. Drops continue at the configured Pasture Loot probability and quantities.
5. There are no duplicate drops from Pasture Loot and this addon.

## Planned next steps

- Optional direct output into adjacent inventories/hoppers to reduce dropped `ItemEntity` count.
- Runtime statistics for number of virtualized Pokemon and avoided entities.
- Configurable visual representatives (for example one visible Pokemon per Pasture) if it can be done without duplicate loot or persistence issues.
- Performance comparison with many simultaneous player farms.
