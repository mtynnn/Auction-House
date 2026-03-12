package me.elaineqheart.auctionHouse.manager;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.util.StringUtils;
import me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager;
import me.elaineqheart.auctionHouse.model.AuctionItem;
import me.elaineqheart.auctionHouse.configuration.SettingManager;

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
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ItemManager {

    public static ItemStack emptyPaper;
    // WeakHashMap allows GC to clean up unused cached items
    private static final Map<CacheKey, ItemStack> itemCache = new WeakHashMap<>();
    private static final DateTimeFormatter TRACE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final String TRACE_LORE_PREFIX = ChatColor.DARK_GRAY + "AH-ID: " + ChatColor.GRAY;
    private static final String TRACE_DATE_LORE_PREFIX = ChatColor.DARK_GRAY + "Comprado el: " + ChatColor.GRAY;

    static {
        emptyPaper = createEmptyPaper();
    }

    /**
     * Cache key for display items. Combines auction UUID, player UUID, and admin flag.
     */
    private static class CacheKey {
        private final UUID auctionId;
        private final UUID playerId;
        private final boolean isAdmin;
        private final long cacheTime;

        CacheKey(UUID auctionId, UUID playerId, boolean isAdmin) {
            this.auctionId = auctionId;
            this.playerId = playerId;
            this.isAdmin = isAdmin;
            this.cacheTime = System.currentTimeMillis();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey)) return false;
            CacheKey cacheKey = (CacheKey) o;
            return isAdmin == cacheKey.isAdmin &&
                   Objects.equals(auctionId, cacheKey.auctionId) &&
                   Objects.equals(playerId, cacheKey.playerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(auctionId, playerId, isAdmin);
        }

        /**
         * Returns true if this cache entry is older than 5 seconds (item might have updated)
         */
        boolean isStale() {
            return System.currentTimeMillis() - cacheTime > 5000;
        }
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

    private static NamespacedKey traceOriginIdKey() {
        return new NamespacedKey(AuctionHouse.getPlugin(), "ah_origin_id");
    }

    private static NamespacedKey traceAuctionIdKey() {
        return new NamespacedKey(AuctionHouse.getPlugin(), "ah_origin_auction_id");
    }

    private static NamespacedKey traceBoughtAtKey() {
        return new NamespacedKey(AuctionHouse.getPlugin(), "ah_origin_bought_at");
    }

    private static NamespacedKey traceBuyerKey() {
        return new NamespacedKey(AuctionHouse.getPlugin(), "ah_origin_buyer");
    }

    public static class ItemTraceInfo {
        private final String originId;
        private final String auctionId;
        private final Long boughtAtEpochSeconds;
        private final String buyer;

        public ItemTraceInfo(String originId, String auctionId, Long boughtAtEpochSeconds, String buyer) {
            this.originId = originId;
            this.auctionId = auctionId;
            this.boughtAtEpochSeconds = boughtAtEpochSeconds;
            this.buyer = buyer;
        }

        public String getOriginId() {
            return originId;
        }

        public String getAuctionId() {
            return auctionId;
        }

        public Long getBoughtAtEpochSeconds() {
            return boughtAtEpochSeconds;
        }

        public String getBuyer() {
            return buyer;
        }

        public String getFormattedBoughtAt() {
            if (boughtAtEpochSeconds == null || boughtAtEpochSeconds <= 0) {
                return "desconocido";
            }
            return TRACE_DATE_FORMAT.format(Instant.ofEpochSecond(boughtAtEpochSeconds));
        }
    }

    public static String getOriginId(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(traceOriginIdKey(), PersistentDataType.STRING);
    }

    public static ItemTraceInfo getTraceInfo(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String originId = meta.getPersistentDataContainer().get(traceOriginIdKey(), PersistentDataType.STRING);
        if (originId == null || originId.isEmpty()) {
            return null;
        }
        String auctionId = meta.getPersistentDataContainer().get(traceAuctionIdKey(), PersistentDataType.STRING);
        Long boughtAt = meta.getPersistentDataContainer().get(traceBoughtAtKey(), PersistentDataType.LONG);
        String buyer = meta.getPersistentDataContainer().get(traceBuyerKey(), PersistentDataType.STRING);
        return new ItemTraceInfo(originId, auctionId, boughtAt, buyer);
    }

    public static ItemStack stampAuctionPurchase(ItemStack source, AuctionItem note, Player buyer) {
        if (source == null) {
            return null;
        }
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        long now = System.currentTimeMillis() / 1000L;

        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        }
        // Remove any previous purchase-date line before adding a fresh one
        lore.removeIf(line -> line != null && line.startsWith(TRACE_DATE_LORE_PREFIX));
        lore.add(TRACE_DATE_LORE_PREFIX + TRACE_DATE_FORMAT.format(Instant.ofEpochSecond(now)));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean stripAuctionTraceLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        boolean changed = clearTraceLore(meta);
        if (changed) {
            item.setItemMeta(meta);
        }
        return changed;
    }

    private static boolean clearTraceLore(ItemMeta meta) {
        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return false;
        }
        int before = lore.size();
        lore.removeIf(line -> line != null
                && (line.startsWith(TRACE_LORE_PREFIX) || line.startsWith(TRACE_DATE_LORE_PREFIX)));
        if (lore.size() == before) {
            return false;
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        return true;
    }

    private static ItemStack createCorruptedItemPlaceholder(AuctionItem note) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(ChatColor.RED + "Corrupted Item - Missing Plugin");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Item ID: " + ChatColor.WHITE + note.getNoteID().toString().substring(0, 8));
            lore.add(ChatColor.GRAY + "Seller: " + ChatColor.WHITE + note.getPlayerName());
            lore.add(ChatColor.GRAY + "Price: " + ChatColor.GOLD + note.getPrice());
            lore.add("");
            lore.add(ChatColor.YELLOW + "This item has custom enchantments");
            lore.add(ChatColor.YELLOW + "from a plugin that is not installed.");
            lore.add("");
            lore.add(ChatColor.RED + "Contact an admin to remove this auction.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createItemFromNote(AuctionItem note, Player p, boolean ownAuction, boolean isAdmin) {
        UUID playerId = p != null ? p.getUniqueId() : new UUID(0L, 0L);

        // Check cache first
        CacheKey key = new CacheKey(note.getNoteID(), playerId, isAdmin);
        ItemStack cached = itemCache.get(key);
        if (cached != null && !key.isStale()) {
            return cached.clone(); // Return clone to prevent modification
        }

        ItemStack item = note.getItem();
        
        // Handle corrupted items (missing plugin dependencies)
        if (item == null) {
            item = createCorruptedItemPlaceholder(note);
            return item;
        }
        
        item = item.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null)
                lore = new ArrayList<>();

            // Use the new consolidated template
            List<String> template = GuiConfigManager.auctionHouse().getLore("auction-item-lore",
                    "{price}", StringUtils.formatPrice(ownAuction ? note.getPrice() : note.getCurrentPrice()),
                    "{seller}", StringUtils.escapeMiniMessage(note.getPlayerName()),
                    "{remaining_time}", StringUtils.getTime(note.getTimeLeft(), true));

            boolean isSeller = p != null && Objects.equals(note.getPlayerUUID(), p.getUniqueId());

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
        
        // Store in cache for future requests
        itemCache.put(key, item.clone());
        return item;
    }

    /**
     * Invalidates cached display items for a specific auction.
     * Call this when an auction is updated (e.g., new bid, price changed).
     */
    public static void invalidateCache(UUID auctionId) {
        itemCache.entrySet().removeIf(entry -> entry.getKey().auctionId.equals(auctionId));
    }

    /**
     * Clears the entire item cache.
     * Useful for plugin reloads or memory optimization.
     */
    public static void clearCache() {
        itemCache.clear();
    }

    public static ItemStack createCollectingItemFromNote(AuctionItem note, Player p) {
        // Updated signature to include Player p if needed, matching usage in
        // CollectSoldItemGUI
        ItemStack item = note.getItem();
        
        // Handle corrupted items
        if (item == null) {
            return createCorruptedItemPlaceholder(note);
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null)
                lore = new ArrayList<>();
            lore.addAll(GuiConfigManager.auctionItems().getLore("items.auction.lore.default",
                    "{price}", String.valueOf(note.getSoldPrice()),
                    "{player}", StringUtils.escapeMiniMessage(note.getPlayerName())));
            lore.addAll(GuiConfigManager.auctionItems().getLore("items.auction.lore.own-auction"));
            lore.addAll(GuiConfigManager.auctionItems().getLore("items.auction.lore.sold",
                    "{buyer}", StringUtils.escapeMiniMessage(note.getBuyerName() != null ? note.getBuyerName() : "Unknown")));
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
        
        // Handle corrupted items
        if (item == null) {
            return createCorruptedItemPlaceholder(note);
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null)
                lore = new ArrayList<>();
            lore.addAll(GuiConfigManager.auctionItems().getLore("items.admin-expire-item.lore",
                    "{price}", String.valueOf(note.getPrice()),
                    "{player}", StringUtils.escapeMiniMessage(note.getPlayerName()),
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
                    "{player}", StringUtils.escapeMiniMessage(note.getPlayerName()),
                    "{reason}", reason));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createBuyingItemDisplay(ItemStack item) {
        return item;
    }

    /**
     * Create item display specifically for MyAuctionsGUI with status-specific lore
     */
    public static ItemStack createMyAuctionItem(AuctionItem note, Player p) {
        ItemStack item = note.getItem();
        
        // Handle corrupted items
        if (item == null) {
            return createCorruptedItemPlaceholder(note);
        }
        
        item = item.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null)
                lore = new ArrayList<>();

            // Determine which lore template to use based on item state
            List<String> template;
            
            if (note.isSold() || (note.isBIDAuction() && note.hasBidHistory() && note.isExpired())) {
                // Item sold - use item-sold-lore
                template = GuiConfigManager.myAuctions().getLore("item-sold-lore",
                        "{price}", StringUtils.formatPrice(note.getSoldPrice()),
                        "{buyer}", StringUtils.escapeMiniMessage(note.getBuyerName() != null ? note.getBuyerName() : "Unknown"));
            } else if (note.isExpired()) {
                // Item expired - check if admin expired
                if (note.getAdminMessage() != null && !note.getAdminMessage().isEmpty()) {
                    template = GuiConfigManager.myAuctions().getLore("item-admin-expired-lore",
                            "{price}", StringUtils.formatPrice(note.getPrice()),
                            "{reason}", note.getAdminMessage());
                } else {
                    template = GuiConfigManager.myAuctions().getLore("item-expired-lore",
                            "{price}", StringUtils.formatPrice(note.getPrice()));
                }
            } else {
                // Item active - use item-active-lore
                template = GuiConfigManager.myAuctions().getLore("item-active-lore",
                        "{price}", StringUtils.formatPrice(note.getPrice()),
                        "{time}", StringUtils.getTime(note.getTimeLeft(), true));
            }

            lore.addAll(template);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
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
