# Changelog

## 0.1.0-alpha.2

- Sends virtual Pasture Loot drops directly into a hopper placed immediately below the Pasture.
- Preserves Cobblemon `ItemDropEntry` quantity and components by capturing only the ItemEntities created by the current drop call and inserting them before they can tick or move.
- Falls back to a normal world drop when there is no hopper or when the hopper cannot accept the complete stack.
- Verified compatibility design with Cobbreeding 2.2.2: breeding reads the stored tethered Pokemon rather than requiring physical PokemonEntity instances.
- Cobbreeding eggs remain in its Pasture inventory and can be extracted by vanilla hoppers when `allowHoppersToPullFromPastureBlock` is enabled (default in Cobbreeding 2.2.2).

## 0.1.0-alpha.1

- Initial experimental Fabric implementation.
- Virtualizes physically spawned Cobblemon pasture Pokemon while preserving pasture tethering.
- Replays Cobblemon Pasture Loot 1.0.5 drop checks from stored Pokemon data.
- Uses Pasture Loot's already-loaded probability, blacklist, and legacy quantity configuration.
- Moves virtual drop spawn position to the Pasture block.
