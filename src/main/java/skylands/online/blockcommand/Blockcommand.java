package skylands.online.blockcommand;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Level;

public final class Blockcommand extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private String syncMode;
    private String dbPath;

    private static class Role {
        final String name;
        final String permission;
        final List<String> commands;

        Role(String name, String permission, List<String> commands) {
            this.name = name;
            this.permission = permission;
            this.commands = commands;
        }
    }

    private Set<String> allowedCommands = new HashSet<>();
    private List<Role> roles = new ArrayList<>();
    private final Map<UUID, Set<String>> playerRoles = new HashMap<>();

    private File playersFile;
    private FileConfiguration playersConfig;

    private String bypassPermission;
    private String adminPermission;
    private String defaultLanguage;
    private final Map<String, FileConfiguration> langConfigs = new HashMap<>();

    @Override
    public void onEnable() {
        // Load configurations and player data
        loadPluginConfig();
        loadPlayersData();

        // Register commands
        Objects.requireNonNull(getCommand("blockcommand")).setExecutor(this);
        Objects.requireNonNull(getCommand("blockcommand")).setTabCompleter(this);

        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Save player data on shutdown
        savePlayersData();
    }

    /**
     * Loads/reloads configurations from config.yml
     */
    public void loadPluginConfig() {
        saveDefaultConfig();
        reloadConfig();
        FileConfiguration config = getConfig();
        loadLangConfigs();

        syncMode = config.getString("sync-mode", "rcon").toLowerCase();
        dbPath = config.getString("database-path", "plugins/BlockCommand/users.db");

        // Load global allowed commands
        allowedCommands = new HashSet<>();
        List<String> list = config.getStringList("allowed-commands");
        if (list != null) {
            for (String cmd : list) {
                if (cmd != null) {
                    allowedCommands.add(cmd.trim().toLowerCase());
                }
            }
        }

        // Load roles
        roles = new ArrayList<>();
        ConfigurationSection rolesSection = config.getConfigurationSection("roles");
        if (rolesSection != null) {
            for (String key : rolesSection.getKeys(false)) {
                ConfigurationSection roleSec = rolesSection.getConfigurationSection(key);
                if (roleSec != null) {
                    String perm = roleSec.getString("permission");
                    List<String> cmds = roleSec.getStringList("commands");
                    if (perm != null && cmds != null) {
                        List<String> cleanedCmds = new ArrayList<>();
                        for (String c : cmds) {
                            if (c != null) {
                                cleanedCmds.add(c.trim().toLowerCase());
                            }
                        }
                        roles.add(new Role(key.toLowerCase(), perm, cleanedCmds));
                    }
                }
            }
        }

        bypassPermission = config.getString("permissions.bypass", "blockcommand.bypass");
        adminPermission = config.getString("permissions.admin", "blockcommand.admin");
        defaultLanguage = config.getString("default-language", "ru").toLowerCase();
    }

    /**
     * Loads player roles from players.yml
     */
    public void loadPlayersData() {
        playersFile = new File(getDataFolder(), "players.yml");
        if (!playersFile.exists()) {
            try {
                playersFile.getParentFile().mkdirs();
                playersFile.createNewFile();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not create players.yml", e);
            }
        }
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);

        playerRoles.clear();
        ConfigurationSection section = playersConfig.getConfigurationSection("players");
        if (section != null) {
            for (String uuidStr : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    List<String> list = playersConfig.getStringList("players." + uuidStr + ".roles");
                    if (list != null) {
                        Set<String> rolesSet = new HashSet<>();
                        for (String r : list) {
                            if (r != null) {
                                rolesSet.add(r.trim().toLowerCase());
                            }
                        }
                        playerRoles.put(uuid, rolesSet);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /**
     * Saves player roles to players.yml
     */
    public void savePlayersData() {
        if (playersConfig == null || playersFile == null) return;

        playersConfig.set("players", null); // Reset structure
        for (Map.Entry<UUID, Set<String>> entry : playerRoles.entrySet()) {
            if (entry.getValue().isEmpty()) continue;

            String uuidStr = entry.getKey().toString();
            OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(entry.getKey());
            String name = offlinePlayer.getName();
            if (name != null) {
                playersConfig.set("players." + uuidStr + ".name", name);
            }
            playersConfig.set("players." + uuidStr + ".roles", new ArrayList<>(entry.getValue()));
        }

        try {
            playersConfig.save(playersFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save players.yml", e);
        }
    }

    private String translateColor(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private Set<String> getAvailableRoles() {
        Set<String> names = new HashSet<>();
        for (Role r : roles) {
            names.add(r.name);
        }
        return names;
    }
    private Set<String> queryRolesFromDB(UUID uuid, String name) {
        Set<String> rolesSet = new HashSet<>();
        File dbFile = new File(dbPath);
        if (!dbFile.exists()) {
            return rolesSet;
        }
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection(url)) {
                // 1. Query staff_roles
                String q1 = "SELECT role FROM staff_roles JOIN users ON staff_roles.discord_id = users.discord_id WHERE LOWER(users.username) = LOWER(?)";
                try (PreparedStatement ps = conn.prepareStatement(q1)) {
                    ps.setString(1, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rolesSet.add(rs.getString("role").trim().toLowerCase());
                        }
                    }
                }
                // 2. Query town ownership (mayor)
                String q2 = "SELECT 1 FROM towns JOIN users ON towns.owner_id = users.discord_id WHERE LOWER(users.username) = LOWER(?)";
                try (PreparedStatement ps = conn.prepareStatement(q2)) {
                    ps.setString(1, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            rolesSet.add("mayor");
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("Error querying roles from DB for " + name + ": " + e.getMessage());
        }
        return rolesSet;
    }

    private void loadPlayerRolesFromDB(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            Set<String> rolesSet = queryRolesFromDB(uuid, name);
            getServer().getScheduler().runTask(this, () -> {
                playerRoles.put(uuid, rolesSet);
                player.updateCommands();
            });
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if ("db".equals(syncMode)) {
            loadPlayerRolesFromDB(event.getPlayer());
        }
    }

    private boolean hasRole(Player player, String roleName) {
        String roleLower = roleName.toLowerCase();

        // 1. Check players cache
        Set<String> assigned = playerRoles.get(player.getUniqueId());
        if (assigned == null && "db".equals(syncMode)) {
            assigned = queryRolesFromDB(player.getUniqueId(), player.getName());
            playerRoles.put(player.getUniqueId(), assigned);
        }

        if (assigned != null && assigned.contains(roleLower)) {
            return true;
        }

        // 2. Check permission node (e.g. blockcommand.role.detective)
        for (Role r : roles) {
            if (r.name.equals(roleLower)) {
                if (r.permission != null && !r.permission.isEmpty() && player.hasPermission(r.permission)) {
                    return true;
                }
            }
        }

        return false;
    }
    public void loadLangConfigs() {
        langConfigs.clear();
        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }
        
        try {
            saveResource("lang/messages_ru.yml", false);
        } catch (Exception ignored) {}
        try {
            saveResource("lang/messages_en.yml", false);
        } catch (Exception ignored) {}

        File[] files = langFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().startsWith("messages_") && file.getName().endsWith(".yml")) {
                    String code = file.getName().substring(9, file.getName().length() - 4).toLowerCase();
                    FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                    langConfigs.put(code, cfg);
                }
            }
        }
    }

    /**
     * Resolves a localized message based on player locale setting.
     */
    private String getMessage(Player player, String messageKey, String defaultValue) {
        String locale = player != null ? player.getLocale().toLowerCase() : defaultLanguage;
        String lang = locale;
        if (locale.contains("_")) {
            lang = locale.split("_")[0];
        }

        // 1. Try full locale config
        FileConfiguration locCfg = langConfigs.get(locale);
        if (locCfg != null) {
            String msg = locCfg.getString("messages." + messageKey);
            if (msg != null) return msg;
        }

        // 2. Try short lang config
        FileConfiguration langCfg = langConfigs.get(lang);
        if (langCfg != null) {
            String msg = langCfg.getString("messages." + messageKey);
            if (msg != null) return msg;
        }

        // 3. Try default language
        FileConfiguration defCfg = langConfigs.get(defaultLanguage);
        if (defCfg != null) {
            String msg = defCfg.getString("messages." + messageKey);
            if (msg != null) return msg;
        }

        // 4. Try hardcoded ru fallback
        FileConfiguration ruCfg = langConfigs.get("ru");
        if (ruCfg != null) {
            String msg = ruCfg.getString("messages." + messageKey);
            if (msg != null) return msg;
        }

        return defaultValue;
    }

    /**
     * Resolves a command description localized for the player.
     */
    private String getCommandDescription(Player player, String pattern) {
        String locale = player != null ? player.getLocale().toLowerCase() : defaultLanguage;
        String lang = locale;
        if (locale.contains("_")) {
            lang = locale.split("_")[0];
        }

        String[] localesToCheck = { locale, lang, defaultLanguage, "ru" };

        for (String loc : localesToCheck) {
            FileConfiguration locCfg = langConfigs.get(loc);
            if (locCfg != null) {
                // Check exact pattern (e.g. "co i")
                String desc = locCfg.getString("help-descriptions." + pattern);
                if (desc != null) return desc;

                // Check root command (e.g. "co")
                String baseCmd = pattern.contains(" ") ? pattern.split(" ")[0] : pattern;
                desc = locCfg.getString("help-descriptions." + baseCmd);
                if (desc != null) return desc;
            }
        }

        // Fallback to server command description
        try {
            String baseCmd = pattern.contains(" ") ? pattern.split(" ")[0] : pattern;
            org.bukkit.command.Command serverCmd = getServer().getCommandMap().getCommand(baseCmd);
            if (serverCmd != null && serverCmd.getDescription() != null && !serverCmd.getDescription().isEmpty()) {
                return serverCmd.getDescription();
            }
        } catch (Throwable ignored) {
        }

        return getMessage(player, "no-description", "Описание отсутствует.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player playerSender = sender instanceof Player ? (Player) sender : null;

        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            
            if (sub.equals("reload")) {
                if (!sender.hasPermission(adminPermission)) {
                    sender.sendMessage(translateColor(getMessage(playerSender, "no-permission", "&cНедостаточно прав!")));
                    return true;
                }
                loadPluginConfig();
                loadPlayersData();
                sender.sendMessage(translateColor(getMessage(playerSender, "reload-success", "&aКонфигурация плагина BlockCommand успешно перезагружена!")));
                return true;
            }

            if (sub.equals("grant") || sub.equals("add")) {
                if (!sender.hasPermission(adminPermission)) {
                    sender.sendMessage(translateColor(getMessage(playerSender, "no-permission", "&cНедостаточно прав!")));
                    return true;
                }
                if ("db".equals(syncMode)) {
                    sender.sendMessage(translateColor("&cВы не можете изменять роли вручную, так как включен режим синхронизации с базой данных (sync-mode: db)!"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(translateColor("&cИспользование: /blockcommand grant <игрок> <роль>"));
                    return true;
                }
                String targetName = args[1];
                String roleName = args[2].toLowerCase();

                // Check if role exists
                if (!getAvailableRoles().contains(roleName)) {
                    sender.sendMessage(translateColor("&cРоль &7" + roleName + " &cне существует в конфигурации!"));
                    return true;
                }

                // Get offline player or online player
                Player onlinePlayer = getServer().getPlayer(targetName);
                UUID uuid;
                String actualName;
                if (onlinePlayer != null) {
                    uuid = onlinePlayer.getUniqueId();
                    actualName = onlinePlayer.getName();
                } else {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(targetName);
                    if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
                        sender.sendMessage(translateColor("&cИгрок &7" + targetName + " &cни разу не заходил на сервер!"));
                        return true;
                    }
                    uuid = offlinePlayer.getUniqueId();
                    actualName = offlinePlayer.getName() != null ? offlinePlayer.getName() : targetName;
                }

                // Grant role
                Set<String> assigned = playerRoles.computeIfAbsent(uuid, k -> new HashSet<>());
                if (assigned.contains(roleName)) {
                    sender.sendMessage(translateColor("&eУ игрока &a" + actualName + " &eуже есть роль &a" + roleName + "&e!"));
                    return true;
                }
                assigned.add(roleName);
                savePlayersData();

                // If online, force update command suggestions immediately
                if (onlinePlayer != null) {
                    onlinePlayer.updateCommands();
                }

                sender.sendMessage(translateColor("&aИгроку &e" + actualName + " &aвыдана роль &e" + roleName + "&a!"));
                return true;
            }

            if (sub.equals("revoke") || sub.equals("remove")) {
                if (!sender.hasPermission(adminPermission)) {
                    sender.sendMessage(translateColor(getMessage(playerSender, "no-permission", "&cНедостаточно прав!")));
                    return true;
                }
                if ("db".equals(syncMode)) {
                    sender.sendMessage(translateColor("&cВы не можете изменять роли вручную, так как включен режим синхронизации с базой данных (sync-mode: db)!"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(translateColor("&cИспользование: /blockcommand revoke <игрок> <роль>"));
                    return true;
                }
                String targetName = args[1];
                String roleName = args[2].toLowerCase();

                Player onlinePlayer = getServer().getPlayer(targetName);
                UUID uuid;
                String actualName;
                if (onlinePlayer != null) {
                    uuid = onlinePlayer.getUniqueId();
                    actualName = onlinePlayer.getName();
                } else {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(targetName);
                    if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
                        sender.sendMessage(translateColor("&cИгрок &7" + targetName + " &cни разу не заходил на сервер!"));
                        return true;
                    }
                    uuid = offlinePlayer.getUniqueId();
                    actualName = offlinePlayer.getName() != null ? offlinePlayer.getName() : targetName;
                }

                Set<String> assigned = playerRoles.get(uuid);
                if (assigned == null || !assigned.contains(roleName)) {
                    sender.sendMessage(translateColor("&cУ игрока &7" + actualName + " &cнет роли &7" + roleName + "&c!"));
                    return true;
                }
                assigned.remove(roleName);
                if (assigned.isEmpty()) {
                    playerRoles.remove(uuid);
                }
                savePlayersData();

                // If online, force update command suggestions immediately
                if (onlinePlayer != null) {
                    onlinePlayer.updateCommands();
                }

                sender.sendMessage(translateColor("&aУ игрока &e" + actualName + " &aзабрана роль &e" + roleName + "&a!"));
                return true;
            }

            if (sub.equals("list")) {
                if (!sender.hasPermission(adminPermission)) {
                    sender.sendMessage(translateColor(getMessage(playerSender, "no-permission", "&cНедостаточно прав!")));
                    return true;
                }

                if (args.length >= 2) {
                    String targetName = args[1];
                    Player onlinePlayer = getServer().getPlayer(targetName);
                    UUID uuid;
                    String actualName;
                    if (onlinePlayer != null) {
                        uuid = onlinePlayer.getUniqueId();
                        actualName = onlinePlayer.getName();
                    } else {
                        @SuppressWarnings("deprecation")
                        OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(targetName);
                        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
                            sender.sendMessage(translateColor("&cИгрок &7" + targetName + " &cни разу не заходил на сервер!"));
                            return true;
                        }
                        uuid = offlinePlayer.getUniqueId();
                        actualName = offlinePlayer.getName() != null ? offlinePlayer.getName() : targetName;
                    }

                    Set<String> assigned = playerRoles.get(uuid);
                    if (assigned == null || assigned.isEmpty()) {
                        sender.sendMessage(translateColor("&eУ игрока &a" + actualName + " &eнет выданных ролей."));
                        return true;
                    }

                    sender.sendMessage(translateColor("&aРоли игрока &e" + actualName + "&a: &e" + String.join(", ", assigned)));
                    return true;
                } else {
                    Set<String> available = getAvailableRoles();
                    if (available.isEmpty()) {
                        sender.sendMessage(translateColor("&eВ конфигурации нет доступных ролей."));
                        return true;
                    }
                    sender.sendMessage(translateColor("&aДоступные роли в конфиге: &e" + String.join(", ", available)));
                    return true;
                }
            }
        }

        sender.sendMessage(translateColor("&6&lДоступные команды BlockCommand:"));
        sender.sendMessage(translateColor("&e/bc reload &7- Перезагрузить конфигурацию и файлы данных."));
        sender.sendMessage(translateColor("&e/bc grant <игрок> <роль> &7- Выдать игроку роль."));
        sender.sendMessage(translateColor("&e/bc revoke <игрок> <роль> &7- Забрать у игрока роль."));
        sender.sendMessage(translateColor("&e/bc list [игрок] &7- Показать список ролей игрока или все доступные роли."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(adminPermission)) {
            return Collections.emptyList();
        }

        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String sub : Arrays.asList("reload", "grant", "revoke", "list")) {
                if (sub.startsWith(input)) {
                    list.add(sub);
                }
            }
            return list;
        }

        if (args.length == 2) {
            String input = args[1].toLowerCase();
            if (args[0].equalsIgnoreCase("grant") || args[0].equalsIgnoreCase("revoke") || args[0].equalsIgnoreCase("list")) {
                // Suggest online player names
                for (Player p : getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) {
                        list.add(p.getName());
                    }
                }
                return list;
            }
        }

        if (args.length == 3) {
            String input = args[2].toLowerCase();
            if (args[0].equalsIgnoreCase("grant") || args[0].equalsIgnoreCase("revoke")) {
                // Suggest available roles
                for (String role : getAvailableRoles()) {
                    if (role.toLowerCase().startsWith(input)) {
                        list.add(role);
                    }
                }
                return list;
            }
        }

        return Collections.emptyList();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // 1. Bypass check
        if (player.hasPermission(bypassPermission)) {
            return;
        }

        // 2. Clean and parse command
        String cleanedMessage = cleanMessage(event.getMessage());
        if (cleanedMessage.isEmpty()) {
            return;
        }

        // 3. Custom help override
        String[] parts = cleanedMessage.split("\\s+");
        String rootCommand = parts[0];
        if (rootCommand.equals("help") || rootCommand.equals("?")) {
            if (isAllowed(player, cleanedMessage)) {
                event.setCancelled(true);
                sendCustomHelp(player, cleanedMessage);
                return;
            }
        }

        // 4. Check if command is allowed
        if (!isAllowed(player, cleanedMessage)) {
            event.setCancelled(true);
            player.sendMessage(translateColor(getMessage(player, "blocked", "&cУ вас нет доступа к этой команде!")));
        }
    }

    private boolean playerHasServerPermission(Player player, String pattern) {
        String baseCmd = pattern.contains(" ") ? pattern.split(" ")[0] : pattern;
        try {
            org.bukkit.command.Command serverCmd = getServer().getCommandMap().getCommand(baseCmd);
            if (serverCmd != null) {
                // Check if player has permission for the command on the server
                return serverCmd.testPermissionSilent(player);
            }
        } catch (Throwable ignored) {
        }
        return true; // Fallback to true if command is not registered or checked
    }

    private void sendCustomHelp(Player player, String cleanedMessage) {
        Set<String> playerCommands = new TreeSet<>();

        // Add global allowed commands
        for (String pattern : allowedCommands) {
            if (playerHasServerPermission(player, pattern)) {
                playerCommands.add(pattern);
            }
        }

        // Add role allowed commands
        for (Role role : roles) {
            boolean playerHasRole = player.hasPermission(role.permission) || hasRole(player, role.name);
            if (playerHasRole) {
                for (String pattern : role.commands) {
                    if (playerHasServerPermission(player, pattern)) {
                        playerCommands.add(pattern);
                    }
                }
            }
        }

        // Add admin commands if player has permission
        if (player.hasPermission(adminPermission)) {
            if (playerHasServerPermission(player, "blockcommand")) {
                playerCommands.add("blockcommand reload");
                playerCommands.add("blockcommand grant");
                playerCommands.add("blockcommand revoke");
                playerCommands.add("blockcommand list");
            }
        }

        // Parse page number if present (e.g. /help 2)
        int page = 1;
        String[] parts = cleanedMessage.split("\\s+");
        if (parts.length > 1) {
            try {
                page = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        int itemsPerPage = 8;
        List<String> sortedCommands = new ArrayList<>(playerCommands);
        int totalCommands = sortedCommands.size();
        int totalPages = (int) Math.ceil((double) totalCommands / itemsPerPage);
        if (totalPages == 0) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        String header = getMessage(player, "help-header", "&8========================================\n&6&lДоступные команды &7(Страница %page% из %total%):")
                .replace("%page%", String.valueOf(page))
                .replace("%total%", String.valueOf(totalPages));
        player.sendMessage(translateColor(header));

        int start = (page - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, totalCommands);
        for (int i = start; i < end; i++) {
            String pattern = sortedCommands.get(i);
            String description = getCommandDescription(player, pattern);
            player.sendMessage(translateColor("&e/" + pattern + " &7- " + description));
        }

        String footer = getMessage(player, "help-footer", "&8========================================");
        player.sendMessage(translateColor(footer));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();

        // 1. Bypass check
        if (player.hasPermission(bypassPermission)) {
            return;
        }

        // 2. Filter commands list sent to the player
        event.getCommands().removeIf(cmd -> {
            // Remove ALL commands containing colon (namespaces like minecraft:me, slchat:me)
            if (cmd.contains(":")) {
                return true;
            }

            String cleanCommand = cmd.toLowerCase();

            // Keep plugin command if the player is admin
            if (cleanCommand.equals("blockcommand") || cleanCommand.equals("bc")) {
                return !player.hasPermission(adminPermission);
            }

            // Check if this root command (or any of its subcommands) is allowed globally
            for (String pattern : allowedCommands) {
                if (pattern.equals(cleanCommand) || pattern.startsWith(cleanCommand + " ")) {
                    return false; // Do NOT remove
                }
            }

            // Check if this root command (or any of its subcommands) is allowed by roles
            for (Role role : roles) {
                boolean playerHasRole = player.hasPermission(role.permission) || hasRole(player, role.name);
                if (playerHasRole) {
                    for (String pattern : role.commands) {
                        if (pattern.equals(cleanCommand) || pattern.startsWith(cleanCommand + " ")) {
                            return false; // Do NOT remove
                        }
                    }
                }
            }

            return true; // Remove
        });
    }

    private String cleanMessage(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }
        int slashCount = 0;
        while (slashCount < rawMessage.length() && rawMessage.charAt(slashCount) == '/') {
            slashCount++;
        }
        String message = rawMessage.substring(slashCount).trim();
        if (message.isEmpty()) {
            return "";
        }

        String[] parts = message.split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return "";
        }

        String firstWord = parts[0].toLowerCase();
        if (firstWord.contains(":")) {
            firstWord = firstWord.substring(firstWord.indexOf(':') + 1);
        }

        // Reconstruct message with namespace-stripped first word
        StringBuilder sb = new StringBuilder(firstWord);
        for (int i = 1; i < parts.length; i++) {
            sb.append(" ").append(parts[i]);
        }
        return sb.toString().toLowerCase();
    }

    private boolean isAllowed(Player player, String cleanedMessage) {
        String[] parts = cleanedMessage.split("\\s+");
        String rootCommand = parts[0];

        // Special check for plugin command
        if (rootCommand.equals("blockcommand") || rootCommand.equals("bc")) {
            if (player.hasPermission(adminPermission)) {
                return true;
            }
        }

        // Check global whitelist
        for (String pattern : allowedCommands) {
            if (matches(cleanedMessage, pattern)) {
                return true;
            }
        }

        // Check role-based whitelists
        for (Role role : roles) {
            boolean playerHasRole = player.hasPermission(role.permission) || hasRole(player, role.name);
            if (playerHasRole) {
                for (String pattern : role.commands) {
                    if (matches(cleanedMessage, pattern)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean matches(String typed, String pattern) {
        return typed.equals(pattern) || typed.startsWith(pattern + " ");
    }
}
