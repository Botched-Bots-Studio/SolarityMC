---
id: onboarding
title: Onboarding
sidebar_position: 1
---

## The flow

When a player who hasn't onboarded right-clicks (or hits) an onboarding guide:

1. **They already own a claim?** With `existing-claim-counts: true` they're marked as onboarded and
   hear the returning lines. Nothing is given and their claim isn't touched.
2. **Intro lines.** The guide's own `lines`, or `intro-lines` from `config.yml` if it has none.
   Keep it to one or two short lines; players skip walls of text.
3. **Start actions.** `start-actions` run. By default that's `[player] gens start`, which makes
   SolarityGens give the claim and starter gens and teleport the player there.
4. **Check.** With `verify.mode: landclaim` the plugin asks LandClaim every half second whether the
   player now owns a claim, for up to `timeout-seconds`.
5. **Done.** The player is saved as onboarded, `reward-actions` run once, the guidance goes away,
   and `after-lines` play a moment later (after the teleport).

If the claim never shows up, the player gets `onboarding-failed`, the console logs why, and they stay
not-onboarded so they can click again. `/gens start` only works once per player, so clicking again
after a real failure needs staff to look first.

Clicking again while the lines are playing does nothing, and so does a double click.

## Players who have already onboarded

They hear `returning-lines`, then the guide's own click actions run (if it has any). Use that for a
"take me home" guide: `/onboard npc actions welcome add [player] gens home`.

## Without a guide

You don't need a guide at all:

- `start-command: no-guide` (the default) lets players use `/onboard start` until the first onboarding
  guide is made. `always` keeps it on; `never` turns it off.
- `without-guide.auto-on-first-join: true` onboards new players by themselves a few seconds after their
  first join, while no onboarding guide exists.

In both cases the lines are said by `dialogue.default-name`.

## New seasons

`/onboard wipe` then `/onboard wipe confirm` forgets every onboarded player. Pair it with the season reset
in SolarityGens, so `/gens start` works for everyone again. Players who keep a claim through the reset
are counted as onboarded again the first time they join (`existing-claim-counts`).

## When it's counted

A player is onboarded once, the first time any of these happens:

| Source shown in `/onboard status` | Meaning |
|---|---|
| the guide's name, e.g. `welcome` | Finished at that guide |
| `command` | Finished with `/onboard start` |
| `first-join` | Finished by the first-join fallback |
| `staff` | `/onboard complete` or `/onboard start <player>` |
| `existing-claim` | Already owned a claim |
