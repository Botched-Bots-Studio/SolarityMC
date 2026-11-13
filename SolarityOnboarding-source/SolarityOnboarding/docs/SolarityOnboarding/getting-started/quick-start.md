---
id: quick-start
title: Quick Start
sidebar_position: 3
---

## 1. Put the guide at spawn

Stand where the guide should be, facing the way it should face, and run:

```
/onboard npc create welcome onboarding
```

The guide appears at your feet. New players now see an arrow over it, a particle trail leading to
it and the distance in their action bar.

## 2. Give it a name and a skin

```
/onboard npc name welcome <gold><bold>Guide
/onboard npc skin welcome mineskin https://mineskin.org/skins/<id>
```

See [Skins](../features-modules/skins.md) for the other ways to set one.

## 3. Try it

Run `/onboard reset <your name>`, then right-click the guide. It says its two lines, runs
`/gens start` for you, and once LandClaim sees your claim it says its last line. Run
`/onboard status <your name>` to see that you're marked as onboarded.

## 4. Add info guides (optional)

```
/onboard npc create pvp info
/onboard npc name pvp <red><bold>Warzone
/onboard npc lines pvp add <white>KOTH, crates and rare ores. <red>PvP is always on.
/onboard npc actions pvp add [player] warp warzone
```

Clicking it says the line, then sends the player to the warzone.
