package me.elaineqheart.auctionHouse.configuration;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.configuration.M;
import me.elaineqheart.auctionHouse.configuration.ConfigManager;

import org.bukkit.configuration.file.FileConfiguration;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;

public class SettingManager {

    // General settings
    public static double taxRate;
    public static long auctionSetupTime;
    public static DecimalFormat formatter;
    public static int defaultMaxAuctions;
    public static String permissionModerate;

    // Notification settings
    public static boolean soldMessageEnabled;
    public static boolean autoCollect;
    public static boolean auctionAnnouncementsEnabled;

    // GUI settings
    public static boolean useSignGUI;
    public static List<String> searchSignLines;
    public static boolean partialSelling;
    public static int displayUpdateTicks;

    // BIN auction settings
    public static boolean BINAuctions;
    public static long BINAuctionDuration; // in seconds, -1 for infinite
    public static double minBINPrice;
    public static double maxPrice;

    // Price protection settings
    public static boolean priceProtectionEnabled;
    public static double priceProtectionMultiplier;
    public static int priceProtectionMinSales;

    // BID auction settings
    public static boolean BIDAuctions;
    public static long BIDAuctionDuration; // in seconds, -1 for infinite
    public static int lastBIDExtraTime;
    public static double bidIncreaseRatio;
    public static double minBIDPrice;

    // Expiration settings
    public static int expiredItemCheckInterval; // in minutes
    public static int expiredItemRetentionDays;

    // Format settings
    public static String formatMoneyK;
    public static String formatMoneyM;
    public static String formatMoneyB;
    public static String formatMoneyT;
    public static boolean formatShowDecimals;

    // Debug settings
    public static boolean debugEnabled;
    public static boolean traceVisibleLore;

    // Time units (Small Caps)
    public static String timeUnitDays;
    public static String timeUnitDay;
    public static String timeUnitHours;
    public static String timeUnitHour;
    public static String timeUnitMinutes;
    public static String timeUnitMinute;
    public static String timeUnitSeconds;
    public static String timeUnitSecond;
    public static String timeUnitNever;

    // Legacy sound fields (kept for compatibility, now handled by SoundsConfig)
    public static String soundClick;
    public static String soundOpenEnderchest;
    public static String soundCloseEnderchest;
    public static String soundBreakWood;
    public static String soundExperience;
    public static String soundVillagerDeny;
    public static String soundOpenShulker;
    public static String soundCloseShulker;
    public static String soundNPCClick;

    static {
        try {
            loadData();
        } catch (Exception e) {
            AuctionHouse.getPlugin().getLogger().warning("Could not fully load settings: " + e.getMessage());
        }
    }

    public static void loadData() {
        AuctionHouse.getPlugin().reloadConfig();
        FileConfiguration c = AuctionHouse.getPlugin().getConfig();

        // General settings
        taxRate = 0.0; // Tax system removed, replaced with transaction history
        auctionSetupTime = c.getLong("auction-setup-time", 30);
        defaultMaxAuctions = c.getInt("default-max-auctions", 10);
        permissionModerate = c.getString("admin-permission", "auctionhouse.admin");

        // Notification settings
        soldMessageEnabled = c.getBoolean("sold-message", true);
        autoCollect = c.getBoolean("auto-collect", false);
        auctionAnnouncementsEnabled = c.getBoolean("auction-announcements", true);

        // GUI settings
        useSignGUI = c.getBoolean("use-sign-gui", true);
        searchSignLines = c.getStringList("search-sign.lines");
        partialSelling = c.getBoolean("partial-selling", false);
        displayUpdateTicks = c.getInt("display-update", 20);

        // BIN auction settings - now in hours, convert to seconds (-1 stays -1 for
        // infinite)
        BINAuctions = c.getBoolean("bin-auctions", true);
        long binHours = c.getLong("bin-auction-duration-hours", 360);
        BINAuctionDuration = binHours == -1 ? -1 : binHours * 3600; // Convert hours to seconds
        minBINPrice = c.getDouble("min-bin", 1);
        maxPrice = c.getDouble("max-price", 1000000000000000000.0);

        // Price protection settings
        priceProtectionEnabled = c.getBoolean("price-protection.enabled", false);
        priceProtectionMultiplier = c.getDouble("price-protection.max-multiplier", 10);
        priceProtectionMinSales = c.getInt("price-protection.min-sales-required", 5);

        // BID auction settings - now in hours, convert to seconds
        BIDAuctions = c.getBoolean("bid-auctions", true);
        long bidHours = c.getLong("bid-auction-duration-hours", 48);
        BIDAuctionDuration = bidHours == -1 ? -1 : bidHours * 3600; // Convert hours to seconds
        lastBIDExtraTime = c.getInt("last-bid-extra-time", 60);
        bidIncreaseRatio = c.getDouble("bid-increase-percent", 25) / 100;
        minBIDPrice = c.getDouble("min-bid", 1);

        // Expiration settings
        expiredItemCheckInterval = c.getInt("expired-item-check-interval", 15);
        expiredItemRetentionDays = c.getInt("expired-item-retention-days", 7);

        // Format settings
        formatter = new DecimalFormat(M.getFormatted("placeholders.format-numbers"));
        formatMoneyK = c.getString("format-money.k", "k");
        formatMoneyM = c.getString("format-money.m", "M");
        formatMoneyB = c.getString("format-money.b", "B");
        formatMoneyT = c.getString("format-money.t", "T");
        formatShowDecimals = c.getBoolean("format-money.show-decimals", false);

        // Time units (Small Caps)
        timeUnitDays = c.getString("time-units.days", "ᴅɪᴀs");
        timeUnitDay = c.getString("time-units.day", "ᴅɪᴀ");
        timeUnitHours = c.getString("time-units.hours", "ʜᴏʀᴀs");
        timeUnitHour = c.getString("time-units.hour", "ʜᴏʀᴀ");
        timeUnitMinutes = c.getString("time-units.minutes", "ᴍɪɴᴜᴛᴏs");
        timeUnitMinute = c.getString("time-units.minute", "ᴍɪɴᴜᴛᴏ");
        timeUnitSeconds = c.getString("time-units.seconds", "sᴇɢᴜɴᴅᴏs");
        timeUnitSecond = c.getString("time-units.second", "sᴇɢᴜɴᴅᴏ");
        timeUnitNever = c.getString("time-units.never", "ɴᴜɴᴄᴀ");

        // Legacy sound loading (for backwards compatibility, actual sounds now in
        // SoundsConfig)
        loadLegacySounds();

        // Debug
        debugEnabled = c.getBoolean("debug.enabled", false);
        traceVisibleLore = c.getBoolean("trace.visible-lore", false);

        if (ConfigManager.backwardsCompatibility())
            backwardsCompatibility();
    }

    public static void setDebugEnabled(boolean enabled, boolean persist) {
        debugEnabled = enabled;
        if (persist) {
            try {
                FileConfiguration c = AuctionHouse.getPlugin().getConfig();
                c.set("debug.enabled", enabled);
                AuctionHouse.getPlugin().saveConfig();
            } catch (Exception e) {
                AuctionHouse.getPlugin().getLogger().warning("Could not persist debug.enabled: " + e.getMessage());
            }
        }
    }

    private static void loadLegacySounds() {
        // Legacy sound loading removed
    }

    /**
     * Check if duration is infinite (-1)
     */
    public static boolean isInfiniteDuration(long duration) {
        return duration == -1;
    }

    /**
     * Check if BIN auctions never expire
     */
    public static boolean isBINNeverExpires() {
        return BINAuctionDuration == -1;
    }

    /**
     * Check if BID auctions never expire
     */
    public static boolean isBIDNeverExpires() {
        return BIDAuctionDuration == -1;
    }

    private static void backwardsCompatibility() {
        FileConfiguration c = AuctionHouse.getPlugin().getConfig();
        c.set("plugin-version", AuctionHouse.getPlugin().getDescription().getVersion());
        FileConfiguration messageFile = M.get();

        // Migrate old duration format (seconds) to new format (hours)
        if (c.contains("bin-auction-duration")) {
            long oldDuration = c.getLong("bin-auction-duration");
            long newDuration = oldDuration / 3600; // Convert to hours
            c.set("bin-auction-duration-hours", newDuration);
            c.set("bin-auction-duration", null);
        }
        if (c.contains("bid-auction-duration")) {
            long oldDuration = c.getLong("bid-auction-duration");
            long newDuration = oldDuration / 3600; // Convert to hours
            c.set("bid-auction-duration-hours", newDuration);
            c.set("bid-auction-duration", null);
        }

        if (c.contains("currency")) {
            messageFile.set("placeholders.currency-symbol", c.getString("currency"));
            c.set("currency", null);
            c.set("currency-symbol", "has been moved to messages.yml");
        }
        if (c.contains("currency-before-number")) {
            messageFile.set("placeholders.price", "%currency-symbol%%number%");
            c.set("currency-before-number", null);
        }
        if (c.contains("format-numbers")) {
            messageFile.set("placeholders.format-numbers", c.getString("format-numbers"));
            c.set("format-numbers", null);
        }
        if (c.contains("format-time-characters")) {
            messageFile.set("placeholders.format-time-characters", c.getString("format-time-characters"));
            c.set("format-time-characters", null);
        }
        if (c.contains("filler-item")) {
            c.set("filler-item", null);
        }
        if (c.contains("auction-duration")) {
            c.set("bin-auction-duration-hours", c.getLong("auction-duration") / 3600);
            c.set("auction-duration", null);
        }
        if (ConfigManager.permissions.getCustomFile().contains("auction-duration")) {
            ConfigManager.permissions.getCustomFile().set("bin-auction-duration",
                    ConfigManager.permissions.getCustomFile().get("auction-duration"));
            ConfigManager.permissions.getCustomFile().set("auction-duration", null);
            ConfigManager.permissions.save();
            ConfigManager.permissions.reload();
        }
        if (Objects.equals(messageFile.getString("placeholders.currency-symbol"), " §ecoins")) {
            messageFile.set("placeholders.currency-symbol", " coins");
        }
        if (messageFile.contains("world.displays.sign-interaction")) {
            messageFile.set("world.displays.line-3", messageFile.get("world.displays.sign-interaction"));
            messageFile.set("world.displays.sign-interaction", null);
            String by = messageFile.getString("world.displays.by-player");
            if (by != null && !by.contains("%player%")) {
                messageFile.set("world.displays.by-player", messageFile.get("world.displays.by-player") + "%player%");
            }
        }
        try {
            ConfigManager.messages.save();
        } catch (Exception e) {
            AuctionHouse.getPlugin().getLogger().warning("Could not save messages.yml: " + e.getMessage());
        }
        ConfigManager.messages.reload();
        try {
            AuctionHouse.getPlugin().saveConfig();
        } catch (Exception e) {
            AuctionHouse.getPlugin().getLogger().warning("Could not save config.yml: " + e.getMessage());
        }
        AuctionHouse.getPlugin().reloadConfig();
    }
}
