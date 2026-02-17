package me.elaineqheart.auctionHouse.configuration;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager;
import me.elaineqheart.auctionHouse.configuration.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import me.elaineqheart.auctionHouse.database.dao.PlayerPreferencesDAO;
import me.elaineqheart.auctionHouse.model.UserSession;
import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigManager {

    public static M messages = new M();
    public static Config displays = new Config();
    public static BannedPlayers bannedPlayers = new BannedPlayers();
    public static Permissions permissions = new Permissions();
    public static Blacklist blacklist = new Blacklist();
    public static Config categories = new Config();
    public static PlayerPreferences playerPreferences = new PlayerPreferences();
    public static TransactionLogger transactionLogger = new TransactionLogger();
    private static final List<Config> list = new ArrayList<>();

    public static void setupConfigs() {
        // Setup config.yml
        AuctionHouse.getPlugin().reloadConfig();
        AuctionHouse.getPlugin().getConfig().options().copyDefaults(true);
        try {
            AuctionHouse.getPlugin().saveConfig();
        } catch (Exception e) {
            AuctionHouse.getPlugin().getLogger()
                    .warning("Could not save config.yml (file may be locked): " + e.getMessage());
        }
        messages.setup("messages.yml", true, "");
        displays.setup("displays.yml", false, "/data");
        permissions.setup("permissions.yml", true, "");
        categories.setup("categories.yml", false, "/data");

        // Load new modular config system
        SlotConfigManager.load();
        SoundsConfig.load();
        GuiConfigManager.loadAll();

        Bukkit.getScheduler().runTask(AuctionHouse.getPlugin(), ConfigManager::displaysBackwardsCompatibility);
        permissionsSetup();
    }

    /**
     * Load server data from DB (banned players, blacklist) and run migrations.
     * Must be called AFTER DatabaseManager is initialized.
     */
    public static void loadServerData() {
        // Load Banned Players and Blacklist from DB
        new me.elaineqheart.auctionHouse.database.dao.ServerDataDAO().loadAll();

        // Load player preferences for online players (e.g. on reload)
        playerPreferences.loadAll();

        // Migration Check
        checkMigrations();
    }

    private static void checkMigrations() {
        File dataFolder = new File(AuctionHouse.getPlugin().getDataFolder(), "data");
        File rootFolder = AuctionHouse.getPlugin().getDataFolder();
        com.google.gson.Gson gson = new com.google.gson.Gson();

        migrateFile(rootFolder, dataFolder, "bannedPlayers.yml");
        migrateFile(rootFolder, dataFolder, "blacklist.yml");
        migrateFile(rootFolder, dataFolder, "playerPreferences.yml");

        // 1. Banned Players Migration
        File bannedFile = new File(dataFolder, "bannedPlayers.yml");
        if (bannedFile.exists()) {
            AuctionHouse.getPlugin().getLogger().info("Migrating legacy bannedPlayers.yml...");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(bannedFile);
            java.util.Map<java.util.UUID, BannedPlayers.BanEntry> bans = new java.util.HashMap<>();
            if (yaml.getConfigurationSection("bans") != null) {
                for (String key : yaml.getConfigurationSection("bans").getKeys(false)) {
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(key);
                        long end = yaml.getLong("bans." + key + ".end");
                        String name = yaml.getString("bans." + key + ".name");
                        String reason = yaml.getString("bans." + key + ".reason");
                        bans.put(uuid, new BannedPlayers.BanEntry(end, name, reason));
                    } catch (Exception ignored) {
                    }
                }
            }
            if (!bans.isEmpty()) {
                new me.elaineqheart.auctionHouse.database.dao.ServerDataDAO().saveBannedPlayers(bans);
                BannedPlayers.setBans(bans);
            }
            bannedFile.renameTo(new File(dataFolder, "bannedPlayers.yml.bak"));
        }

        // 2. Blacklist Migration
        File blacklistFile = new File(dataFolder, "blacklist.yml");
        if (blacklistFile.exists()) {
            AuctionHouse.getPlugin().getLogger().info("Migrating legacy blacklist.yml...");
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(blacklistFile);
                List<java.util.Map<String, Object>> entries = new ArrayList<>();
                if (yaml.getList("blacklist") != null) {
                    for (Object obj : yaml.getList("blacklist")) {
                        if (obj instanceof java.util.Map) {
                            java.util.Map<String, Object> entry = (java.util.Map<String, Object>) obj;
                            java.util.Map<String, Object> newEntry = new java.util.HashMap<>(entry);

                            // Essential fix: If the key is an ItemStack, encode it to Base64!
                            // Gson cannot handle Spigot's internal structures directly (Optional, NMS,
                            // etc.)
                            if (newEntry.get("key") instanceof org.bukkit.inventory.ItemStack) {
                                newEntry.put("key", me.elaineqheart.auctionHouse.util.ItemStackConverter
                                        .encode((org.bukkit.inventory.ItemStack) newEntry.get("key")));
                                newEntry.put("isItem", true);
                            }
                            entries.add(newEntry);
                        }
                    }
                }
                if (!entries.isEmpty()) {
                    new me.elaineqheart.auctionHouse.database.dao.ServerDataDAO().saveBlacklist(entries);
                    Blacklist.setBlacklist(entries);
                }
            } catch (Exception e) {
                AuctionHouse.getPlugin().getLogger().severe(
                        "Failed to migrate blacklist.yml! Is there a missing plugin dependency or corrupted item data?");
                e.printStackTrace();
            }
            blacklistFile.renameTo(new File(dataFolder, "blacklist.yml.bak"));
        }

        // 3. Player Preferences Migration
        File prefsFile = new File(dataFolder, "playerPreferences.yml");
        if (prefsFile.exists()) {
            AuctionHouse.getPlugin().getLogger().info("Migrating legacy playerPreferences.yml...");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(prefsFile);
            PlayerPreferencesDAO prefsDao = new PlayerPreferencesDAO();
            for (String key : yaml.getKeys(false)) {
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(key);
                    UserSession session = new UserSession();
                    if (yaml.contains(key + ".announcements"))
                        session.setAnnouncementsEnabled(yaml.getBoolean(key + ".announcements"));
                    prefsDao.save(uuid, gson.toJson(session));
                } catch (Exception ignored) {
                }
            }
            prefsFile.renameTo(new File(dataFolder, "playerPreferences.yml.bak"));
        }
    }

    private static void migrateFile(File root, File data, String name) {
        File oldFile = new File(root, name);
        File newFile = new File(data, name);
        if (oldFile.exists() && !newFile.exists()) {
            if (!data.exists())
                data.mkdirs();
            oldFile.renameTo(newFile);
        }
    }

    public static boolean backwardsCompatibility() {
        FileConfiguration c = AuctionHouse.getPlugin().getConfig();
        if (c.getString("plugin-version") == null)
            return true;
        return !Objects.equals(c.getString("plugin-version"), AuctionHouse.getPlugin().getDescription().getVersion());
    }

    public static void reloadConfigs() {
        AuctionHouse.getPlugin().reloadConfig();
        try {
            AuctionHouse.getPlugin().getConfig().options().copyDefaults(true);
            AuctionHouse.getPlugin().saveConfig();
        } catch (Exception e) {
            AuctionHouse.getPlugin().getLogger().warning("Could not save config.yml on reload: " + e.getMessage());
        }
        getList().forEach(Config::reload);
    }

    private static List<Config> getList() {
        if (list.isEmpty())
            list.addAll(List.of(messages, displays, permissions, categories));
        return list;

    }

    private static void permissionsSetup() {
        if (permissions.getCustomFile().getConfigurationSection("auction-slots") == null) {
            permissions.getCustomFile().createSection("auction-slots");
            permissions.save();
        }
        if (permissions.getCustomFile().getConfigurationSection("bin-auction-duration") == null) {
            permissions.getCustomFile().createSection("bin-auction-duration");
            permissions.save();
        }
        if (permissions.getCustomFile().getConfigurationSection("bid-auction-duration") == null) {
            permissions.getCustomFile().createSection("bid-auction-duration");
            permissions.save();
        }
    }

    private static void displaysBackwardsCompatibility() {
        Set<Integer> oldSet = null;
        FileConfiguration customFile = displays.getCustomFile();
        try {
            // This method is for backwards compatibility
            oldSet = customFile.getKeys(false).stream()
                    .map(Integer::parseInt)
                    .collect(Collectors.toSet());
        } catch (NumberFormatException ignored) {
        }

        // This section of code is needed, even without backwards compatibility
        if (customFile.getConfigurationSection("displays") == null) {
            customFile.createSection("displays");
        }

        if (oldSet != null) {
            for (Integer displayID : oldSet) {
                Objects.requireNonNull(customFile.getConfigurationSection("displays")).set(String.valueOf(displayID),
                        customFile.get(String.valueOf(displayID)));
                customFile.set(String.valueOf(displayID), null); // Remove the old key
            }
        }
        displays.save();
    }

    public static boolean oldVersion21() {
        String version = Bukkit.getServer().getVersion();
        List<String> oldVersions = List.of("1.21.4", "1.21.3", "1.21.2", "1.21.1");
        for (String oldVersion : oldVersions) {
            if (version.contains(oldVersion))
                return true;
        }
        return false;
    }

}
