package me.elaineqheart.auctionHouse.configuration;

import me.elaineqheart.auctionHouse.AuctionHouse;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages slot positions and materials for all GUIs from slotconfig.yml
 */
public class SlotConfigManager {

    private static YamlConfiguration slotConfig;
    private static File configFile;

    // Caches
    private static final Map<String, Map<String, Integer>> slotsCache = new HashMap<>();
    private static final Map<String, Map<String, Material>> materialsCache = new HashMap<>();
    private static final Map<String, List<Integer>> itemSlotsCache = new HashMap<>();
    private static final Map<String, Integer> sizesCache = new HashMap<>();

    public static void load() {
        AuctionHouse plugin = AuctionHouse.getPlugin();
        configFile = new File(plugin.getDataFolder(), "slotconfig.yml");

        if (!configFile.exists()) {
            try {
                plugin.saveResource("slotconfig.yml", false);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save slotconfig.yml: " + e.getMessage());
            }
        }

        slotConfig = YamlConfiguration.loadConfiguration(configFile);

        // Load defaults from jar for missing values
        InputStream defaultStream = plugin.getResource("slotconfig.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            slotConfig.setDefaults(defaultConfig);
        }

        // Build caches
        buildCaches();
    }

    public static void reload() {
        slotsCache.clear();
        materialsCache.clear();
        itemSlotsCache.clear();
        sizesCache.clear();
        load();
    }

    private static void buildCaches() {
        for (String guiName : slotConfig.getKeys(false)) {
            ConfigurationSection guiSection = slotConfig.getConfigurationSection(guiName);
            if (guiSection == null)
                continue;

            // Cache size
            sizesCache.put(guiName, guiSection.getInt("size", 54));

            // Cache item-slots (auction item display positions)
            // Supports both individual values (10) and ranges (10-35)
            List<Integer> itemSlots = parseItemSlots(guiSection.getList("item-slots"));
            if (!itemSlots.isEmpty()) {
                itemSlotsCache.put(guiName, itemSlots);
            }

            // Cache slots
            ConfigurationSection slotsSection = guiSection.getConfigurationSection("slots");
            if (slotsSection != null) {
                Map<String, Integer> slots = new HashMap<>();
                for (String key : slotsSection.getKeys(false)) {
                    slots.put(key, slotsSection.getInt(key, 0));
                }
                slotsCache.put(guiName, slots);
            }

            // Cache materials
            ConfigurationSection materialsSection = guiSection.getConfigurationSection("materials");
            if (materialsSection != null) {
                Map<String, Material> materials = new HashMap<>();
                for (String key : materialsSection.getKeys(false)) {
                    String materialName = materialsSection.getString(key, "STONE");
                    try {
                        materials.put(key, Material.valueOf(materialName));
                    } catch (IllegalArgumentException e) {
                        AuctionHouse.getPlugin().getLogger().warning(
                                "Invalid material '" + materialName + "' in slotconfig.yml for " + guiName + "." + key);
                        materials.put(key, Material.STONE);
                    }
                }
                materialsCache.put(guiName, materials);
            }
        }
    }

    /**
     * Parse item-slots supporting both individual values and ranges.
     * Examples:
     * - 10 -> [10]
     * - "10-15" -> [10, 11, 12, 13, 14, 15]
     * - [10, "12-14", 20] -> [10, 12, 13, 14, 20]
     */
    private static List<Integer> parseItemSlots(List<?> rawList) {
        List<Integer> result = new ArrayList<>();
        if (rawList == null)
            return result;

        for (Object item : rawList) {
            if (item instanceof Integer) {
                result.add((Integer) item);
            } else if (item instanceof Number) {
                result.add(((Number) item).intValue());
            } else if (item instanceof String str) {
                str = str.trim();
                if (str.contains("-")) {
                    String[] parts = str.split("-");
                    if (parts.length == 2) {
                        try {
                            int start = Integer.parseInt(parts[0].trim());
                            int end = Integer.parseInt(parts[1].trim());
                            for (int i = start; i <= end; i++) {
                                result.add(i);
                            }
                        } catch (NumberFormatException e) {
                            AuctionHouse.getPlugin().getLogger().warning(
                                    "Invalid slot range: " + str);
                        }
                    }
                } else {
                    try {
                        result.add(Integer.parseInt(str));
                    } catch (NumberFormatException e) {
                        AuctionHouse.getPlugin().getLogger().warning(
                                "Invalid slot value: " + str);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Get the GUI size (inventory slots)
     */
    public static int getSize(String guiName) {
        return sizesCache.getOrDefault(guiName, 54);
    }

    /**
     * Get the item display slots (where auction items are shown)
     */
    public static List<Integer> getItemSlots(String guiName) {
        return itemSlotsCache.getOrDefault(guiName, new ArrayList<>());
    }

    /**
     * Get the slot position for a GUI element
     */
    public static int getSlot(String guiName, String element) {
        Map<String, Integer> slots = slotsCache.get(guiName);
        if (slots != null && slots.containsKey(element)) {
            return slots.get(element);
        }
        return slotConfig.getInt(guiName + ".slots." + element, 0);
    }

    /**
     * Get the material for a GUI element
     */
    public static Material getMaterial(String guiName, String element) {
        Map<String, Material> materials = materialsCache.get(guiName);
        if (materials != null && materials.containsKey(element)) {
            return materials.get(element);
        }
        String materialName = slotConfig.getString(guiName + ".materials." + element, "STONE");
        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            return Material.STONE;
        }
    }

    /**
     * Create an ItemStack with the material from config
     */
    public static ItemStack createItem(String guiName, String element) {
        return new ItemStack(getMaterial(guiName, element));
    }

    /**
     * Get the filler item material for a GUI
     */
    public static Material getFillerMaterial(String guiName) {
        return getMaterial(guiName, "filler");
    }

    /**
     * Create a filler item for a GUI
     */
    public static ItemStack createFillerItem(String guiName) {
        return new ItemStack(getFillerMaterial(guiName));
    }

    /**
     * Get all slots for a specific GUI
     */
    public static Map<String, Integer> getAllSlots(String guiName) {
        return slotsCache.getOrDefault(guiName, new HashMap<>());
    }

    /**
     * Get all materials for a specific GUI
     */
    public static Map<String, Material> getAllMaterials(String guiName) {
        return materialsCache.getOrDefault(guiName, new HashMap<>());
    }

    public static YamlConfiguration getConfig() {
        return slotConfig;
    }
}
