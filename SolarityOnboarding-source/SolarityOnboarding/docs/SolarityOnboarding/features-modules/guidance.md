---
id: guidance
title: Guidance
sidebar_position: 3
---

Players who haven't onboarded are pointed to the onboarding guide. Nobody else sees any of it, and it
stops the moment they're onboarded.

| Part | What the player sees | Config |
|---|---|---|
| Arrow | A bobbing `▼` over the guide's head (only for them) | `guidance.arrow` |
| Ring | Particles circling the guide's feet, within 64 blocks | `guidance.beacon` |
| Trail | A short line of particles from the player toward the guide | `guidance.trail` |
| Action bar | `Talk to Guide » 12m`, then `Right-click Guide` when close | `guidance.action-bar` |

The trail stops when the player is within `min-distance` and isn't drawn past `max-distance` or in
another world. With several onboarding guides, each player is pointed to the nearest one.

Turn guidance off for one guide with `/onboard npc guidance <name> off`.
