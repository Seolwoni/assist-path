# Assist Path

Assist Path is a modified version of parts of **Baritone** (https://github.com/cabaletta/baritone, release v1.19.0 for Minecraft 26.2),
licensed under the **GNU Lesser General Public License v3.0** (see `LICENSE`). Assist Path is distributed under the same license.

Original authors: leijurv, Brady and the Baritone contributors. All original copyright notices are kept in the source files.

## What was taken
- A* search (`calc/AStarPathFinder`, `AbstractNodeCostSearch`, `PathNode`, `openset/*`)
- Movement cost calculation (`movement/*`, `movement/movements/*`: traverse, ascend, descend, diagonal, fall, downward, pillar, parkour)
- Block passability and precomputed data (`MovementHelper`, `precompute/*`), tool speed (`util/ToolSet`), goals (`goals/*`)

## What was changed (2026, seolwoni)
- Package renamed to `com.seolwoni.assistpath`.
- Execution removed from movements (inputs, rotations, state machine): Assist Path only plans; Assist Core walks the plan.
- No dependency on Baritone's `IBaritone`, settings system, world cache or command system. Settings used by the
  calculation are in `PathSettings` with Baritone's defaults (a few adjusted: more storage blocks avoided when breaking,
  no water-bucket falls).
- `BlockStateInterface` reads only chunks loaded by the client (no world cache yet).
- `Path` / `PathResult` simplified; mob avoidance removed for now.
- New public API: `AssistPath.find(...)` returning a `Route` of steps.
