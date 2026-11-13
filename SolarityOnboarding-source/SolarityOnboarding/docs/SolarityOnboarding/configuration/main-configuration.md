---
id: main-configuration
title: Main Configuration
sidebar_position: 1
---

`plugins/SolarityOnboarding/config.yml`. `/onboard reload` applies changes. Text uses
[MiniMessage](https://docs.advntr.dev/minimessage/format.html); `&` codes work in a line with no
MiniMessage tags.

## onboarding

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Turn onboarding off. Info guides keep working |
| `intro-lines` | 2 lines | Said before the claim is handed out, for guides with no `lines` of their own |
| `after-lines` | 1 line | Said once onboarding is done |
| `returning-lines` | 1 line | Said to players who have already onboarded. Empty sends `already-onboarded` instead |
| `start-actions` | `[player] gens start` | What hands out the claim. See [actions](../features-modules/guide-npcs.md#lines-and-actions) |
| `reward-actions` | none | Run once, after onboarding is confirmed (a kit, a crate key ...) |
| `verify.mode` | `landclaim` | `landclaim`: wait until LandClaim says they own a claim. `none`: done as soon as the start actions run |
| `verify.timeout-seconds` | `15` | How long to wait for the claim |
| `existing-claim-counts` | `true` | Players who already own a claim count as onboarded |
| `start-command` | `no-guide` | When `/onboard start` works: `never`, `no-guide` or `always` |
| `without-guide.auto-on-first-join` | `false` | Onboard new players on first join while no onboarding guide exists |
| `without-guide.delay-seconds` | `3` | How long after joining |

## dialogue

| Key | Default | Meaning |
|---|---|---|
| `style` | `chat` | `chat`, or `title` (a subtitle under the guide's name, also sent to chat) |
| `format` | `%guide% » %line%` | How a chat line looks |
| `default-name` | `Guide` | Who's speaking when there's no guide |
| `line-delay-ticks` | `50` | Gap between lines (20 ticks = 1 second) |
| `sound`, `sound-volume`, `sound-pitch` | villager | Sound on each line. Empty for none |
| `click-cooldown-ms` | `750` | Clicks closer together are ignored |

## guidance

See [Guidance](../features-modules/guidance.md). Every part has `enabled`.

| Key | Default | Meaning |
|---|---|---|
| `arrow.text` | `▼` | The lines above the guide |
| `arrow.height`, `arrow.scale` | `2.55`, `2.0` | Where and how big |
| `arrow.bob-distance`, `arrow.bob-ticks` | `0.25`, `20` | How far and how fast it bobs |
| `trail.particle` | `END_ROD` | Any particle that needs no extra data |
| `trail.length`, `trail.spacing` | `9.0`, `0.7` | Trail length and gap, in blocks |
| `trail.min-distance`, `trail.max-distance` | `4.0`, `200.0` | When the trail is drawn |
| `beacon.particle`, `beacon.radius`, `beacon.points` | `HAPPY_VILLAGER`, `0.8`, `10` | The ring at the guide's feet |
| `action-bar.text`, `action-bar.near-text` | | `%guide%` and `%distance%` work |
| `period-ticks` | `5` | How often guidance updates |

## npc

| Key | Default | Meaning |
|---|---|---|
| `look-distance` | `8.0` | Guides face the nearest player this close. `0` turns it off |
| `look-period-ticks` | `4` | How often they look around |
| `check-seconds` | `5` | How often missing guides are put back |

## skins

| Key | Default | Meaning |
|---|---|---|
| `mineskin-api-key` | empty | Optional, raises MineSkin's rate limit |
| `user-agent` | `SolarityOnboarding/26.11.0` | Sent to Mojang and MineSkin |
