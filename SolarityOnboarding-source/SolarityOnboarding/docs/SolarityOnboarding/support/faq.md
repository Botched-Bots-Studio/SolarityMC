---
id: faq
title: FAQ
sidebar_position: 1
---

**Does it need Citizens or another NPC plugin?**
No. Guides are vanilla mannequins.

**Does it change SolarityGens or LandClaim?**
No. It runs `/gens start` as the player and reads LandClaim's API to check the claim.

**A player clicked the guide and got "Something went wrong".**
`/gens start` ran but LandClaim never showed a claim within `timeout-seconds`. The console line says
what LandClaim answered. Usually it's `/gens start` failing (no space, no WorldGuard region, the player
has already started). Fix it, then let them click again, or use `/onboard complete <player>` if they
did get their claim.

**I wiped the season but players still get "already onboarded".**
Run `/onboard wipe` then `/onboard wipe confirm`. If they still own a claim and `existing-claim-counts`
is on, they're counted as onboarded again; that's on purpose.

**The guide disappeared.**
It comes back within `npc.check-seconds` once its chunk is loaded. If not, check `/onboard npc list` for
`(not loaded)`: that world isn't loaded.

**Can I use it without a guide?**
Yes. See [Without a guide](../features-modules/onboarding.md#without-a-guide).

**The skin command says I'm rate limited.**
Wait a minute, or add a free MineSkin key in `skins.mineskin-api-key`.

**Player skins don't work on my offline-mode server.**
They do: the plugin asks Mojang directly. The server just needs outgoing HTTPS.
