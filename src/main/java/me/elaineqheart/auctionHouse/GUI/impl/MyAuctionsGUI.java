package me.elaineqheart.auctionHouse.GUI.impl;

import me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.GUI.InventoryButton;
import me.elaineqheart.auctionHouse.GUI.InventoryGUI;
import me.elaineqheart.auctionHouse.GUI.other.Sounds;
import me.elaineqheart.auctionHouse.TaskManager;
import me.elaineqheart.auctionHouse.manager.AuctionManager;
import me.elaineqheart.auctionHouse.configuration.M;
import me.elaineqheart.auctionHouse.configuration.SettingManager;
import me.elaineqheart.auctionHouse.configuration.SlotConfigManager;
import me.elaineqheart.auctionHouse.configuration.ConfigManager;
import me.elaineqheart.auctionHouse.database.dao.TransactionDAO;
import me.elaineqheart.auctionHouse.model.*;
import me.elaineqheart.auctionHouse.manager.ItemManager;
import me.elaineqheart.auctionHouse.util.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class MyAuctionsGUI extends InventoryGUI implements Runnable {

    private static final String GUI_NAME = "MyAuctionsGUI";

    private UUID invID = UUID.randomUUID();
    private final UserSession c;
    private int noteSize;
    private int screenSize;

    @Override
    public void run() {
        Player player = c.getPlayer();
        if (player == null || !player.isOnline()) {
            TaskManager.cancelTask(invID);
            return;
        }
        decorate(player);
    }

    public enum MySort {
        ALL_AUCTIONS,
        SOLD_ITEMS,
        EXPIRED_ITEMS,
        ACTIVE_AUCTIONS
    }

    public MyAuctionsGUI(UserSession configuration) {
        super();
        c = configuration;
        c.setView(UserSession.View.MY_AUCTIONS);
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
    }

    @Override
    protected Inventory createInventory() {
        int size = SlotConfigManager.getSize(GUI_NAME);
        return Bukkit.createInventory(null, size, GuiConfigManager.myAuctions().getTitle());
    }

    @Override
    public void decorate(Player player) {
        // Fill with filler items
        fillFiller(player);

        // Place auction items
        List<Integer> itemSlots = SlotConfigManager.getItemSlots(GUI_NAME);
        fillOutItems(player, itemSlots);
        fillOutBarriers(ConfigManager.permissions.getAuctionSlots(player), itemSlots);

        // Place control buttons
        placeControlButtons(player);

        super.decorate(player);
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        TaskManager.cancelTask(invID);
    }

    private void update() {
        Player player = c.getPlayer();
        if (player == null || !player.isOnline()) {
            TaskManager.cancelTask(invID);
            return;
        }
        decorate(player);
        TaskManager.cancelTask(invID);
        invID = UUID.randomUUID();
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
    }

    private void fillFiller(Player player) {
        int size = SlotConfigManager.getSize(GUI_NAME);
        List<Integer> itemSlots = SlotConfigManager.getItemSlots(GUI_NAME);
        Set<Integer> reservedSlots = new HashSet<>(itemSlots);

        // Add control button slots
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "back"));
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "sort"));
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "previous-page"));
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "refresh"));
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "next-page"));
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "collect-all"));
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "info"));

        ItemStack filler = SlotConfigManager.createFillerItem(GUI_NAME);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < size; i++) {
            if (!reservedSlots.contains(i)) {
                int slot = i;
                this.addButton(slot, new InventoryButton()
                        .creator(p -> filler)
                        .consumer(event -> {
                        }));
            }
        }
    }

    private void placeControlButtons(Player player) {
        // Back
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "back"), back());

        // Sort
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "sort"), sortButton());

        // Pagination (only if player has more slots than screen)
        int playerSlots = ConfigManager.permissions.getAuctionSlots(player);
        if (playerSlots > screenSize) {
            this.addButton(SlotConfigManager.getSlot(GUI_NAME, "previous-page"), previousPage());
            this.addButton(SlotConfigManager.getSlot(GUI_NAME, "next-page"), nextPage());
        } else {
            this.addButton(SlotConfigManager.getSlot(GUI_NAME, "previous-page"), fillerButton());
            this.addButton(SlotConfigManager.getSlot(GUI_NAME, "next-page"), fillerButton());
        }

        // Refresh
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "refresh"), refresh());

        // My Bids (only if bids enabled)
        int collectAllSlot = SlotConfigManager.getSlot(GUI_NAME, "collect-all");
        if (SettingManager.BIDAuctions) {
            this.addButton(collectAllSlot, myBids());
        } else {
            this.addButton(collectAllSlot, fillerButton());
        }

        // Info
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "info"), info());
    }

    private void fillOutItems(Player p, List<Integer> itemSlots) {
        List<AuctionItem> myAuctions = AuctionManager.getInstance().getMySortedDateCreated(p.getUniqueId());
        List<AuctionItem> returnList;
        switch (c.getMyCurrentSort()) {
            case SOLD_ITEMS -> returnList = myAuctions.stream()
                    .filter(note -> note.isSold() || note.isBIDAuction() && note.hasBidHistory() && note.isExpired())
                    .collect(Collectors.toList());
            case EXPIRED_ITEMS -> returnList = myAuctions.stream()
                    .filter(note -> note.isExpired()
                            && (!note.isBIDAuction() && !note.isSold() || note.isBIDAuction() && !note.hasBidHistory()))
                    .collect(Collectors.toList());
            case ACTIVE_AUCTIONS -> returnList = myAuctions.stream()
                    .filter(note -> !note.isExpired() && note.isOnAuction())
                    .collect(Collectors.toList());
            default -> returnList = myAuctions;
        }
        createButtonsForAuctionItems(returnList, itemSlots);
    }

    private void createButtonsForAuctionItems(List<AuctionItem> myAuctions, List<Integer> itemSlots) {
        noteSize = myAuctions.size();
        screenSize = itemSlots.size();
        int start = c.getMyCurrentPage() * screenSize;
        int stop = start + screenSize;
        int end = Math.min(noteSize, stop);
        myAuctions = myAuctions.subList(start, end);
        int size = myAuctions.size();
        for (int i = 0; i < screenSize; ++i) {
            int j = itemSlots.get(i);
            if (size - 1 < i) {
                // Empty slot - display empty without permission check

                this.addButton(j, new InventoryButton()
                        .creator(player -> null)
                        .consumer(event -> {
                        }));
                continue;
            }
            AuctionItem note = myAuctions.stream().skip(i).findFirst().orElse(null);
            if (note == null)
                continue;
            this.addButton(j, auctionItem(note));
        }
    }

    private InventoryButton auctionItem(AuctionItem note) {
        ItemStack item = ItemManager.createMyAuctionItem(note, c.getPlayer());
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    if (ItemManager.isShulkerBox(item) && event.isRightClick()) {
                        Sounds.openShulker(event);
                        AuctionHouse.getGuiManager().openGUI(
                                new ShulkerViewGUI(note, c, UserSession.View.AUCTION_HOUSE), c.getPlayer());
                        return;
                    }
                    Sounds.click(event);
                    if (note.isSold() || note.isBIDAuction() && note.hasBidHistory() && note.isExpired()
                            && note.getAdminMessage() == null) {
                        Player p = c.getPlayer();
                        double soldPrice = note.getSoldPrice();
                        boolean success = AuctionManager.getInstance().claimSoldItemMoney(p, note);
                        if (success) {
                            Sounds.experience(event);
                            p.sendMessage(M.getFormatted("chat.claimed-money", soldPrice));
                            update();
                        } else {
                            Sounds.villagerDeny(event);
                        }
                    } else if (note.isExpired()) {
                        Player p = c.getPlayer();
                        String itemName = note.getItemName();
                        boolean success = AuctionManager.getInstance().claimExpiredItem(p, note);
                        if (success) {
                            Sounds.experience(event);
                            p.sendMessage(M.getFormatted("chat.claimed-item", "%item%", itemName));
                            update();
                        } else {
                            p.sendMessage(M.getFormatted("chat.inventory-full"));
                            Sounds.villagerDeny(event);
                        }
                    } else {
                        if (!note.isBIDAuction())
                            AuctionHouse.getGuiManager().openGUI(new CancelAuctionGUI(note, c), c.getPlayer());
                        else
                            AuctionHouse.getGuiManager().openGUI(
                                    new AuctionViewGUI(note, c, 0, UserSession.View.MY_AUCTIONS), c.getPlayer());
                    }
                });
    }

    private void fillOutBarriers(int auctions, List<Integer> itemSlots) {
        int startPage = c.getMyCurrentPage() * screenSize + screenSize;
        int barriers = startPage - auctions;
        for (int i = 0; i < barriers; i++) {
            this.addButton(itemSlots.get(screenSize - i - 1), barrier());
        }
    }

    private InventoryButton fillerButton() {
        ItemStack filler = SlotConfigManager.createFillerItem(GUI_NAME);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            filler.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> filler)
                .consumer(event -> {
                });
    }

    private InventoryButton barrier() {
        ItemStack barrier = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "locked-slot"));
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.myAuctions().getItemName("locked-slot"));
            meta.setLore(GuiConfigManager.myAuctions().getLore("locked-slot-lore"));
            barrier.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> barrier)
                .consumer(event -> {
                    Sounds.villagerDeny(event);
                });
    }

    private InventoryButton refresh() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "refresh"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.myAuctions().getItemName("refresh"));
            meta.setLore(GuiConfigManager.myAuctions().getLore("refresh-lore"));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Sounds.click(event);
                    AuctionHouse.getGuiManager().openGUI(new MyAuctionsGUI(c), c.getPlayer());
                });
    }

    private InventoryButton sortButton() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "sort"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.myAuctions().getItemName("sort"));

            List<String> lore = new ArrayList<>();
            lore.add(GuiConfigManager.myAuctions().getLoreLine("filter-lore", "divider-top"));

            // All
            if (c.getMyCurrentSort() == MySort.ALL_AUCTIONS) {
                lore.add(GuiConfigManager.myAuctions().getLoreLine("filter-lore", "all"));
            } else {
                lore.add(GuiConfigManager.myAuctions().getLoreLine("filter-lore", "all-inactive"));
            }

            // Sold
            if (c.getMyCurrentSort() == MySort.SOLD_ITEMS) {
                lore.add(GuiConfigManager.myAuctions().getLoreLine("filter-lore", "sold"));
            } else {
                lore.add(GuiConfigManager.myAuctions().getLoreLine("filter-lore", "sold-inactive"));
            }

            // Expired
            if (c.getMyCurrentSort() == MySort.EXPIRED_ITEMS) {
                lore.add(GuiConfigManager.myAuctions().getLoreLine("filter-lore", "expired"));
            } else {
                lore.add(GuiConfigManager.myAuctions().getLoreLine("filter-lore", "expired-inactive"));
            }

            // Active
            if (c.getMyCurrentSort() == MySort.ACTIVE_AUCTIONS) {
                lore.add(GuiConfigManager.myAuctions().getLoreLine("filter-lore", "active"));
            } else {
                lore.add(GuiConfigManager.myAuctions().getLoreLine("filter-lore", "active-inactive"));
            }

            lore.add(GuiConfigManager.myAuctions().getLoreLine("filter-lore", "divider-bottom"));
            lore.add(GuiConfigManager.myAuctions().getLoreLine("filter-lore", "click-to-switch"));
            lore.add(GuiConfigManager.myAuctions().getLoreLine("filter-lore", "right-click"));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Sounds.click(event);
                    if (event.isRightClick())
                        c.setMyCurrentSort(previousSort(c.getMyCurrentSort()));
                    else
                        c.setMyCurrentSort(nextSort(c.getMyCurrentSort()));
                    c.setMyCurrentPage(0);
                    update();
                });
    }

    private InventoryButton back() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "back"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.myAuctions().getItemName("back"));
            meta.setLore(GuiConfigManager.myAuctions().getLore("back-lore"));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Sounds.closeEnderChest(event);
                    AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), c.getPlayer());
                });
    }

    private InventoryButton info() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "info"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.myAuctions().getItemName("info"));
            
            // Load transaction history asynchronously
            List<String> lore = new ArrayList<>();
            lore.add(StringUtils.colorize("<gray>Cargando historial..."));
            meta.setLore(lore);
            item.setItemMeta(meta);
            
            // Load transactions asynchronously and update item
            TransactionDAO dao = new TransactionDAO();
            dao.getRecentTransactions(c.getPlayer().getUniqueId(), 10).thenAccept(transactions -> {
                Bukkit.getScheduler().runTask(AuctionHouse.getPlugin(), () -> {
                    List<String> historyLore = new ArrayList<>();
                    historyLore.add(StringUtils.colorize("<color:#FFD180>Historial de Transacciones"));
                    historyLore.add("");
                    
                    if (transactions.isEmpty()) {
                        historyLore.add(StringUtils.colorize("<gray>No hay transacciones recientes"));
                    } else {
                        for (TransactionDAO.Transaction tx : transactions) {
                            long timePassed = System.currentTimeMillis() - tx.date;
                            String timeAgo = StringUtils.getTime(timePassed, false);
                            String priceStr = StringUtils.formatPrice(tx.price);
                            
                            // Format: + 2,200, 20 Horas atras por Venta de items
                            String sign = tx.isSeller ? "+" : "-";
                            String action = tx.isSeller ? "Venta" : "Compra";
                            String colorCode = tx.isSeller ? "<green>" : "<red>";
                            
                            historyLore.add(StringUtils.colorize(colorCode + sign + " " + priceStr + "<gray>, " + 
                                          timeAgo + " <gray>por " + action));
                        }
                    }
                    
                    historyLore.add("");
                    historyLore.add(StringUtils.colorize("<dark_gray>Plugin por ElaineQheart"));
                    
                    ItemMeta updatedMeta = item.getItemMeta();
                    if (updatedMeta != null) {
                        updatedMeta.setLore(historyLore);
                        item.setItemMeta(updatedMeta);
                    }
                });
            });
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                });
    }

    private InventoryButton myBids() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "collect-all"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.myAuctions().getItemName("my-bids"));
            meta.setLore(GuiConfigManager.myAuctions().getLore("my-bids-lore"));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Sounds.click(event);
                    AuctionHouse.getGuiManager().openGUI(new MyBidsGUI(c, 0), c.getPlayer());
                });
    }

    private InventoryButton nextPage() {
        int pages = (noteSize - 1) / screenSize;
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "next-page"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.myAuctions().getItemName("next-page"));
            meta.setLore(GuiConfigManager.myAuctions().getLore("next-page-lore",
                    "{page}", String.valueOf(c.getMyCurrentPage() + 1),
                    "{pages}", String.valueOf(pages + 1)));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    if (c.getMyCurrentPage() == pages)
                        return;
                    if (event.isRightClick())
                        c.setMyCurrentPage(pages);
                    else
                        c.setMyCurrentPage(c.getMyCurrentPage() + 1);
                    Sounds.click(event);
                    update();
                });
    }

    private InventoryButton previousPage() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "previous-page"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.myAuctions().getItemName("prev-page"));
            meta.setLore(GuiConfigManager.myAuctions().getLore("prev-page-lore",
                    "{page}", String.valueOf(c.getMyCurrentPage() + 1),
                    "{pages}", String.valueOf((noteSize - 1) / screenSize + 1)));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    if (c.getMyCurrentPage() == 0)
                        return;
                    if (event.isRightClick())
                        c.setMyCurrentPage(0);
                    else
                        c.setMyCurrentPage(c.getMyCurrentPage() - 1);
                    Sounds.click(event);
                    update();
                });
    }

    private MySort nextSort(MySort input) {
        return switch (input) {
            case ALL_AUCTIONS -> MySort.SOLD_ITEMS;
            case SOLD_ITEMS -> MySort.EXPIRED_ITEMS;
            case EXPIRED_ITEMS -> MySort.ACTIVE_AUCTIONS;
            case ACTIVE_AUCTIONS -> MySort.ALL_AUCTIONS;
        };
    }

    private MySort previousSort(MySort input) {
        return switch (input) {
            case ACTIVE_AUCTIONS -> MySort.EXPIRED_ITEMS;
            case EXPIRED_ITEMS -> MySort.SOLD_ITEMS;
            case SOLD_ITEMS -> MySort.ALL_AUCTIONS;
            case ALL_AUCTIONS -> MySort.ACTIVE_AUCTIONS;
        };
    }
}
