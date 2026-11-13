---
id: requirements
title: Requirements
sidebar_position: 1
---

## Server

- **Paper or Folia 26.3** or newer. The plugin is built against the 26.3 API.
- **Java 25**.

## Plugins

None are required. Each of these adds something when it is installed:

| Plugin | What it adds |
|---|---|
| SolarityGens 26.11.0+ | `/gens start`, which is what the default `start-actions` runs to hand out the claim and starter gens. Without it, change `start-actions` to whatever gives your players their start. |
| LandClaim 26.11.0+ | Checking that the claim really exists before a player counts as onboarded, and counting players who already own a claim as onboarded. Without it, `verify.mode: landclaim` acts like `none`. |

SolarityOnboarding uses the SQLite driver that ships with Paper. Skins are fetched from Mojang and
[MineSkin](https://mineskin.org), so the server needs outgoing HTTPS for the `skin` command (only when
you set a skin, never while players are on).
