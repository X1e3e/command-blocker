# Command Blocker

A robust Bukkit/Paper plugin designed to secure your Minecraft server by restricting player command execution, filtering tab-completions, and managing specific game roles with customizable command permissions.

## Features

- **Command Blocking:** Intercepts and blocks unauthorized command execution for default players.
- **Tab-Complete Filtering:** Automatically hides disallowed commands from auto-completion lists.
- **Role System:** Supports managing distinct game roles (e.g., Admin, Detective, Banker, Judge, Mayor).
- **Flexible Synchronization:** Sync player roles via direct SQLite/MySQL database access or through RCON site integration.
- **Admin Control:** In-game commands to reload configurations and grant/revoke player roles.

## Commands & Permissions

- `/blockcommand reload` (alias: `/bc reload`) — Reloads the plugin configuration. Requires `blockcommand.admin`.
- `/blockcommand grant <player> <role>` (alias: `/bc grant`) — Assigns a role to a player. Requires `blockcommand.admin`.
- `/blockcommand revoke <player> <role>` (alias: `/bc revoke`) — Removes a role from a player. Requires `blockcommand.admin`.
- `/blockcommand list [player]` (alias: `/bc list`) — Lists roles assigned to players. Requires `blockcommand.admin`.

## Configuration

Customize the allowed commands, roles, and database options in the default `config.yml`:

```yaml
# Sync Mode:
# "rcon" (roles are managed via in-game /bc commands and stored in local players.yml)
# "db" (roles are read dynamically from the SQLite/MySQL database specified in database-path)
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

# Role definitions
roles:
  detective:
    permission: "blockcommand.role.detective"
    commands:
      - "co i"
      - "co inspect"
  banker:
    permission: "blockcommand.role.banker"
    commands:
      - "deposit"
      - "withdraw"
```

## Role System Configuration

You can customize roles to grant players access to additional commands that are otherwise blocked.

### 1. Defining a Role
Roles are defined under the `roles` section of the `config.yml` file. Every role consists of:
- **Role name:** The key under the `roles` node (e.g., `detective`).
- **Permission node:** A custom permission string (e.g., `blockcommand.role.detective`). Any player holding this permission node will automatically receive access to the role's commands.
- **Commands list:** A list of permitted commands (without the slash `/` prefix).
  - If a plain command is specified (e.g., `spawn`), it allows execution of the full command.
  - If sub-commands are specified (e.g., `co i`), it restricts the permission to commands matching that exact start.

### 2. Granting Roles to Players
Depending on the `sync-mode` set in your configuration, roles are assigned in different ways:

- **Local Mode (`sync-mode: "rcon"`)**:
  Assign roles directly using the in-game command:
  ```bash
  /blockcommand grant <player> <role_name>
  ```
  This creates/updates the `players.yml` file inside the plugin data folder, saving role assignments.
  
- **Database Mode (`sync-mode: "db"`)**:
  Roles are fetched dynamically from the database specified in `database-path`. Real-time checks are performed, preventing manual in-game edits to guarantee website/sync compatibility.

### 3. Bypass Permission
If you want to grant a player (such as moderators or admins) access to execute all commands bypassing the blocker entirely, grant them:
- `blockcommand.bypass` (customizable via `permissions.bypass` in the configuration).
