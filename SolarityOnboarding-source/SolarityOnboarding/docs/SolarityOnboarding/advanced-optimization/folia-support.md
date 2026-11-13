---
id: folia-support
title: Folia Support
sidebar_position: 1
---

SolarityOnboarding runs natively on Folia (`folia-supported: true`). It's tested on Folia 26.3.

- Guides are spawned, turned and checked on the region that owns their chunk.
- Each player's dialogue, guidance and onboarding run on that player's own scheduler, so they follow
  the player across regions and teleports.
- A guide's per-player arrow is only touched from the region that owns it.
- Database writes and skin downloads happen off the game threads.
