# Skylease

Per-player flight time for Paper servers. [Español](README.es.md)

Each player has a balance of flight seconds. `/fly` switches flight on, the balance drops while they fly, and when it runs out they glide down instead of falling. Administrators add or remove time with a command, so it plugs into a store or a rewards system.

## Features

- Flight balance per player, with configurable cost and wait between activations
- Gentle landing when time runs out
- Low-time alerts that each player switches on or off
- 33 particle effects, each with its own permission
- Allowed and blocked worlds, and a WorldGuard region flag to deny flight
- PlaceholderAPI placeholders
- SQLite, or MySQL/MariaDB to share data between servers
- English and Spanish; add your own language in `lang/`

## Requirements

- Paper 1.21 or later
- Java 21

PlaceholderAPI and WorldGuard are optional.

## Installation

1. Download the JAR from [Releases](https://github.com/joshlucem/Skylease/releases).
2. Put it in `plugins` and restart.
3. On the first start Paper downloads the database drivers, so the server needs internet access.
4. Edit the files in `plugins/Skylease` and apply them with `/flyadmin reload`.

## Commands

| Command | Does | Permission |
|---|---|---|
| `/fly` | Switches your flight on or off | `skylease.user.fly` |
| `/fly settings` | Opens the alerts and effects menu | `skylease.user.menu.settings` |
| `/fly shop` | Shows how to get more time | `skylease.user.fly` |
| `/flyadmin add <player> <time>` | Adds time | `skylease.admin.command.add` |
| `/flyadmin remove <player> [time]` | Takes time away; with no amount, all of it | `skylease.admin.command.remove` |
| `/flyadmin info <player>` | Shows a player's data | `skylease.admin.command.info` |
| `/flyadmin reload` | Reloads the configuration | `skylease.admin.command.reload` |
| `/flyadmin debug on\|off` | Switches debug mode on or off | `skylease.admin.debug` |
| `/skylease about` | Shows the credits | `skylease.about` |

Times use `s`, `m`, `h`, `d`, `mo` (30 days) and `y` (365 days), alone or combined: `30s`, `2h`, `1h30m`.

## Other permissions

| Permission | Effect |
|---|---|
| `skylease.unlimited` | Fly without using time |
| `skylease.user.cooldown.bypass` | Skip the wait between activations |
| `skylease.admin.bypass-restrictions` | Fly in blocked worlds and regions |
| `skylease.admin.updates` | Be told about new versions on joining |
| `skylease.effects.<effect>` | Use one particle effect |
| `skylease.effects.*` | Use all of them |

## Configuration

| File | Holds |
|---|---|
| `config.yml` | Language, database, flight, alerts, placeholders |
| `lang/*.yml` | All text: messages, menus and effect names |
| `menus.yml` | Menu layout |
| `effects.yml` | The particle effects |

Set the language in `config.yml` under `language.selected`. To add one, copy `lang/en.yml`, translate it and use its name. Missing keys fall back to English. An empty message is not sent.

## WorldGuard

With WorldGuard installed, Skylease adds the region flag `skylease-fly`:

```
/rg flag <region> skylease-fly deny
```

Flight cannot be switched on inside that region, and anyone already flying who enters it glides gently down. Regions without the flag allow flight. `skylease.admin.bypass-restrictions` ignores it. Turn it off with `flight.worldguard-integration: false`.

## Placeholders

With PlaceholderAPI, for online players only.

| Placeholder | Gives |
|---|---|
| `%skylease_fly-time%` | Time left, such as `1h 30m`, or `∞` if unlimited |
| `%skylease_fly-time-seconds%` | Time left in seconds |
| `%skylease_fly-has-time%` | `yes` or `no` |
| `%skylease_fly-unlimited%` | `yes` or `no` |
| `%skylease_fly-status%` | Flying or on the ground |
| `%skylease_fly-effect%` / `-effect-name%` | The chosen effect |
| `%skylease_fly-alerts%` / `-alerts-list%` | Alerts switched on |
| `%skylease_fly-percentage%` / `-bar%` | Time against the configured maximum |
| `%skylease_fly-time-color%` | A colour code for the time left |

## Data

Per player, Skylease stores the UUID, name, time left, chosen effect and alerts. Nothing else.

With MySQL, several servers can share the same data. It is built for one server at a time per player: each server keeps its players in memory and writes them every `save-interval-seconds` and when they leave. On a network, a player who switches servers can lose changes made in the last few seconds on the server they left.

The update checker makes one request to the public GitHub API on start-up and sends nothing about your server or players. Turn it off with `update-checker.enabled: false`.

## Building

Needs Java 21 and Maven: `mvn package`. The JAR is in `target/`.

## Licence and credits

Open source under the [Apache License 2.0](LICENSES/Apache-2.0.txt) plus the [Credit Preservation Addendum](LICENSES/LicenseRef-Skylease-Credits.txt). You may use, change and redistribute it, commercially too.

The addendum asks one thing: keep the credits built into the plugin (the author in `plugin.yml`, file headers, `NOTICE`, `/skylease about` and the console banner), even in modified versions. A modified version must say so and keep the original author. Crediting the project on your download page is appreciated, not required.

Created by [joshlucem](https://github.com/joshlucem).
