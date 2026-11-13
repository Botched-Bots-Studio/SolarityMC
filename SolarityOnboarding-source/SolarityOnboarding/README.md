# SolarityOnboarding

New-player onboarding and guide NPCs for Solarity's GenSMP. Paper and Folia 26.3, Java 25.

- An **onboarding guide** at spawn says 1-2 short lines and hands out the starter claim and gen
  (runs `/gens start`), then checks with LandClaim that the claim really exists.
- New players are pointed to it with an arrow, particles and an action bar; nobody else sees them.
- **Info guides** for the warzone, shops, crates ... with their own lines and click actions.
- Skins from a player name, a MineSkin link or any skin PNG link.
- No NPC plugin needed. Standalone: SolarityGens and LandClaim are optional soft dependencies.

## Quick start

```
/onboard npc create welcome onboarding
/onboard npc name welcome <gold><bold>Guide
/onboard npc skin welcome mineskin https://mineskin.org/skins/<id>
```

Full docs are in [`docs/SolarityOnboarding`](docs/SolarityOnboarding/intro.md), in the same layout as
PluginDocs, so the folder can be copied there as is.

## Building

```bash
mvn clean package
```

Output: `target/SolarityOnboarding-26.11.0.jar`.
