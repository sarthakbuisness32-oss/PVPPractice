# PvPPractice 1.0.0

An original Paper 1.21.11 training plugin with a simple practice menu, difficulty presets, admin controls, and optional AuthMe Reloaded integration.

## Important current-version scope

This first build uses equipped adult Zombies as training opponents. They use vanilla mob AI and are **not player-shaped NPCs**; they are a lightweight starter implementation that compiles without Citizens or other dependencies. It does not claim to reproduce VexBot's proprietary implementation. A later release can add Citizens-based player NPCs and more advanced combat behavior.

## Requirements

- Paper 1.21.11
- Java 21
- Maven 3.8+
- AuthMe Reloaded recommended; it is an optional soft dependency

## Build

From this directory run:

```bash
mvn clean package
```

The output jar will be `target/PvPPractice-1.0.0.jar`.

## Install

1. Stop the server and back up your server folder.
2. Copy `target/PvPPractice-1.0.0.jar` into `plugins/`.
3. Keep AuthMe Reloaded in `plugins/` if your server uses it.
4. Start the server and check the console for `PvPPractice enabled`.
5. Edit `plugins/PvPPractice/config.yml` as needed, then restart.

## Commands

- `/pvppractice` or `/pvppractice menu` — open the practice menu.
- `/pvppractice bot [easy|normal|hard]` — spawn a bot.
- `/pvppractice difficulty <easy|normal|hard>` — set your default difficulty.
- `/pvppractice clear` — remove your own bots; admins can clear all bots in their current world.
- `/pvppractice help` — command help.

Aliases: `/practice`, `/pvppr`.

## Permissions

- `pvppractice.use` — use menu and spawn bots (default true).
- `pvppractice.admin` — admin permission; bypasses bot cap and allows clearing all bots in the current world (default OP).

## AuthMe integration

The plugin detects AuthMe by its Bukkit plugin name and reflectively queries `fr.xephi.authme.api.v3.AuthMeApi#getInstance().isAuthenticated(Player)`, avoiding a hard compile-time dependency. Commands and menu clicks are blocked until AuthMe confirms the player is authenticated. If AuthMe is installed but its API cannot be queried, access is denied rather than silently bypassing authentication. If AuthMe is not installed, practice features are allowed. Test this against the exact AuthMe Reloaded build you run.

## Difficulty presets

- Easy: 16 health, 0.20 movement speed, 2 attack damage, iron sword and partial iron armor.
- Normal: 24 health, 0.24 movement speed, 3 attack damage, diamond sword and partial iron armor.
- Hard: 32 health, 0.29 movement speed, 4.5 attack damage, netherite sword and partial diamond armor.

## Known limitations

- Bots use vanilla Zombie AI; they don't strafe like a human player or implement a custom combo system.
- The menu title is used to identify the inventory; avoid other plugins creating an identical title.
- Test on a staging server before installing on a public server. Server owners should verify AuthMe API behavior and permissions in their actual setup.
