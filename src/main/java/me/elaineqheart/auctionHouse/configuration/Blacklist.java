package me.elaineqheart.auctionHouse.configuration;

import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import me.elaineqheart.auctionHouse.util.Debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class Blacklist {

    private static final List<Map<String, Object>> blacklist = new CopyOnWriteArrayList<>();
    private static final String KEY_ENCODED = "key_encoded";

    public static void setBlacklist(List<Map<String, Object>> list) {
        blacklist.clear();
        if (list != null) {
            for (Map<String, Object> entry : list) {
                Map<String, Object> newEntry = new HashMap<>(entry);
                if (newEntry.containsKey("isItem") && (boolean) newEntry.get("isItem")) {
                    Object key = newEntry.get("key");
                    if (key instanceof String) {
                        String encoded = (String) key;
                        newEntry.put(KEY_ENCODED, encoded);
                        newEntry.put("key", me.elaineqheart.auctionHouse.util.ItemStackConverter.decode(encoded));
                    }
                }
                blacklist.add(newEntry);
            }
        }
    }

    public static List<Map<String, Object>> getBlacklist() {
        return blacklist;
    }

    public static boolean isBlacklisted(ItemStack item) {
        return isBlacklisted(item, blacklist);
    }

    public static boolean isBlacklisted(ItemStack item, List<Map<String, Object>> blacklist) {
        // Safe check for null list
        if (blacklist == null)
            return false;
        if (item == null || item.getType().isAir())
            return false;

        boolean blacklisted = false;
        for (Map<String, Object> entry : blacklist) { // Changed generics to Map<String, Object>
            Object keyObj = entry.get("key");
            Object typeObj = entry.get("type");
            if (keyObj == null || typeObj == null)
                continue;

            String type = typeObj.toString();
            switch (type) {
                case "exact" -> blacklisted = isExact(item, (ItemStack) keyObj);
                case "material" -> blacklisted = isMaterial(item, keyObj.toString());
                case "lore" -> blacklisted = loreContains(item, keyObj.toString());
                case "name" -> blacklisted = nameContains(item, keyObj.toString());
                case "item_model" -> blacklisted = itemModelContains(item, keyObj.toString());
                case "custom_model_data" -> blacklisted = customModelContains(item, keyObj.toString());
                case "all" -> blacklisted = true;
                default -> {
                }
            }
            if (blacklisted)
            {
                Debug.log("Blacklist match: type=" + type + " key=" + keyObj + " item=" + item.getType());
                return true;
            }
        }
        return false;
    }

    private static boolean isExact(ItemStack item, ItemStack key) {
        if (key == null)
            return false;
        ItemStack keyClone = key.clone();
        keyClone.setAmount(item.getAmount());
        return item.equals(keyClone);
    }

    // ... helper methods ...
    private static boolean isMaterial(ItemStack item, String key) {
        Material material = parseMaterial(key);
        return material != null && item.getType() == material;
    }

    private static boolean loreContains(ItemStack item, String key) {
        if (key == null || key.isBlank())
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null)
            return false;
        String needle = key.toLowerCase(Locale.ROOT);
        return meta.getLore().stream()
                .map(line -> ChatColor.stripColor(line) == null ? "" : ChatColor.stripColor(line))
                .anyMatch(line -> line.toLowerCase(Locale.ROOT).contains(needle));
    }

    private static boolean nameContains(ItemStack item, String key) {
        if (key == null || key.isBlank())
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;
        String needle = key.toLowerCase(Locale.ROOT);
        String displayName = meta.hasDisplayName() ? Objects.requireNonNullElse(ChatColor.stripColor(meta.getDisplayName()), "") : "";
        String itemName = meta.hasItemName() ? Objects.requireNonNullElse(ChatColor.stripColor(meta.getItemName()), "") : "";
        return displayName.toLowerCase(Locale.ROOT).contains(needle) || itemName.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean itemModelContains(ItemStack item, String key) {
        if (key == null || key.isBlank())
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasItemModel() || meta.getItemModel() == null)
            return false;
        String haystack = meta.getItemModel().getKey().toString().toLowerCase(Locale.ROOT);
        String needle = key.toLowerCase(Locale.ROOT);
        return haystack.contains(needle);
    }

    private static boolean customModelContains(ItemStack item, String key) {
        if (key == null || key.isBlank())
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;
        // Assuming this is correct for 1.21.4.
        return meta.hasCustomModelData() && String.valueOf(meta.getCustomModelData()).equals(key);
    }

    private static Material parseMaterial(String input) {
        if (input == null)
            return null;
        String s = input.trim();
        if (s.isEmpty())
            return null;

        // Support "minecraft:player_head" (and similar)
        if (s.contains(":")) {
            String[] parts = s.split(":", 2);
            s = parts[1];
        }

        s = s.trim()
                .replace(" ", "_")
                .toUpperCase(Locale.ROOT);

        Material matched = Material.matchMaterial(s);
        if (matched != null)
            return matched;
        return Material.getMaterial(s);
    }

    public void addExact(ItemStack item) {
        add("exact", item);
    }

    public void addMaterial(String material) {
        add("material", material);
    }

    public void addLoreContains(String lore) {
        add("lore", lore);
    }

    public void addNameContains(String itemName) {
        add("name", itemName);
    }

    public void addItemModel(String model) {
        add("item_model", model);
    }

    public void addCustomModelData(String model) {
        add("custom_model_data", model);
    }

    public void addAll() {
        add("all", "0");
    }

    private void add(String type, Object object) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("type", type);
        entry.put("key", object);
        if (object instanceof ItemStack itemStack) {
            // Cache encoded form so saving doesn't serialize ItemStacks on the main thread repeatedly.
            entry.put(KEY_ENCODED, me.elaineqheart.auctionHouse.util.ItemStackConverter.encode(itemStack));
            entry.put("isItem", true);
        }
        blacklist.add(entry);
        save();
    }

    public boolean undo() {
        if (!blacklist.isEmpty()) {
            blacklist.removeLast();
            save();
            return true;
        }
        return false;
    }

    private void save() {
        List<Map<String, Object>> saveList = new ArrayList<>();
        for (Map<String, Object> entry : blacklist) {
            Map<String, Object> newEntry = new HashMap<>(entry);
            if (newEntry.get("key") instanceof ItemStack itemStack) {
                Object encodedObj = entry.get(KEY_ENCODED);
                String encoded;
                if (encodedObj instanceof String s && !s.isBlank()) {
                    encoded = s;
                } else {
                    // Fallback: encode once, then cache it for future saves.
                    encoded = me.elaineqheart.auctionHouse.util.ItemStackConverter.encode(itemStack);
                    entry.put(KEY_ENCODED, encoded);
                }
                newEntry.put("key", encoded);
                newEntry.put("isItem", true);
            }
            saveList.add(newEntry);
        }

        new me.elaineqheart.auctionHouse.database.dao.ServerDataDAO().saveBlacklist(saveList);
    }
}
