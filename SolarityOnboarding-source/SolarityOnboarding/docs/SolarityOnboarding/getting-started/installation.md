---
id: installation
title: Installation
sidebar_position: 2
---

1. Stop the server.
2. Put `SolarityOnboarding-26.11.0.jar` in `plugins/`.
3. Start the server. The plugin writes `plugins/SolarityOnboarding/` with `config.yml`, `messages.yml`,
   `guides.yml` and `onboarding.db`.
4. Check the console for `SolarityOnboarding enabled`. If LandClaim isn't found you'll see a warning
   that claim checks are off.

To update, replace the jar and restart. Your guides, config and the list of onboarded players are kept.

## Building from source

```bash
mvn clean package
```

The jar is `target/SolarityOnboarding-26.11.0.jar`. It needs Java 25 and Maven 3.9+.
