# Changelog

Versions follow Major.Minor.Patch followed by a build number: 1.0.0 - Build 1.

## 1.0.0 - Build 1 (2026-10-08)

First public release.

- Flight time per player, with configurable cost and wait between activations
- Unlimited flight with `skylease.unlimited`, shown as `∞`
- Gentle landing when time runs out
- Low-time alerts, chosen per player in `/fly settings`
- 33 particle effects with their own permissions and a paged menu
- Allowed and blocked worlds, and the WorldGuard flag `skylease-fly`
- Inactive players who still have flight time are not purged
- `/flyadmin` to add, remove, inspect, reload and debug
- PlaceholderAPI placeholders
- SQLite or MySQL/MariaDB; queries run off the main thread
- English (British) and Spanish in `lang/`, chosen in `config.yml`
- `/skylease about` for the credits
