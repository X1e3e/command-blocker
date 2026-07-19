# Command Blocker

A robust Bukkit/Paper plugin designed to secure your Minecraft server by restricting player command execution, filtering tab-completions, and managing specific game roles with customizable command permissions.

## Features

- **Command Blocking:** Intercepts and blocks unauthorized command execution for default players.
- **Tab-Complete Filtering:** Automatically hides disallowed commands from auto-completion lists.
- **Role System:** Supports managing distinct game roles (e.g., Admin, Detective, Banker, Judge, Mayor).
- **Flexible Synchronization:** Sync player roles via direct SQLite/MySQL database access or through RCON site integration.
- **Admin Control:** In-game commands to reload configurations and grant/revoke player roles.

## Commands & Permissions

- `/blockcommand reload` — Reloads the plugin configuration. Requires `blockcommand.admin`.
- `/blockcommand grant <player> <role>` — Assigns a role to a player. Requires `blockcommand.admin`.
- `/blockcommand revoke <player> <role>` — Removes a role from a player. Requires `blockcommand.admin`.
- `/blockcommand list [player]` — Lists roles assigned to players. Requires `blockcommand.admin`.

## Configuration

Customize the allowed commands, roles, and database options in the default `config.yml`:

```yaml
# Sync Mode: "rcon" or "db"
sync-mode: "rcon"
database-path: "plugins/BlockCommand/users.db"

# Commands accessible to all players (without slash prefix)
allowed-commands:
  - msg
  - me
  - try
  - login
  - changepassword
  - register
  - help
```
