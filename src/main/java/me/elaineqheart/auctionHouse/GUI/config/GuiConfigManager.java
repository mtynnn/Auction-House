package me.elaineqheart.auctionHouse.GUI.config;

import me.elaineqheart.auctionHouse.AuctionHouse;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Central manager for all GUI configurations.
 * Loads and caches individual GuiConfig instances for each GUI.
 */
public class GuiConfigManager {

    private static final Map<String, GuiConfig> guiConfigs = new HashMap<>();

    // List of all GUI config files
    private static final String[] GUI_FILES = {
            "AuctionHouseGUI",
            "MyAuctionsGUI",
            "ConfirmBuyGUI",
            "ConfirmBidGUI",
            "CollectSoldItemGUI",
            "CollectExpiredItemGUI",
            "CancelAuctionGUI",
            "AdminConfirmGUI",
            "AuctionViewGUI",
            "MyBidsGUI",
            "EndedAuctionGUI",
            "AdminActionGUI",
            "AuctionItems"
    };

    /**
     * Load all GUI configuration files
     */
    public static void loadAll() {
        guiConfigs.clear();

        // Create gui folder if it doesn't exist
        File guiFolder = new File(AuctionHouse.getPlugin().getDataFolder(), "gui");
        if (!guiFolder.exists()) {
            guiFolder.mkdirs();
        }

        // Load each GUI config
        for (String guiName : GUI_FILES) {
            try {
                guiConfigs.put(guiName, new GuiConfig(guiName));
            } catch (Exception e) {
                AuctionHouse.getPlugin().getLogger().warning(
                        "Failed to load GUI config: " + guiName + ".yml - " + e.getMessage());
            }
        }

        AuctionHouse.getPlugin().getLogger().info("Loaded " + guiConfigs.size() + " GUI configurations");
    }

    /**
     * Reload all GUI configurations
     */
    public static void reloadAll() {
        for (GuiConfig config : guiConfigs.values()) {
            config.reload();
        }
    }

    /**
     * Reload a specific GUI configuration
     * 
     * @param guiName Name of the GUI to reload
     * @return true if successful, false if GUI not found
     */
    public static boolean reload(String guiName) {
        GuiConfig config = guiConfigs.get(guiName);
        if (config != null) {
            config.reload();
            return true;
        }
        return false;
    }

    /**
     * Get a GUI configuration
     * 
     * @param guiName Name of the GUI (e.g., "AuctionHouseGUI")
     * @return The GuiConfig, or null if not found
     */
    public static GuiConfig getConfig(String guiName) {
        return guiConfigs.get(guiName);
    }

    /**
     * Get a GUI configuration, creating it if it doesn't exist
     * 
     * @param guiName Name of the GUI
     * @return The GuiConfig
     */
    public static GuiConfig getOrCreate(String guiName) {
        GuiConfig config = guiConfigs.get(guiName);
        if (config == null) {
            config = new GuiConfig(guiName);
            guiConfigs.put(guiName, config);
        }
        return config;
    }

    /**
     * Check if a GUI configuration is loaded
     * 
     * @param guiName Name of the GUI
     * @return true if loaded
     */
    public static boolean isLoaded(String guiName) {
        return guiConfigs.containsKey(guiName);
    }

    /**
     * Get all loaded GUI names
     * 
     * @return Array of GUI names
     */
    public static String[] getLoadedGuis() {
        return guiConfigs.keySet().toArray(new String[0]);
    }

    /**
     * Convenience methods for common GUIs
     */
    public static GuiConfig auctionHouse() {
        return getConfig("AuctionHouseGUI");
    }

    public static GuiConfig myAuctions() {
        return getConfig("MyAuctionsGUI");
    }

    public static GuiConfig confirmBuy() {
        return getConfig("ConfirmBuyGUI");
    }

    public static GuiConfig confirmBid() {
        return getConfig("ConfirmBidGUI");
    }

    public static GuiConfig collectSold() {
        return getConfig("CollectSoldItemGUI");
    }

    public static GuiConfig collectExpired() {
        return getConfig("CollectExpiredItemGUI");
    }

    public static GuiConfig cancelAuction() {
        return getConfig("CancelAuctionGUI");
    }

    public static GuiConfig adminConfirm() {
        return getConfig("AdminConfirmGUI");
    }

    public static GuiConfig auctionView() {
        return getConfig("AuctionViewGUI");
    }

    public static GuiConfig myBids() {
        return getConfig("MyBidsGUI");
    }

    public static GuiConfig endedAuction() {
        return getConfig("EndedAuctionGUI");
    }

    public static GuiConfig auctionItems() {
        return getConfig("AuctionItems");
    }

    public static GuiConfig adminAction() {
        return getConfig("AdminActionGUI");
    }
}
