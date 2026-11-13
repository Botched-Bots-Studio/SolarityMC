---
id: commands-permissions
title: Commands & Permissions
sidebar_position: 1
---

The command is `/onboard`, also answering to `/solarityonboarding` and `/guide`. Tab completion
covers every argument.

## Player commands

| Command | Does | Permission |
|---|---|---|
| `/onboard start` | Onboard without walking to a guide. Only works when `start-command` allows it (by default: only while no onboarding guide exists) | `solarityonboarding.start` |

## Admin commands

All need `solarityonboarding.admin` and work from the console too (give a world and coordinates when
the console creates or moves a guide).

| Command | Does |
|---|---|
| `/onboard start <player>` | Run onboarding for an online player, ignoring `start-command` |
| `/onboard status <player>` | Whether a player has onboarded, when, and how (`guide`, `command`, `staff`, `existing-claim`) |
| `/onboard reset <player>` | Forget that a player onboarded, so the guide works for them again. Their claim and gens stay; use `/gens admin reset <player> confirm` to clear those too |
| `/onboard complete <player>` | Mark a player as onboarded without running anything |
| `/onboard wipe` then `/onboard wipe confirm` | Reset onboarding for every player (for a new season). The confirm must come within 30 seconds |
| `/onboard reload` | Reload `config.yml`, `messages.yml` and `guides.yml` |

## Guide commands

| Command | Does |
|---|---|
| `/onboard npc create <name> [onboarding\|info] [world x y z [yaw]]` | Make a guide where you stand (or at the given spot). Role defaults to `onboarding`. Names use a-z, 0-9, `_` and `-` |
| `/onboard npc remove <name>` | Delete a guide |
| `/onboard npc move <name> [world x y z [yaw]]` | Move a guide to you (or to the given spot) |
| `/onboard npc tp <name>` | Teleport to a guide |
| `/onboard npc list` | Every guide with its role, place and skin |
| `/onboard npc name <name> <text>` | The name over its head, in MiniMessage |
| `/onboard npc description <name> <text\|none>` | The small line under the name |
| `/onboard npc skin <name> player <player>` | Copy a Minecraft player's skin |
| `/onboard npc skin <name> mineskin <link or id>` | Use a skin from MineSkin |
| `/onboard npc skin <name> url <png link>` | Use any skin image online (NameMC, Planet Minecraft, imgur ...) |
| `/onboard npc skin <name> texture <value> [signature]` | Paste a texture value directly |
| `/onboard npc skin <name> clear` | Back to the default skin |
| `/onboard npc lines <name> [list \| add <text> \| remove <n> \| clear]` | What the guide says when clicked |
| `/onboard npc actions <name> [list \| add <action> \| remove <n> \| clear]` | What runs after the lines (info guides, or onboarding guides after onboarding) |
| `/onboard npc guidance <name> [on\|off]` | Whether new players are pointed to this onboarding guide |

## Permissions

| Permission | Default | Allows |
|---|---|---|
| `solarityonboarding.start` | everyone | `/onboard start` (when allowed in config) |
| `solarityonboarding.admin` | op | Everything else |

Clicking a guide needs no permission.
