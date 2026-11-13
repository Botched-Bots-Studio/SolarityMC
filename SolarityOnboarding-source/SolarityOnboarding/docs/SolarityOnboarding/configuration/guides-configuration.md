---
id: guides-configuration
title: guides.yml
sidebar_position: 2
---

`plugins/SolarityOnboarding/guides.yml` holds every guide. The plugin writes it whenever a guide
changes in game, so you rarely need to touch it. If you do edit it by hand, run `/onboard reload`.

```yaml
guides:
  welcome:
    role: onboarding          # onboarding or info
    location:
      world: world
      x: 0.5
      y: 64.0
      z: 0.5
      yaw: 180.0
      pitch: 0.0
    name: "<gold><bold>Guide</bold></gold>"
    description: "<gray>Right-click me</gray>"   # remove for none
    guidance: true            # point new players to it (onboarding guides only)
    lines: []                 # empty: use intro-lines from config.yml
    actions: []
    skin:
      label: mineskin 7405aacd
      value: ewogICJ0aW1l...
      signature: e2uz7Jg+...
```

The key (`welcome`) is the guide's name in commands. Skins are stored signed; paste a `value` and
`signature` from anywhere to set one by hand.
