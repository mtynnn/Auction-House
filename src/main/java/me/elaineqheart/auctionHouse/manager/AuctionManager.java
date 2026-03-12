package me.elaineqheart.auctionHouse.manager;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.configuration.SettingManager;
import me.elaineqheart.auctionHouse.configuration.Blacklist;
import me.elaineqheart.auctionHouse.configuration.ConfigManager;
import me.elaineqheart.auctionHouse.database.dao.AuctionDAO;
import me.elaineqheart.auctionHouse.model.AuctionItem;
import me.elaineqheart.auctionHouse.model.UserSession;
import me.elaineqheart.auctionHouse.util.Debug;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.elaineqheart.auctionHouse.vault.VaultHook;
import net.milkbowl.vault.economy.Economy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AuctionManager {

    private static final AuctionManager instance = new AuctionManager();
    private final AuctionDAO dao;
    private volatile boolean loaded = false;

    // RAM Cache (formerly AuctionHouseStorage) - Thread-safe collections
    private final List<UUID> itemNotes = Collections.synchronizedList(new ArrayList<>());
    private final List<UUID> sortedHighestPrice = Collections.synchronizedList(new ArrayList<>());
    private final List<UUID> sortedTimeLeft = Collections.synchronizedList(new ArrayList<>());
    private final List<UUID> sortedAlphabetical = Collections.synchronizedList(new ArrayList<>());
    private final List<UUID> sortedRecentlyPosted = Collections.synchronizedList(new ArrayList<>());
    private final Map<UUID, List<UUID>> sortedBids = new ConcurrentHashMap<>(); // player : itemNotes
    private final Map<UUID, List<UUID>> sortedPlayers = new ConcurrentHashMap<>(); // itemNote : players
    private final Map<UUID, AuctionItem> notes = new ConcurrentHashMap<>();
    private final Map<List<Map<String, Object>>, List<UUID>> categories = new ConcurrentHashMap<>();
    private final Map<UUID, Object> auctionLocks = new ConcurrentHashMap<>();

    private AuctionManager() {
        this.dao = new AuctionDAO(AuctionHouse.getPlugin().getDatabaseManager());
    }

    public static AuctionManager getInstance() {
        return instance;
    }

    public static void resetInstance() {
        instance.clear();
        instance.loaded = false;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    // --- Actions (formerly ItemNoteStorage) ---

    public AuctionItem createAuction(Player p, ItemStack item, double price, boolean isBIDAuction) {
        AuctionItem auctionItem = new AuctionItem(p, item, price, isBIDAuction);
        add(auctionItem);
        dao.save(auctionItem);
        Debug.log("Created auction id=" + auctionItem.getNoteID() + " seller=" + p.getName() + " item="
                + (item == null ? "null" : item.getType()) + " amount=" + (item == null ? 0 : item.getAmount())
                + " price=" + price + " bid=" + isBIDAuction);
        return auctionItem;
    }

    public void deleteAuction(AuctionItem item) {
        if (item != null) {
            ItemManager.invalidateCache(item.getNoteID());
        }
        remove(item);
        dao.delete(item);
        if (item != null) {
            auctionLocks.remove(item.getNoteID());
        }
        if (item != null) {
            Debug.log("Deleted auction id=" + item.getNoteID() + " seller=" + item.getPlayerName());
        }
    }

    public void updateAuction(AuctionItem item) {
        // Any setter on AuctionItem should be followed by this or explicit DAO save
        // But since we are moving logic here, we can expose specific update methods
        if (item != null) {
            ItemManager.invalidateCache(item.getNoteID());
        }
        dao.save(item);
    }

    public void addBid(AuctionItem item, Player player, double amount) {
        item.addBid(player, amount);
        ItemManager.invalidateCache(item.getNoteID());
        // Update RAM cache for bids
        addBidToCache(player.getUniqueId(), item.getNoteID());
        dao.save(item);
        Debug.log("Bid placed auction=" + item.getNoteID() + " bidder=" + player.getName() + " amount=" + amount);
    }

    public void removeBid(Player player, AuctionItem item) {
        item.removeBid(player);
        ItemManager.invalidateCache(item.getNoteID());
        removeBidFromCache(player.getUniqueId(), item.getNoteID());
        dao.save(item);
        Debug.log("Bid removed auction=" + item.getNoteID() + " bidder=" + player.getName());
    }

    public void expireAuction(AuctionItem item, String reason) {
        // Remove from auction listings
        item.setAuctionTime(-1);
        item.setAdminMessage(reason);
        // We do NOT set it as sold. It is just expired/admin removed.
        // It stays in DB but is not "on auction".
        // The owner can collect it from "Expired/Return" menu?
        // Original logic: ItemNoteStorage.setAuctionTime(note, -1);
        // ItemNoteStorage.setAdminMessage(note, reason);

        remove(item); // Update cache (remove from sorted lists)
        add(item); // Re-add to cache (will go to specific lists if applicable, or just stay in
                   // map)

        dao.save(item);
    }

    public void loadAuctions() {
        dao.loadAll();
    }

    public void purge() {
        clear();
        dao.purge();
    }

    public void flushToDatabaseSync() {
        dao.saveSnapshotSync(getAll());
    }

    // --- RAM Cache Management (formerly AuctionHouseStorage) ---

    public void add(AuctionItem note) {
        addToLists(note);
        updateSortedLists();
    }

    public void addQuietly(AuctionItem note) {
        addToLists(note);
    }

    public void finalizeLoad() {
        updateSortedLists();
        updateBids();
    }

    public void set(AuctionItem[] newNotes) {
        clear();
        for (AuctionItem note : newNotes) {
            if (note == null)
                continue;
            addToLists(note);
        }
        updateBids();
        updateSortedLists();
    }

    public void remove(AuctionItem item) {
        removeFromLists(item.getNoteID());
        if (!item.isBIDAuction() || !item.hasBidHistory())
            notes.remove(item.getNoteID());
    }

    public AuctionItem getAuction(UUID noteID) {
        return notes.get(noteID);
    }

    public List<AuctionItem> getAll() {
        return itemNotes.stream().map(notes::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    // --- Private Helper Methods ---

    private void addToLists(AuctionItem note) {
        UUID noteId = note.getNoteID();

        // Avoid duplicate UUIDs in RAM lists when lag/reload races happen.
        removeUuidEverywhere(noteId);
        notes.put(noteId, note);
        itemNotes.add(noteId);

        boolean onAuction = note.isOnAuction();
        boolean isExpired = note.isExpired();
        boolean hasAdminMsg = note.getAdminMessage() != null && !note.getAdminMessage().isEmpty();

        if (onAuction && !isExpired && !hasAdminMsg) {
            sortedHighestPrice.add(noteId);
            sortedTimeLeft.add(noteId);
            sortedAlphabetical.add(noteId);
            sortedRecentlyPosted.add(noteId);
            categories.forEach((maps, uuids) -> {
                ItemStack item = note.getItem();
                if (item != null && !Blacklist.isBlacklisted(item, maps))
                    uuids.add(noteId);
            });
        }
    }

    private void removeFromLists(UUID noteID) {
        notes.remove(noteID);
        itemNotes.remove(noteID);
        sortedHighestPrice.remove(noteID);
        sortedTimeLeft.remove(noteID);
        sortedAlphabetical.remove(noteID);
        sortedRecentlyPosted.remove(noteID);
        categories.forEach((maps, uuids) -> uuids.remove(noteID));
    }

    public void clear() {
        notes.clear();
        itemNotes.clear();
        sortedHighestPrice.clear();
        sortedTimeLeft.clear();
        sortedAlphabetical.clear();
        sortedRecentlyPosted.clear();
        sortedBids.clear();
        sortedPlayers.clear();
        categories.clear();
        auctionLocks.clear();
        ItemManager.clearCache();
    }

    private Object lockFor(UUID noteID) {
        return auctionLocks.computeIfAbsent(noteID, ignored -> new Object());
    }

    public enum PurchaseStatus {
        SUCCESS,
        NOT_FOUND,
        NOT_AVAILABLE,
        OWN_AUCTION,
        INSUFFICIENT_FUNDS,
        INVALID_AMOUNT,
        CORRUPTED_ITEM
    }

    public static class PurchaseResult {
        private final PurchaseStatus status;
        private final AuctionItem note;
        private final ItemStack boughtItem;
        private final double pricePaid;

        public PurchaseResult(PurchaseStatus status, AuctionItem note, ItemStack boughtItem, double pricePaid) {
            this.status = status;
            this.note = note;
            this.boughtItem = boughtItem;
            this.pricePaid = pricePaid;
        }

        public PurchaseStatus getStatus() {
            return status;
        }

        public AuctionItem getNote() {
            return note;
        }

        public ItemStack getBoughtItem() {
            return boughtItem;
        }

        public double getPricePaid() {
            return pricePaid;
        }
    }

    public PurchaseResult purchaseBin(Player buyer, AuctionItem note, int amount) {
        if (buyer == null || note == null || amount <= 0) {
            return new PurchaseResult(PurchaseStatus.INVALID_AMOUNT, null, null, 0.0);
        }

        UUID noteID = note.getNoteID();
        synchronized (lockFor(noteID)) {
            AuctionItem liveNote = getAuction(noteID);
            if (liveNote == null) {
                return new PurchaseResult(PurchaseStatus.NOT_FOUND, null, null, 0.0);
            }
            if (!liveNote.isOnAuction() || liveNote.isExpired() || liveNote.isBIDAuction()) {
                return new PurchaseResult(PurchaseStatus.NOT_AVAILABLE, liveNote, null, 0.0);
            }
            if (Objects.equals(liveNote.getPlayerUUID(), buyer.getUniqueId())) {
                return new PurchaseResult(PurchaseStatus.OWN_AUCTION, liveNote, null, 0.0);
            }

            ItemStack baseItem = liveNote.getItem();
            if (baseItem == null || baseItem.getAmount() <= 0) {
                return new PurchaseResult(PurchaseStatus.CORRUPTED_ITEM, liveNote, null, 0.0);
            }

            int currentAmount = liveNote.getCurrentAmount();
            if (amount > currentAmount) {
                return new PurchaseResult(PurchaseStatus.NOT_AVAILABLE, liveNote, null, 0.0);
            }

            double unitPrice = liveNote.getPrice() / baseItem.getAmount();
            double price = unitPrice * amount;

            Economy eco = VaultHook.getEconomy();
            if (eco.getBalance(buyer) < price) {
                return new PurchaseResult(PurchaseStatus.INSUFFICIENT_FUNDS, liveNote, null, price);
            }

            net.milkbowl.vault.economy.EconomyResponse withdrawResp = eco.withdrawPlayer(buyer, price);
            if (!withdrawResp.transactionSuccess()) {
                return new PurchaseResult(PurchaseStatus.INSUFFICIENT_FUNDS, liveNote, null, price);
            }

            ItemStack boughtItem = baseItem.clone();
            boughtItem.setAmount(amount);

            liveNote.setSold(true);
            liveNote.setBuyerName(buyer.getDisplayName());
            if (price != liveNote.getPrice()) {
                if (liveNote.getPartiallySoldAmountLeft() == 0) {
                    liveNote.setPartiallySoldAmountLeft(baseItem.getAmount() - amount);
                } else {
                    liveNote.setPartiallySoldAmountLeft(liveNote.getPartiallySoldAmountLeft() - amount);
                }
                if (liveNote.getPartiallySoldAmountLeft() < 0) {
                    liveNote.setPartiallySoldAmountLeft(0);
                }
            }
            dao.save(liveNote);

            return new PurchaseResult(PurchaseStatus.SUCCESS, liveNote, boughtItem, price);
        }
    }

    public boolean cancelAuctionAndReturnItem(Player player, AuctionItem note) {
        if (player == null || note == null) {
            return false;
        }

        UUID noteID = note.getNoteID();
        synchronized (lockFor(noteID)) {
            AuctionItem liveNote = getAuction(noteID);
            if (liveNote == null) {
                return false;
            }
            if (!Objects.equals(liveNote.getPlayerUUID(), player.getUniqueId())) {
                return false;
            }
            if (!liveNote.isOnAuction() || liveNote.isExpired()) {
                return false;
            }
            if (liveNote.isBIDAuction() && liveNote.hasBidHistory()) {
                return false;
            }

            ItemStack item = liveNote.getItem();
            if (item == null || player.getInventory().firstEmpty() == -1) {
                return false;
            }

            player.getInventory().addItem(item.clone());
            deleteAuction(liveNote);
            return true;
        }
    }

    private void removeUuidEverywhere(UUID noteID) {
        itemNotes.removeIf(uuid -> uuid.equals(noteID));
        sortedHighestPrice.removeIf(uuid -> uuid.equals(noteID));
        sortedTimeLeft.removeIf(uuid -> uuid.equals(noteID));
        sortedAlphabetical.removeIf(uuid -> uuid.equals(noteID));
        sortedRecentlyPosted.removeIf(uuid -> uuid.equals(noteID));
        categories.forEach((maps, uuids) -> uuids.removeIf(uuid -> uuid.equals(noteID)));
    }

    private void updateSortedLists() {
        sortedAlphabetical.sort(Comparator.comparing(o -> notes.get(o).getItemName()));
        sortedHighestPrice.sort(Comparator.comparing(o -> notes.get(o).getPrice()));
        sortedTimeLeft.sort((Comparator.comparing(o -> notes.get(o).getTimeLeft())));
        sortedRecentlyPosted.sort(Comparator.comparing((UUID o) -> notes.get(o).getDateCreated()).reversed());
    }

    // --- Bids ---

    private void updateBids() {
        sortedBids.clear();
        sortedPlayers.clear();
        for (UUID noteID : itemNotes) {
            AuctionItem note = notes.get(noteID);
            for (UUID playerID : note.getBidders()) {
                if (canCollectBid(note, playerID))
                    addBidToCache(playerID, note.getNoteID());
            }
        }
    }

    public void addBidToCache(UUID playerID, UUID noteID) {
        sortedBids.computeIfAbsent(playerID, k -> new ArrayList<>());
        List<UUID> bids = sortedBids.get(playerID);
        if (!bids.contains(noteID))
            bids.addFirst(noteID);

        sortedPlayers.computeIfAbsent(noteID, k -> new ArrayList<>());
        List<UUID> players = sortedPlayers.get(noteID);
        if (!players.contains(playerID))
            players.addFirst(playerID);
    }

    public void removeBidFromCache(UUID player, UUID noteID) {
        if (sortedBids.containsKey(player))
            sortedBids.get(player).remove(noteID);
        if (sortedPlayers.containsKey(noteID)) {
            sortedPlayers.get(noteID).remove(player);
            checkRemove(noteID);
        }
    }

    private void checkRemove(UUID noteID) {
        if (!notes.get(noteID).isBIDAuction())
            return;
        if (sortedPlayers.get(noteID).isEmpty() && notes.get(noteID).isSold()) {
            sortedPlayers.remove(noteID);
            sortedBids.remove(notes.get(noteID).getPlayerUUID());
            removeFromLists(noteID);
        }
    }

    public boolean canCollectBid(AuctionItem item, UUID player) {
        return !item.getClaimedPlayers().contains(player);
    }

    public List<AuctionItem> getMyBids(UUID playerID) {
        if (!sortedBids.containsKey(playerID))
            return List.of();
        return sortedBids.get(playerID).stream()
                .map(notes::get)
                .filter(itemNote -> itemNote.canClaimBid(playerID))
                .collect(Collectors.toList());
    }

    // --- Sorting & Filtering ---

    public enum SortMode {
        RECENTLY_POSTED,
        NAME,
        PRICE_ASC,
        PRICE_DESC,
        DATE
    }

    public List<AuctionItem> getSortedList(SortMode mode, UserSession c) {
        String search = c != null ? c.getCurrentSearch().toLowerCase() : "";
        Player sessionPlayer = c != null ? c.getPlayer() : null;
        List<UUID> list;
        switch (mode) {
            case RECENTLY_POSTED -> list = snapshotOf(sortedRecentlyPosted);
            case DATE -> list = snapshotOf(sortedTimeLeft);
            case NAME -> list = snapshotOf(sortedAlphabetical);
            case PRICE_ASC -> list = snapshotOf(sortedHighestPrice);
            case PRICE_DESC -> {
                List<UUID> desc = snapshotOf(sortedHighestPrice);
                Collections.reverse(desc);
                list = desc;
            }
            default -> list = List.of();
        }

        List<AuctionItem> result = new ArrayList<>();
        int filteredOut_notOnAuctionOrExpired = 0;
        int filteredOut_waitingList = 0;
        int filteredOut_adminMsg = 0;
        int filteredOut_search = 0;
        int filteredOut_bin = 0;

        for (UUID uuid : list) {
            AuctionItem note = notes.get(uuid);
            if (note == null)
                continue;

            // Visibility checks
            if (!note.isOnAuction() || note.isExpired()) {
                filteredOut_notOnAuctionOrExpired++;
                continue;
            }
            if (note.isOnWaitingList()) {
                filteredOut_waitingList++;
                continue;
            }
            if (note.getAdminMessage() != null && !note.getAdminMessage().isEmpty()) {
                filteredOut_adminMsg++;
                continue;
            }

            // Search filter
            if (!search.isEmpty()) {
                if (sessionPlayer == null || !note.getSearchIndex(sessionPlayer).contains(search)) {
                    filteredOut_search++;
                    continue;
                }
            }

            // BIN filter
            switch (c != null ? c.getBinFilter() : UserSession.BINFilter.ALL) {
                case AUCTIONS_ONLY:
                    if (!note.isBIDAuction()) {
                        filteredOut_bin++;
                        continue;
                    }
                    break;
                case BIN_ONLY:
                    if (note.isBIDAuction()) {
                        filteredOut_bin++;
                        continue;
                    }
                    break;
                case ALL:
                default:
                    break;
            }

            result.add(note);
        }

        if (Debug.isEnabled() && result.isEmpty() && c != null && sessionPlayer != null) {
            Debug.log("AH list empty for " + sessionPlayer.getName()
                    + " search=\"" + c.getCurrentSearch() + "\" binFilter=" + c.getBinFilter()
                    + " filtered(notOnAuction/expired)=" + filteredOut_notOnAuctionOrExpired
                    + " waiting=" + filteredOut_waitingList
                    + " adminMsg=" + filteredOut_adminMsg
                    + " search=" + filteredOut_search
                    + " bin=" + filteredOut_bin);
        }
        return result;
    }

    private List<UUID> snapshotOf(List<UUID> source) {
        synchronized (source) {
            return new ArrayList<>(source);
        }
    }

    public void applyWhitelist(List<AuctionItem> notes, List<Map<?, ?>> whitelist) {
        // Similar logic to original...
        List<Map<String, Object>> verifiedList = new ArrayList<>();
        if (whitelist != null) {
            for (Map<?, ?> entry : whitelist) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castEntry = (Map<String, Object>) entry;
                verifiedList.add(castEntry);
            }
        }

        if (!categories.containsKey(verifiedList)) {
            addWhiteList(verifiedList);
        }
        notes.removeIf(note -> !categories.get(verifiedList).contains(note.getNoteID()));
    }

    private void addWhiteList(List<Map<String, Object>> whitelist) {
        categories.put(whitelist, itemNotes.stream()
                .filter(noteID -> {
                    AuctionItem note = notes.get(noteID);
                    if (note == null) return false;
                    ItemStack item = note.getItem();
                    return item != null && !Blacklist.isBlacklisted(item, whitelist);
                })
                .collect(Collectors.toList()));
    }

    public List<AuctionItem> getMySortedDateCreated(UUID playerID) {
        return itemNotes.stream()
                .map(notes::get)
                .filter(Objects::nonNull)
                .filter(note -> Objects.equals(note.getPlayerUUID(), playerID))
                .filter(note -> !(note.isBIDAuction() && note.isSold()))
                .toList();
    }

    public boolean claimSoldItemMoney(OfflinePlayer p, AuctionItem note) {
        if (note == null || p == null || p.getUniqueId() == null)
            return false;

        UUID noteID = note.getNoteID();
        synchronized (lockFor(noteID)) {
            // Resolve against current manager state to avoid stale GUI references.
            AuctionItem liveNote = getAuction(noteID);
            if (liveNote == null)
                return false;
            if (!Objects.equals(liveNote.getPlayerUUID(), p.getUniqueId()))
                return false;

            double price = liveNote.getSoldPrice();
            if (liveNote.isBIDAuction() && liveNote.isSold())
                return false;

            Economy eco = VaultHook.getEconomy();
            double profit = price; // No tax applied
            net.milkbowl.vault.economy.EconomyResponse depositResp = eco.depositPlayer(p, profit);
            if (!depositResp.transactionSuccess()) {
                AuctionHouse.getPlugin().getLogger().warning(
                    "[AuctionHouse] depositPlayer failed for " + p.getName() + " amount=" + profit
                    + " reason=" + depositResp.errorMessage);
            }

            if (liveNote.getPartiallySoldAmountLeft() != 0) {
                // Convert listing to the remaining unsold amount to avoid repeated claims.
                int remainingAmount = liveNote.getPartiallySoldAmountLeft();
                ItemStack baseItem = liveNote.getItem();
                if (baseItem == null) {
                    return false;
                }
                ItemStack remaining = baseItem.clone();
                remaining.setAmount(remainingAmount);

                liveNote.setPrice(liveNote.getCurrentPrice());
                liveNote.setItem(remaining);
                liveNote.setPartiallySoldAmountLeft(0);
                liveNote.setSold(false);
                dao.save(liveNote);
            } else {
                if (!liveNote.isBIDAuction()) {
                    deleteAuction(liveNote);
                } else {
                    liveNote.setSold(true);
                    checkRemove(liveNote.getNoteID());
                    dao.save(liveNote);
                }
            }
            return true;
        }
    }

    // RETHINK: The GUI logic is complex regarding partial sales.
    // For now, I will NOT move the complex partial sale logic to Manager to avoid
    // breaking it without seeing `ItemManager`.
    // I will only move the simple updates and DAO calls.
    // I will expose `deleteAuction` and `updateAuction` (which I already did).
    // I will implement helper for 'claimBid' and 'claimWonItem' as they are
    // simpler.

    public boolean claimWonItem(Player p, AuctionItem note) {
        if (p == null || note == null) {
            return false;
        }

        UUID noteID = note.getNoteID();
        synchronized (lockFor(noteID)) {
            AuctionItem liveNote = getAuction(noteID);
            if (liveNote == null || !liveNote.isBIDAuction()) {
                return false;
            }
            if (!liveNote.isExpired()) {
                return false;
            }
            if (!Objects.equals(liveNote.getLastBidder(), p.getUniqueId())) {
                return false;
            }
            if (!canCollectBid(liveNote, p.getUniqueId())) {
                return false;
            }

            ItemStack item = liveNote.getItem();
            if (item == null) {
                p.sendMessage(ChatColor.RED + "Error: This item cannot be loaded (missing plugin dependency). Contact an admin.");
                return false;
            }
            if (p.getInventory().firstEmpty() == -1) {
                return false;
            }

            ItemStack tracedItem = ItemManager.stampAuctionPurchase(item, liveNote, p);
            p.getInventory().addItem(tracedItem);
            removeBid(p, liveNote);

            ConfigManager.transactionLogger.logTransaction(
                    p.getUniqueId(),
                    liveNote.getPlayerUUID(),
                    item.getType().name(),
                    liveNote.getPrice(),
                    item.getAmount(),
                    !liveNote.isBIDAuction());

            dao.save(liveNote);
            return true;
        }
    }

    public boolean claimExpiredItem(Player p, AuctionItem note) {
        if (note == null || p == null)
            return false;

        UUID noteID = note.getNoteID();
        synchronized (lockFor(noteID)) {
            // Resolve against live manager state to avoid claiming with stale GUI references.
            AuctionItem liveNote = getAuction(noteID);
            if (liveNote == null)
                return false;
            if (!Objects.equals(liveNote.getPlayerUUID(), p.getUniqueId()))
                return false;
            if (!liveNote.isExpired())
                return false;

            ItemStack item = liveNote.getItem();
            if (item == null) {
                p.sendMessage(ChatColor.RED + "Error: This item cannot be loaded (missing plugin dependency). Contact an admin.");
                return false;
            }

            // check if inventory is full
            if (p.getInventory().firstEmpty() == -1) {
                return false;
            }

            p.getInventory().addItem(item.clone());
            deleteAuction(liveNote);
            return true;
        }
    }

    public boolean claimBidMoney(Player p, AuctionItem note) {
        if (p == null || note == null) {
            return false;
        }

        UUID noteID = note.getNoteID();
        synchronized (lockFor(noteID)) {
            AuctionItem liveNote = getAuction(noteID);
            if (liveNote == null || !liveNote.isBIDAuction()) {
                return false;
            }
            if (!liveNote.isExpired()) {
                return false;
            }
            if (Objects.equals(liveNote.getLastBidder(), p.getUniqueId())) {
                return false;
            }
            if (!canCollectBid(liveNote, p.getUniqueId())) {
                return false;
            }

            double amount = liveNote.getBid(p);
            if (amount <= 0) {
                return false;
            }

            net.milkbowl.vault.economy.EconomyResponse refundResp = VaultHook.getEconomy().depositPlayer(p, amount);
            if (!refundResp.transactionSuccess()) {
                AuctionHouse.getPlugin().getLogger().warning(
                    "[AuctionHouse] bid refund depositPlayer failed for " + p.getName() + " amount=" + amount
                    + " reason=" + refundResp.errorMessage);
            }
            removeBid(p, liveNote);
            return true;
        }
    }

    public int getNumberOfAuctions(UUID playerID) {
        return getMySortedDateCreated(playerID).size();
    }
}
