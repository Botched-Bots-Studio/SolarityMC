---
id: data-storage
title: Data Storage
sidebar_position: 2
---

| File | Holds |
|---|---|
| `onboarding.db` | SQLite: one row per onboarded player (UUID, name, when, how) |
| `guides.yml` | Every guide, with its signed skin |
| `config.yml`, `messages.yml` | Settings and text |

`onboarding.db` is read into memory on startup and written from a single background thread, so
clicks never wait on the disk. `guides.yml` is written to a temporary file and moved into place, so a
crash mid-save can't empty it.

To back up, copy the whole `plugins/SolarityOnboarding/` folder.
