package me.elaineqheart.auctionHouse.configuration;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class Blacklist {

    private static final List<Map<String, Object>> blacklist = new CopyOnWriteArrayList<>();

    public static void setBlacklist(List<Map<String, Object>> list) {
        blacklist.clear();
        if (list != null) {
            for (Map<String, Object> entry : list) {
                Map<String, Object> newEntry = new HashMap<>(entry);
                if (newEntry.containsKey("isItem") && (boolean) newEntry.get("isItem")) {
                    Object key = newEntry.get("key");
                    if (key instanceof String) {
                        newEntry.put("key", me.elaineqheart.auctionHouse.util.ItemStackConverter.decode((String) key));
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
                return true;
        }
        return false;
    }

    private static boolean isExact(ItemStack item, ItemStack key) {
        if (key == null)
            return false;
        key.setAmount(item.getAmount());
        return item.equals(key);
    }

    // ... helper methods ...
    private static boolean isMaterial(ItemStack item, String key) {
        return item.getType() == Material.getMaterial(key);
    }

    private static boolean loreContains(ItemStack item, String key) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null)
            return false;
        return meta.getLore().stream().anyMatch(line -> line.contains(key));
    }

    private static boolean nameContains(ItemStack item, String key) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;
        String displayName = meta.hasDisplayName() ? meta.getDisplayName() : "";
        String itemName = meta.hasItemName() ? meta.getItemName() : "";
        return displayName.contains(key) || itemName.contains(key);
    }

    private static boolean itemModelContains(ItemStack item, String key) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasItemModel() || meta.getItemModel() == null)
            return false;
        return meta.getItemModel().getKey().toString().contains(key);
    }

    private static boolean customModelContains(ItemStack item, String key) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;
        // Assuming this is correct for 1.21.4.
        return meta.hasCustomModelData() && String.valueOf(meta.getCustomModelData()).equals(key);
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
            if (newEntry.get("key") instanceof ItemStack) {
                newEntry.put("key", me.elaineqheart.auctionHouse.util.ItemStackConverter
                        .encode((ItemStack) newEntry.get("key")));
                newEntry.put("isItem", true);
            }
            saveList.add(newEntry);
        }

        new me.elaineqheart.auctionHouse.database.dao.ServerDataDAO().saveBlacklist(saveList);
    }
}
