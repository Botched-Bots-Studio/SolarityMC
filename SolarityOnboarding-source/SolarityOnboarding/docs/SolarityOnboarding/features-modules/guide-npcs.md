---
id: guide-npcs
title: Guide NPCs
sidebar_position: 2
---

Guides are vanilla mannequins with a player skin, a name and an optional line under it. No NPC plugin
is needed.

## Roles

| Role | On click |
|---|---|
| `onboarding` | Runs the [onboarding flow](onboarding.md) for new players; returning lines and its own actions for everyone else |
| `info` | Says its `lines`, then runs its `actions`. For the warzone, shops, crates, rules ... |

Both right-click and left-click count. Guides can't be hurt, pushed or moved, and they turn to look at
the nearest player (`npc.look-distance`).

## Lines and actions

```
/onboard npc lines crates add <white>Crate keys drop from KOTH and the warzone ores.
/onboard npc actions crates add [player] warp crates
/onboard npc actions crates add [sound] minecraft:block.note_block.pling 1 1.5
```

| Action | Does |
|---|---|
| `[player] <command>` | Run a command as the player (no slash) |
| `[console] <command>` | Run a command from the console |
| `[message] <text>` | Send the player a MiniMessage line |
| `[sound] <key> [volume] [pitch]` | Play a sound to the player |

`%player%`, `%uuid%` and `%guide%` work in lines and actions.

## Respawning and safety

Guides are never saved into the world. The plugin spawns them when their chunk loads and checks every
`npc.check-seconds` that each one is there, so a `/kill @e`, a crash or a chunk reload puts them back
without making copies. Any leftover guide entity found in a loading chunk is removed.

## Moving them around

`/onboard npc move <name>` moves a guide to you, `/onboard npc tp <name>` takes you to it. Everything is
saved to [`guides.yml`](../configuration/guides-configuration.md) straight away.
