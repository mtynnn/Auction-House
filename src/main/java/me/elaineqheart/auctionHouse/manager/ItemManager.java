package me.elaineqheart.auctionHouse.manager;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.util.StringUtils;
import me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager;
import me.elaineqheart.auctionHouse.model.AuctionItem;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ItemManager {

    public static ItemStack emptyPaper;

    static {
        emptyPaper = createEmptyPaper();
    }

    private static ItemStack createEmptyPaper() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(ChatColor.GRAY + "");
            meta.getPersistentDataContainer().set(new NamespacedKey(AuctionHouse.getPlugin(), "AuctionHouseSearch"),
                    PersistentDataType.BOOLEAN, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createDirt() {
        ItemStack item = new ItemStack(Material.DIRT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionItems().getItemName("items.deleted.name"));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createItemFromNote(AuctionItem note, Player p, boolean ownAuction, boolean isAdmin) {
        ItemStack item = note.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null)
                lore = new ArrayList<>();

            // Use the new consolidated template
            List<String> template = GuiConfigManager.auctionHouse().getLore("auction-item-lore",
                    "{price}", StringUtils.formatPrice(ownAuction ? note.getPrice() : note.getCurrentPrice()),
                    "{seller}", note.getPlayerName(),
                    "{remaining_time}", StringUtils.getTime(note.getTimeLeft(), true));

            boolean isSeller = Objects.equals(note.getPlayerUUID(), p.getUniqueId());

            for (String line : template) {
                if (line.contains("{if_shulker}")) {
                    if (isShulkerBox(item)) {
                        lore.add(line.replace("{if_shulker}", ""));
                    }
                    continue;
                }
                if (line.contains("{if_not_seller}")) {
                    if (!isSeller) {
                        lore.add(line.replace("{if_not_seller}", ""));
                    }
                    continue;
                }
                if (line.contains("{if_seller}")) {
                    if (isSeller) {
                        lore.add(line.replace("{if_seller}", ""));
                    }
                    continue;
                }
                if (line.contains("{if_admin}")) {
                    if (isAdmin) {
                        lore.add(line.replace("{if_admin}", ""));
                    }
                    continue;
                }
                lore.add(line);
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createCollectingItemFromNote(AuctionItem note, Player p) {
        // Updated signature to include Player p if needed, matching usage in
        // CollectSoldItemGUI
        ItemStack item = note.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null)
                lore = new ArrayList<>();
            lore.addAll(GuiConfigManager.auctionItems().getLore("items.auction.lore.default",
                    "{price}", String.valueOf(note.getSoldPrice()),
                    "{player}", note.getPlayerName()));
            lore.addAll(GuiConfigManager.auctionItems().getLore("items.auction.lore.own-auction"));
            lore.addAll(GuiConfigManager.auctionItems().getLore("items.auction.lore.sold",
                    "{buyer}", note.getBuyerName() != null ? note.getBuyerName() : "Unknown"));
            item.setAmount(item.getAmount() - note.getPartiallySoldAmountLeft());

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // Kept for compatibility if called without player
    public static ItemStack createCollectingItemFromNote(AuctionItem note) {
        return createCollectingItemFromNote(note, null);
    }

    public static ItemStack createAdminExpireItem(AuctionItem note, String reason) {
        ItemStack item = note.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null)
                lore = new ArrayList<>();
            lore.addAll(GuiConfigManager.auctionItems().getLore("items.admin-expire-item.lore",
                    "{price}", String.valueOf(note.getPrice()),
                    "{player}", note.getPlayerName(),
                    "{reason}", reason));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createAdminDeleteItem(AuctionItem note, String reason) {
        ItemStack item = createDirt();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null)
                lore = new ArrayList<>();
            lore.addAll(GuiConfigManager.auctionItems().getLore("items.admin-delete-item.lore",
                    "{price}", String.valueOf(note.getPrice()),
                    "{player}", note.getPlayerName(),
                    "{reason}", reason));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createBuyingItemDisplay(ItemStack item) {
        return item;
    }

    public static boolean isShulkerBox(ItemStack item) {
        if (item == null)
            return false;
        Material type = item.getType();
        return type == Material.SHULKER_BOX ||
                type == Material.WHITE_SHULKER_BOX ||
                type == Material.ORANGE_SHULKER_BOX ||
                type == Material.MAGENTA_SHULKER_BOX ||
                type == Material.LIGHT_BLUE_SHULKER_BOX ||
                type == Material.YELLOW_SHULKER_BOX ||
                type == Material.LIME_SHULKER_BOX ||
                type == Material.PINK_SHULKER_BOX ||
                type == Material.GRAY_SHULKER_BOX ||
                type == Material.LIGHT_GRAY_SHULKER_BOX ||
                type == Material.CYAN_SHULKER_BOX ||
                type == Material.PURPLE_SHULKER_BOX ||
                type == Material.BLUE_SHULKER_BOX ||
                type == Material.BROWN_SHULKER_BOX ||
                type == Material.GREEN_SHULKER_BOX ||
                type == Material.RED_SHULKER_BOX ||
                type == Material.BLACK_SHULKER_BOX;
    }

}
