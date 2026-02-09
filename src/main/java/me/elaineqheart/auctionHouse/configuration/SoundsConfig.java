package me.elaineqheart.auctionHouse.configuration;

import me.elaineqheart.auctionHouse.AuctionHouse;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages configurable sounds from sounds.yml
 */
public class SoundsConfig {

    private static YamlConfiguration soundsConfig;
    private static File configFile;

    private static final Map<String, SoundEntry> soundsCache = new HashMap<>();

    public static void load() {
        AuctionHouse plugin = AuctionHouse.getPlugin();
        configFile = new File(plugin.getDataFolder(), "sounds.yml");

        if (!configFile.exists()) {
            try {
                plugin.saveResource("sounds.yml", false);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save sounds.yml: " + e.getMessage());
            }
        }

        soundsConfig = YamlConfiguration.loadConfiguration(configFile);

        // Load defaults from jar for missing values
        InputStream defaultStream = plugin.getResource("sounds.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            soundsConfig.setDefaults(defaultConfig);
        }

        // Build cache
        buildCache();
    }

    public static void reload() {
        soundsCache.clear();
        load();
    }

    private static void buildCache() {
        for (String soundKey : soundsConfig.getKeys(false)) {
            ConfigurationSection section = soundsConfig.getConfigurationSection(soundKey);
            if (section == null)
                continue;

            boolean enabled = section.getBoolean("enabled", true);
            String soundName = section.getString("sound", "UI_BUTTON_CLICK");
            float volume = (float) section.getDouble("volume", 1.0);
            float pitch = (float) section.getDouble("pitch", 1.0);

            Sound sound;
            try {
                sound = Sound.valueOf(soundName);
            } catch (IllegalArgumentException e) {
                AuctionHouse.getPlugin().getLogger().warning(
                        "Invalid sound '" + soundName + "' in sounds.yml for key '" + soundKey + "'");
                sound = Sound.UI_BUTTON_CLICK;
            }

            soundsCache.put(soundKey, new SoundEntry(enabled, sound, volume, pitch));
        }
    }

    /**
     * Play a sound for a player
     * 
     * @param player   The player to play the sound for
     * @param soundKey The key from sounds.yml (e.g., "success", "error",
     *                 "ui-click")
     */
    public static void playSound(Player player, String soundKey) {
        SoundEntry entry = soundsCache.get(soundKey);
        if (entry == null || !entry.enabled)
            return;

        player.playSound(player.getLocation(), entry.sound, entry.volume, entry.pitch);
    }

    /**
     * Check if a sound is enabled
     * 
     * @param soundKey The sound key
     * @return true if enabled, false otherwise
     */
    public static boolean isEnabled(String soundKey) {
        SoundEntry entry = soundsCache.get(soundKey);
        return entry != null && entry.enabled;
    }

    /**
     * Get the Sound enum value for a sound key
     * 
     * @param soundKey The sound key
     * @return The Sound, or null if not found
     */
    public static Sound getSound(String soundKey) {
        SoundEntry entry = soundsCache.get(soundKey);
        return entry != null ? entry.sound : null;
    }

    // Legacy compatibility methods - mapped to new sound keys
    public static void click(Player p) {
        playSound(p, "ui-click");
    }

    public static void openEnderchest(Player p) {
        playSound(p, "open-gui");
    }

    public static void closeEnderchest(Player p) {
        playSound(p, "close-gui");
    }

    public static void breakWood(Player p) {
        playSound(p, "break-wood");
    }

    public static void experience(Player p) {
        playSound(p, "experience");
    }

    public static void villagerDeny(Player p) {
        playSound(p, "error");
    }

    public static void openShulker(Player p) {
        playSound(p, "open-shulker");
    }

    public static void closeShulker(Player p) {
        playSound(p, "close-shulker");
    }

    public static void npcClick(Player p) {
        playSound(p, "npc-click");
    }

    public static void success(Player p) {
        playSound(p, "success");
    }

    public static YamlConfiguration getConfig() {
        return soundsConfig;
    }

    /**
     * Internal class to hold sound configuration
     */
    private static class SoundEntry {
        final boolean enabled;
        final Sound sound;
        final float volume;
        final float pitch;

        SoundEntry(boolean enabled, Sound sound, float volume, float pitch) {
            this.enabled = enabled;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }
}
