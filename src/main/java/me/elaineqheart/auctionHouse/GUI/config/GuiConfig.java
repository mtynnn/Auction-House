package me.elaineqheart.auctionHouse.GUI.config;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.util.StringUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single GUI configuration loaded from gui/{GuiName}.yml
 */
public class GuiConfig {

    private final String guiName;
    private YamlConfiguration config;
    private File configFile;

    public GuiConfig(String guiName) {
        this.guiName = guiName;
        load();
    }

    public void load() {
        AuctionHouse plugin = AuctionHouse.getPlugin();
        configFile = new File(plugin.getDataFolder(), "gui/" + guiName + ".yml");

        // Create gui folder if it doesn't exist
        File guiFolder = new File(plugin.getDataFolder(), "gui");
        if (!guiFolder.exists()) {
            guiFolder.mkdirs();
        }

        // Save default from jar if doesn't exist
        if (!configFile.exists()) {
            try {
                plugin.saveResource("gui/" + guiName + ".yml", false);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save " + guiName + ".yml: " + e.getMessage());
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Load defaults from jar for missing values
        InputStream defaultStream = plugin.getResource("gui/" + guiName + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }
    }

    public void reload() {
        load();
    }

    /**
     * Get an item display name
     * 
     * @param key The item key (e.g., "search", "filter")
     * @return The formatted display name with colors applied
     */
    public String getItemName(String key) {
        String name = config.getString("items." + key, "");
        return StringUtils.colorize(name);
    }

    /**
     * Get lore lines for an item
     * 
     * @param key The lore key (e.g., "search-lore", "filter-lore")
     * @return List of formatted lore lines
     */
    public List<String> getLore(String key) {
        List<String> lore = config.getStringList("lore." + key);
        if (lore.isEmpty()) {
            // Try getting as a single nested section
            ConfigurationSection section = config.getConfigurationSection("lore." + key);
            if (section != null) {
                lore = new ArrayList<>();
                for (String subKey : section.getKeys(false)) {
                    lore.add(section.getString(subKey, ""));
                }
            }
        }

        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(StringUtils.colorize(line));
        }
        return coloredLore;
    }

    public List<String> getLore(String key, String... replacements) {
        List<String> list = getLore(key);
        for (int i = 0; i < list.size(); i++) {
            String line = list.get(i);
            list.set(i, replacePlaceholders(line, replacements));
        }
        return list;
    }

    public String getItemName(String key, String... replacements) {
        String name = getItemName(key);
        return replacePlaceholders(name, replacements);
    }

    private String replacePlaceholders(String message, String... replacements) {
        if (replacements.length % 2 != 0) {
            return message;
        }
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }

    /**
     * Get a specific lore line from a section
     * 
     * @param section The lore section (e.g., "filter-lore")
     * @param key     The specific key (e.g., "highest-price")
     * @return The formatted lore line
     */
    public String getLoreLine(String section, String key) {
        String line = config.getString("lore." + section + "." + key, "");
        return StringUtils.colorize(line);
    }

    /**
     * Get a single lore line with placeholder replacements
     * 
     * @param key          The lore key (e.g., "bid-history-entry")
     * @param replacements Pairs of placeholder and value (e.g., "{player}",
     *                     "Steve")
     * @return The formatted lore line with replacements applied
     */
    public String getLoreLine(String key, String... replacements) {
        String line = config.getString("lore." + key, "");
        line = StringUtils.colorize(line);
        return replacePlaceholders(line, replacements);
    }

    /**
     * Get the GUI title
     * 
     * @return The formatted title
     */
    public String getTitle() {
        String title = config.getString("titles.main", guiName);
        return StringUtils.colorize(title);
    }

    public String getTitle(String... replacements) {
        String title = getTitle();
        return replacePlaceholders(title, replacements);
    }

    /**
     * Get a custom title by key
     * 
     * @param key The title key
     * @return The formatted title
     */
    public String getTitle(String key) {
        String title = config.getString("titles." + key, guiName);
        return StringUtils.colorize(title);
    }

    /**
     * Get a status line for an item
     * 
     * @param key The status key (e.g., "waiting", "active", "expired")
     * @return The formatted status line
     */
    public String getStatus(String key) {
        String status = config.getString("lore.auction-item-status." + key, "");
        return StringUtils.colorize(status);
    }

    /**
     * Get an action line for an item
     * 
     * @param key The action key (e.g., "buy", "own", "shulker")
     * @return The formatted action line
     */
    public String getAction(String key) {
        String action = config.getString("lore.auction-item-action." + key, "");
        return StringUtils.colorize(action);
    }

    /**
     * Get a raw (unformatted) string value
     * 
     * @param path The full path in the config
     * @return The raw string value
     */
    public String getRaw(String path) {
        return config.getString(path, "");
    }

    /**
     * Get a formatted string value
     * 
     * @param path The full path in the config
     * @return The formatted string value
     */
    public String get(String path) {
        return StringUtils.colorize(config.getString(path, ""));
    }

    /**
     * Get a list of strings
     * 
     * @param path The full path in the config
     * @return The list of formatted strings
     */
    public List<String> getList(String path) {
        List<String> list = config.getStringList(path);
        List<String> coloredList = new ArrayList<>();
        for (String line : list) {
            coloredList.add(StringUtils.colorize(line));
        }
        return coloredList;
    }

    public String getGuiName() {
        return guiName;
    }

    public YamlConfiguration getConfig() {
        return config;
    }
}
