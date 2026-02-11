package me.elaineqheart.auctionHouse.model;

import de.unpixelt.locale.Translate;
import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.util.StringUtils;
import me.elaineqheart.auctionHouse.util.ItemStackConverter;
import me.elaineqheart.auctionHouse.configuration.SettingManager;
import me.elaineqheart.auctionHouse.configuration.ConfigManager;
import me.elaineqheart.auctionHouse.manager.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class AuctionItem {

    private final String playerName;
    private String buyerName;
    private final UUID playerUUID;
    private double price;
    private final Date dateCreated;
    private String itemData;
    private boolean isSold;
    private int partiallySoldAmountLeft;
    private final UUID noteID;
    private String adminMessage;
    private long auctionTime;
    private String itemName;
    private final boolean isBIDAuction;
    private List<Bid> bidHistory = new ArrayList<>();
    private Set<UUID> claimedPlayers = new HashSet<>();

    public AuctionItem(Player player, ItemStack item, double price, boolean isBIDAuction) {
        this.noteID = UUID.randomUUID();
        this.playerName = player.getDisplayName();
        this.buyerName = null;
        this.playerUUID = player.getUniqueId();
        this.dateCreated = new Date();
        this.itemData = ItemStackConverter.encode(item);
        this.price = price;
        this.isSold = false;
        this.auctionTime = ConfigManager.permissions.getAuctionDuration(player, isBIDAuction);
        this.itemName = StringUtils.getItemName(item);
        this.isBIDAuction = isBIDAuction;
    }

    // Constructor for Database Loading
    public AuctionItem(UUID noteID, UUID playerUUID, String playerName, String itemData, double price, long dateCreated,
            long auctionTime, boolean isBIDAuction, boolean isSold, int partiallySoldAmountLeft, String adminMessage,
            String buyerName) {
        this.noteID = noteID;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.itemData = itemData;
        this.price = price;
        this.claimedPlayers = new HashSet<>();

        // Handle seconds vs milliseconds mismatch from legacy databases
        if (dateCreated < 2000000000L) {
            this.dateCreated = new Date(dateCreated * 1000);
        } else {
            this.dateCreated = new Date(dateCreated);
        }

        this.auctionTime = auctionTime;
        this.isBIDAuction = isBIDAuction;
        this.isSold = isSold;
        this.partiallySoldAmountLeft = partiallySoldAmountLeft;
        this.adminMessage = adminMessage;
        this.buyerName = buyerName;
        
        // Safely get item name - handle items with missing plugin dependencies
        try {
            ItemStack loadedItem = getItem();
            this.itemName = loadedItem != null ? StringUtils.getItemName(loadedItem) : "Unknown Item";
        } catch (Exception e) {
            this.itemName = "Corrupted Item";
        }
    }

    public ItemStack getItem() {
        try {
            return ItemStackConverter.decode(itemData);
        } catch (Exception e) {
            System.err.println("[AuctionHouse] Failed to decode item for auction " + noteID + ": " + e.getMessage());
            return null;
        }
    }

    public long getTimeLeft() {
        if (auctionTime == 0) {
            Player p = Bukkit.getPlayer(playerUUID);
            if (p != null) {
                auctionTime = ConfigManager.permissions.getAuctionDuration(p, isBIDAuction);
            } else {
                // Fallback for offline players: use default duration from SettingManager
                auctionTime = isBIDAuction ? SettingManager.BIDAuctionDuration : SettingManager.BINAuctionDuration;
            }
        }

        // Any duration over 1000 days is considered infinite (for legacy support)
        if (auctionTime == -1 || auctionTime >= 86400000) {
            return -1; // Infinite
        }

        return auctionTime + SettingManager.auctionSetupTime - (new Date().getTime() - dateCreated.getTime()) / 1000;
    }

    public boolean isExpired() {
        long timeLeft = getTimeLeft();
        if (timeLeft == -1)
            return false;
        return timeLeft < 0;
    }

    public boolean isOnWaitingList() {
        if (auctionTime == 0)
            auctionTime = ConfigManager.permissions.getAuctionDuration(Bukkit.getPlayer(playerUUID), isBIDAuction);

        long elapsed = (new Date().getTime() - dateCreated.getTime()) / 1000;
        return elapsed < SettingManager.auctionSetupTime;
    }

    public double getCurrentPrice() {
        if (getPartiallySoldAmountLeft() == 0)
            return price;
        return price / getItem().getAmount() * getPartiallySoldAmountLeft();
    }

    public double getSoldPrice() {
        return partiallySoldAmountLeft == 0 ? price : price - getCurrentPrice();
    }

    public int getCurrentAmount() {
        return partiallySoldAmountLeft == 0 ? getItem().getAmount() : partiallySoldAmountLeft;
    }

    public String getSearchIndex(Player p) {
        ItemStack item = getItem();
        ItemMeta meta = item.getItemMeta();
        ArrayList<String> index = new ArrayList<>(Collections.singleton(item.toString().toLowerCase()));
        if (AuctionHouse.localeAPI) {
            List<ItemStack> translateItems = new ArrayList<>(List.of(item));
            if (meta != null) {
                if (meta instanceof BundleMeta bundleMeta)
                    translateItems.addAll(bundleMeta.getItems());
                if (ItemManager.isShulkerBox(item))
                    Collections.addAll(translateItems,
                            ((ShulkerBox) ((BlockStateMeta) meta).getBlockState()).getInventory().getContents());
            }

            for (ItemStack translateItem : translateItems) {
                if (translateItem == null)
                    continue;
                index.add(Translate.getMaterial(p, translateItem.getType()).toLowerCase());
                ItemMeta translateMeta = item.getItemMeta();
                if (translateMeta == null)
                    continue;
                for (Enchantment enchantment : translateMeta.getEnchants().keySet()) {
                    String translatedEnchantment = Translate.getEnchantment(p, enchantment);
                    if (translatedEnchantment != null)
                        index.add(translatedEnchantment.toLowerCase());
                }
                if (translateMeta instanceof PotionMeta potionMeta) {
                    PotionType type = potionMeta.getBasePotionType();
                    if (type == null)
                        continue;
                    String translatedPotion = Translate.getPotion(p, type, getPotionSort(translateItem));
                    if (translatedPotion != null)
                        index.add(translatedPotion.toLowerCase());
                }
            }
        }
        return index.toString();
    }

    private static Translate.@NotNull PotionSort getPotionSort(ItemStack translateItem) {
        Translate.PotionSort sort;
        switch (translateItem.getType()) {
            case POTION -> sort = Translate.PotionSort.POTION;
            case LINGERING_POTION -> sort = Translate.PotionSort.LINGERING_POTION;
            case SPLASH_POTION -> sort = Translate.PotionSort.SPLASH_POTION;
            default -> throw new IllegalStateException("Unexpected potion " +
                    "value: " + translateItem.getType());
        }
        return sort;
    }

    // Getters and Setters
    public String getPlayerName() {
        return playerName;
    }

    public String getBuyerName() {
        return isBIDAuction ? getLastBidderName() : buyerName;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public double getPrice() {
        return price;
    }

    public boolean isSold() {
        return isSold;
    }

    public boolean isOnAuction() {
        return !isSold || partiallySoldAmountLeft != 0;
    } // NOT INCLUDING EXPIRED

    public int getPartiallySoldAmountLeft() {
        return partiallySoldAmountLeft;
    }

    public String getAdminMessage() {
        return adminMessage;
    }

    public UUID getNoteID() {
        return noteID;
    }

    public String getItemName() {
        if (itemName == null)
            itemName = StringUtils.getItemName(getItem());
        return itemName;
    }

    public List<Bid> getBidHistoryList() {
        if (bidHistory == null)
            bidHistory = new ArrayList<>();
        return bidHistory;
    }

    public boolean hasBidHistory() {
        return bidHistory != null && !bidHistory.isEmpty();
    }

    public boolean isBIDAuction() {
        return isBIDAuction;
    }

    public String getLastBidderName() {
        return getBidHistoryList().isEmpty() ? null : getBidHistoryList().getLast().getPlayerName();
    }

    public UUID getLastBidder() {
        return getBidHistoryList().isEmpty() ? null : getBidHistoryList().getLast().getPlayerID();
    }

    public Set<UUID> getBidders() {
        return getBidHistoryList().stream()
                .map(Bid::getPlayerID)
                .collect(Collectors.toSet());
    }

    public double getBid(Player p) {
        return getBidHistoryList().stream()
                .filter(bid -> bid.getPlayerID().equals(p.getUniqueId()))
                .map(Bid::getPrice)
                .reduce((first, second) -> second)
                .orElse(0.0);
    }

    public Set<UUID> getClaimedPlayers() {
        if (claimedPlayers == null)
            claimedPlayers = new HashSet<>();
        return claimedPlayers;
    }

    public boolean canClaimBid(UUID playerID) {
        return !getClaimedPlayers().contains(playerID);
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public void addBid(Player player, double bid) {
        this.bidHistory.add(new Bid(player, new Date(), bid));
        this.price = bid;
        if (getTimeLeft() < SettingManager.lastBIDExtraTime) {
            auctionTime = SettingManager.lastBIDExtraTime - SettingManager.auctionSetupTime
                    + (new Date().getTime() - dateCreated.getTime()) / 1000;
        }
    }

    public void removeBid(Player player) {
        getClaimedPlayers().add(player.getUniqueId());
    }

    public void setSold(boolean isSold) {
        this.isSold = isSold;
    }

    public void setAdminMessage(String adminMessage) {
        this.adminMessage = adminMessage;
    }

    public void setItem(ItemStack item) {
        this.itemData = ItemStackConverter.encode(item);
    }

    public void setAuctionTime(long time) {
        this.auctionTime = time;
    }

    public void setPartiallySoldAmountLeft(int amount) {
        this.partiallySoldAmountLeft = amount;
    }

    public void setPrice(double amount) {
        this.price = amount;
    }
}
