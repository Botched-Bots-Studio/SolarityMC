---
id: message-customization
title: Message Customization
sidebar_position: 3
---

Every message is in `plugins/SolarityOnboarding/messages.yml`, in MiniMessage. `%prefix%` is replaced
with the `prefix` key. Set a message to `""` to not send it.

Messages players see:

| Key | When |
|---|---|
| `onboarding-disabled` | Onboarding is turned off |
| `already-onboarded` | Clicking an onboarding guide again when `returning-lines` is empty, or `/onboard start` |
| `start-command-disabled` | `/onboard start` when it isn't allowed |
| `in-progress` | Clicking while onboarding is already running (empty by default) |
| `onboarding-failed` | The claim didn't show up in time |
| `onboarding-done` | Right after onboarding is confirmed (empty by default; `after-lines` cover it) |

The rest are admin command replies. The placeholders each one can use are already in the default file.
