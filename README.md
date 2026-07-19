# Command Blocker (RU/EN)

[Русский](#русский) | [English](#english)

---

## Русский

Гибкий и надежный плагин для ограничения ввода команд обычными игроками, скрытия запрещенных команд при автодополнении через Tab и распределения дополнительных разрешенных команд по игровым ролям (например: Админ, Детектив, Банкир, Судья, Мэр).

### Описание системы ролей
Вы можете настраивать роли для предоставления игрокам доступа к определенным командам.
- **Права по умолчанию**: Любой игрок с правом `blockcommand.role.<имя_роли>` автоматически получает доступ к командам роли.
- **Выдача ролей**: Командой `/blockcommand grant <игрок> <роль>` (работает в режиме `sync-mode: rcon`).
- **Синхронизация с базой данных**: В режиме `sync-mode: db` плагин автоматически считывает роли игроков из внешней базы данных.

### Команды
- `/blockcommand reload` — Перезагрузить конфигурацию. Требует `blockcommand.admin`.
- `/blockcommand grant <player> <role>` — Выдать роль игроку. Требует `blockcommand.admin`.
- `/blockcommand revoke <player> <role>` — Забрать роль у игрока. Требует `blockcommand.admin`.
- `/blockcommand list [player]` — Показать роли игрока. Требует `blockcommand.admin`.

### Настройка конфигурации (config.yml)
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

---

## English

A robust Bukkit/Paper plugin designed to secure your Minecraft server by restricting player command execution, filtering tab-completions, and managing specific game roles with customizable command permissions.

### Role System Configuration
You can customize roles to grant players access to additional commands.
- **Default Permissions**: Any player with permission node `blockcommand.role.<role_name>` automatically gets access to the role's commands.
- **Granting Roles**: Use `/blockcommand grant <player> <role>` (in `sync-mode: rcon` mode).
- **Database Sync**: In `sync-mode: db`, player roles are fetched dynamically from the database.

### Commands
- `/blockcommand reload` — Reload configuration. Requires `blockcommand.admin`.
- `/blockcommand grant <player> <role>` — Grant role to a player. Requires `blockcommand.admin`.
- `/blockcommand revoke <player> <role>` — Revoke role from a player. Requires `blockcommand.admin`.
- `/blockcommand list [player]` — List player roles. Requires `blockcommand.admin`.

### Configuration Example (config.yml)
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
