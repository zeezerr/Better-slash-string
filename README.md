# Better /String

Paper plugin for Minecraft Java **1.21.11** (Java 21). `/string` fills every empty hotbar/main-inventory slot with a stack of string.

## Commands
| Command | Permission | What it does |
|---|---|---|
| `/string` | `betterstring.use` (default: everyone) | Fill empty slots (30 s cooldown) |
| `/string enable` | `betterstring.admin` (default: op) | Turn the feature on |
| `/string disable` | `betterstring.admin` | Turn the feature off |
| `/string status` | `betterstring.admin` | Show current state |

`betterstring.cooldown.bypass` (default: nobody) skips the cooldown.

Whether the feature is enabled is **remembered across restarts** (`state.yml`). Set `remember-enabled-state: false` in `config.yml` to make it always start disabled instead. Cooldowns are stored by UUID in `plugins/BetterString/cooldowns.yml` and survive restarts. Config is read at startup.

## Build
Automatically on GitHub (Actions tab), or locally with `mvn package` (JDK 21). The JAR is `target/BetterString-1.0.0.jar`.

## Install
Drop the JAR into your server's `plugins/` folder (Paper 1.21.11), restart, then run `/string enable` (only needed once).
