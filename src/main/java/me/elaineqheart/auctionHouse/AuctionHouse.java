package me.elaineqheart.auctionHouse;

import me.elaineqheart.auctionHouse.GUI.GUIListener;
import me.elaineqheart.auctionHouse.GUI.GUIManager;

import me.elaineqheart.auctionHouse.commands.DynamicCommandRegisterer;
import me.elaineqheart.auctionHouse.manager.AuctionManager;
import me.elaineqheart.auctionHouse.configuration.ConfigManager;
import me.elaineqheart.auctionHouse.configuration.SlotConfigManager;
import me.elaineqheart.auctionHouse.listeners.UserSessionListener;
import me.elaineqheart.auctionHouse.listeners.PlayerJoinCollectListener;
import me.elaineqheart.auctionHouse.world.displays.DisplayListener;
import me.elaineqheart.auctionHouse.world.displays.KillListener;
import me.elaineqheart.auctionHouse.world.displays.UpdateDisplay;
import me.elaineqheart.auctionHouse.world.npc.NPCListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import me.elaineqheart.auctionHouse.model.UserSession;

public final class AuctionHouse extends JavaPlugin {

    private static AuctionHouse instance;
    private static GUIManager guiManager;
    private me.elaineqheart.auctionHouse.database.DatabaseManager databaseManager;

    public static AuctionHouse getPlugin() {
        return instance;
    }

    public static GUIManager getGuiManager() {
        return guiManager;
    }

    public me.elaineqheart.auctionHouse.database.DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public static boolean localeAPI;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        Plugin localeAPIPlugin = Bukkit.getPluginManager().getPlugin("Locale-API");
        if (localeAPIPlugin != null && localeAPIPlugin.isEnabled())
            localeAPI = true;
        instance = this;

        // Ensure data folders exist (PlugMan may not create them)
        getDataFolder().mkdirs();
        new java.io.File(getDataFolder(), "data").mkdirs();
        new java.io.File(getDataFolder(), "gui").mkdirs();

        guiManager = new GUIManager();
        GUIListener guiListener = new GUIListener(guiManager);
        Bukkit.getPluginManager().registerEvents(guiListener, this);

        // Load configs BEFORE anything that depends on SettingManager/M
        ConfigManager.setupConfigs();
        me.elaineqheart.auctionHouse.configuration.SettingManager.loadData();

        databaseManager = new me.elaineqheart.auctionHouse.database.DatabaseManager(this);
        databaseManager.initialize();

        // CRITICAL: Check if database actually initialized successfully before proceeding
        // This prevents NoClassDefFoundError when PlugMan reloads and DB fails
        if (!databaseManager.isInitialized()) {
            getLogger().severe("[PlugMan-Compatible] Database not initialized - stopping onEnable to prevent ClassNotFoundException");
            return; // Exit early - onDisable will be called by Bukkit after disablePlugin()
        }

        // Load DB-dependent data (banned players, blacklist, migrations)
        // Must happen AFTER databaseManager is initialized
        try {
            ConfigManager.loadServerData();
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().severe("[PlugMan-Compatible] Could not load server data: " + e.getMessage());
            getLogger().severe("[PlugMan-Compatible] This usually means database initialization failed.");
            return;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            Bukkit.getLogger().severe("[PlugMan-Compatible] No registered Vault provider found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Wrap ALL listener registrations in try-catch to prevent
        // cascading ClassNotFoundException from PlugMan classloader issues
        try {
            Bukkit.getPluginManager().registerEvents(new NPCListener(), this);
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().warning("Could not register NPCListener: " + e.getMessage());
        }
        try {
            Bukkit.getPluginManager().registerEvents(new DisplayListener(), this);
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().warning("Could not register DisplayListener: " + e.getMessage());
        }
        try {
            Bukkit.getPluginManager().registerEvents(new PlayerJoinCollectListener(), this);
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().warning("Could not register PlayerJoinCollectListener: " + e.getMessage());
        }
        try {
            Bukkit.getPluginManager().registerEvents(new UserSessionListener(), this);
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().warning("Could not register UserSessionListener: " + e.getMessage());
        }
        try {
            KillListener.register();
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().warning("Could not register KillListener: " + e.getMessage());
        }

        // Load auctions asynchronously to not block server startup
        getLogger().info("Loading auctions in background...");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                long asyncStart = System.currentTimeMillis();
                AuctionManager.getInstance().loadAuctions();
                AuctionManager.getInstance().setLoaded(true);
                long duration = System.currentTimeMillis() - asyncStart;
                getLogger().info("Loaded auctions in " + duration + "ms (async)");
            } catch (Exception e) {
                getLogger().severe("Failed to load auctions: " + e.getMessage());
                e.printStackTrace();
            }
        });

        try {
            DynamicCommandRegisterer.init();
        } catch (Exception e) {
            getLogger().warning("Could not register commands: " + e.getMessage());
        }
        try {
            UpdateDisplay.init();
        } catch (Exception e) {
            getLogger().warning("Could not init displays: " + e.getMessage());
        }

        // Register PlaceholderAPI expansion if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new me.elaineqheart.auctionHouse.placeholder.AuctionHousePlaceholders(this).register();
                getLogger().info("PlaceholderAPI expansion registered successfully");
            } catch (Exception e) {
                getLogger().warning("Could not register PlaceholderAPI expansion: " + e.getMessage());
            }
        }

        getLogger().info("AuctionHouse enabled in " + (System.currentTimeMillis() - start) + "ms");
    }

    @Override
    public void onDisable() {
        getLogger().info("[PlugMan-Compatible] Starting clean shutdown...");
        
        // Each step is wrapped in try-catch to prevent cascading failures
        // when onDisable is called during a failed onEnable (e.g. DB not available)

        // 1. Save player preferences before clearing sessions
        try {
            if (ConfigManager.playerPreferences != null) {
                ConfigManager.playerPreferences.disable();
                getLogger().info("[PlugMan-Compatible] Player preferences saved");
            }
        } catch (Exception e) {
            getLogger().warning("Could not save player preferences: " + e.getMessage());
        }

        // 2. Close all open GUIs
        try {
            if (guiManager != null) {
                guiManager.forceCloseAll();
                getLogger().info("[PlugMan-Compatible] GUIs closed");
            }
        } catch (Exception e) {
            getLogger().warning("Could not close GUIs: " + e.getMessage());
        }

        // 3. Force a synchronous DB snapshot before killing async tasks.
        try {
            AuctionManager.getInstance().flushToDatabaseSync();
            getLogger().info("[PlugMan-Compatible] Auction snapshot persisted");
        } catch (Exception e) {
            getLogger().warning("Could not persist auction snapshot: " + e.getMessage());
        }

        // 4. Cancel ALL scheduled tasks (display updater, GUI refresh timers, etc.)
        try {
            TaskManager.cancelAll();
            Bukkit.getScheduler().cancelTasks(this);
            getLogger().info("[PlugMan-Compatible] All tasks cancelled (" + TaskManager.getTaskCount() + " tracked tasks)");
        } catch (Exception e) {
            getLogger().warning("Could not cancel tasks: " + e.getMessage());
        }

        // 5. Clear user sessions
        try {
            UserSession.clearAll();
            getLogger().info("[PlugMan-Compatible] User sessions cleared");
        } catch (Exception e) {
            getLogger().warning("Could not clear sessions: " + e.getMessage());
        }

        // 6. Clear display caches
        try {
            UpdateDisplay.clearAll();
            getLogger().info("[PlugMan-Compatible] Display caches cleared");
        } catch (Exception e) {
            getLogger().warning("Could not clear displays: " + e.getMessage());
        }

        // 7. Reset AuctionManager singleton (clears all RAM caches)
        try {
            AuctionManager.resetInstance();
            getLogger().info("[PlugMan-Compatible] AuctionManager reset");
        } catch (Exception e) {
            getLogger().warning("Could not reset AuctionManager: " + e.getMessage());
        }

        // 8. Clear configuration caches
        try {
            SlotConfigManager.clearCaches();
            me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager.clearCaches();
            getLogger().info("[PlugMan-Compatible] Configuration caches cleared");
        } catch (Exception e) {
            getLogger().warning("Could not clear config caches: " + e.getMessage());
        }

        // 9. Unregister all event listeners registered by this plugin
        try {
            org.bukkit.event.HandlerList.unregisterAll(this);
            getLogger().info("[PlugMan-Compatible] Event handlers unregistered");
        } catch (Exception e) {
            getLogger().warning("Could not unregister listeners: " + e.getMessage());
        }

        // 10. Close database connection pool (HikariCP)
        try {
            if (databaseManager != null) {
                databaseManager.close();
                getLogger().info("[PlugMan-Compatible] Database connections closed");
            }
        } catch (Exception e) {
            getLogger().warning("Could not close database: " + e.getMessage());
        }

        // 10. Null static references so classloader can be GC'd
        guiManager = null;
        instance = null;
        databaseManager = null;
        
        getLogger().info("[PlugMan-Compatible] Clean shutdown completed - plugin ready for reload");
    }

}
