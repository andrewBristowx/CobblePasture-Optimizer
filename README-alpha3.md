# 0.1.0-alpha.3 test notes

This build keeps the alpha.2 virtual Pokemon and hopper routing behavior and adds a lightweight floating vanilla TextDisplay above non-empty Pastures.

Expected display fields by default:

- total stored Pokemon
- species grouped by count
- hopper output status
- Cobbreeding 2.2.2 breeding status/progress when installed
- Cobbreeding egg count when installed

Configuration is generated at `config/cobblepastureoptimizer.json` on first startup.

Validation checklist:

1. Place Pokemon in a Pasture and verify only one floating text display appears.
2. Verify Pokemon remain physically virtualized.
3. Add/remove Pokemon and verify grouped species/counts update.
4. Add/remove a hopper directly below and verify output status changes.
5. With Cobbreeding 2.2.2, verify breeding progress and egg count update.
6. Break the Pasture and verify its display is removed within a few seconds.
7. Restart the server and verify no duplicate displays appear.
