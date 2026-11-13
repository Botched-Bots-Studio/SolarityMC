---
id: intro
title: Introduction
sidebar_position: 1
---

SolarityOnboarding is the first-join experience for Solarity's GenSMP, for **Paper** and **Folia** 26.3.
It puts a guide NPC at spawn that new players are pointed to, says one or two short lines, and hands
out the starter claim and gen by running `/gens start` for them. It also gives you **info guides**: NPCs
you can put anywhere (the warzone, the shops, the crates) that say a line and run actions when clicked.

It is a standalone plugin. It doesn't need SolarityGens or LandClaim to load, and it doesn't change
them: it runs `/gens start` as the player and then asks LandClaim whether the claim really exists before
it counts the player as onboarded.

## What's in this guide

- **[Getting Started](getting-started/quick-start.md)**: requirements, installation and a first guide in two minutes.
- **[Core Reference](core-reference/commands-permissions.md)**: every command and permission.
- **[Features](features-modules/onboarding.md)**: the onboarding flow, guide NPCs, guidance and skins.
- **[Configuration](configuration/main-configuration.md)**: `config.yml`, `guides.yml` and `messages.yml`.
- **[Advanced](advanced-optimization/folia-support.md)**: Folia and data storage.
- **[Support](support/faq.md)**: FAQ.

## Key features

- **An onboarding guide** with 1-2 short lines that hands out the claim and starter gen.
- **Guidance only new players see**: a bobbing arrow over the guide, a particle ring at its feet, a particle trail from the player to it, and an action bar with the distance. All of it goes away once they're onboarded.
- **Verified, not assumed**: a player only counts as onboarded once LandClaim says they own a claim. If it fails they're told, staff get a log line, and they can click again.
- **Info guides** with their own lines and click actions (`[player]`, `[console]`, `[message]`, `[sound]`).
- **Skins from websites**: a player name, a MineSkin link, or any link to a skin PNG.
- **No NPC plugin needed**: guides are vanilla mannequins that respawn on their own and never save into the world, so they can't duplicate.
- **Season-ready**: `/onboard wipe` resets everyone so the next season starts fresh.
- **Native Folia support.**

Ready to go? Start with the **[Quick Start](getting-started/quick-start.md)** guide.
