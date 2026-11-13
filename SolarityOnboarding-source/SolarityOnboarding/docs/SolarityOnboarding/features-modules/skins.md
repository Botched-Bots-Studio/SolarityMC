---
id: skins
title: Skins
sidebar_position: 4
---

Set a guide's skin with `/onboard npc skin <name> <kind> <value>`. The skin is fetched once, saved
signed into `guides.yml`, and never fetched again, so it keeps working if the website goes down.

| Kind | Example | Notes |
|---|---|---|
| `player` | `player Notch` | Copies a Minecraft account's current skin, from Mojang |
| `mineskin` | `mineskin https://mineskin.org/skins/7405aacd` | Any MineSkin link or id. Instant |
| `url` | `url https://example.com/skin.png` | Any direct link to a 64x64 skin PNG. MineSkin signs it, which takes a few seconds |
| `texture` | `texture <value> [signature]` | Paste a value and signature from elsewhere |
| `clear` | `clear` | Back to the default skin |

A bare link also works: `/onboard npc skin pvp https://mineskin.org/skins/<id>`.

## Getting skins from websites

- **MineSkin** ([mineskin.org](https://mineskin.org)): browse or upload, then copy the page link.
- **NameMC, The Skindex, Planet Minecraft**: right-click the skin's download button, copy the link to the
  PNG, and use `url`.

## MineSkin API key

Without a key, MineSkin allows about 10 skins a minute, which is plenty for setting up guides. If you
hit the limit, get a free key at [account.mineskin.org](https://account.mineskin.org) and put it in
`skins.mineskin-api-key`.
