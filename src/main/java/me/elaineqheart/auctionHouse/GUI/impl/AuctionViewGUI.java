package me.elaineqheart.auctionHouse.GUI.impl;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.GUI.InventoryButton;
import me.elaineqheart.auctionHouse.GUI.InventoryGUI;

import me.elaineqheart.auctionHouse.GUI.other.InputManager;
import me.elaineqheart.auctionHouse.GUI.other.Sounds;
import me.elaineqheart.auctionHouse.TaskManager;
import me.elaineqheart.auctionHouse.util.StringUtils;
import me.elaineqheart.auctionHouse.manager.AuctionManager;
import me.elaineqheart.auctionHouse.configuration.SettingManager;
import me.elaineqheart.auctionHouse.configuration.M;
import me.elaineqheart.auctionHouse.configuration.SlotConfigManager;
import me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager;
import me.elaineqheart.auctionHouse.model.UserSession;
import me.elaineqheart.auctionHouse.model.Bid;
import me.elaineqheart.auctionHouse.manager.ItemManager;
import me.elaineqheart.auctionHouse.model.AuctionItem;
import me.elaineqheart.auctionHouse.vault.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class AuctionViewGUI extends InventoryGUI implements Runnable {

    private static final String GUI_NAME = "AuctionViewGUI";

    private final AuctionItem note;
    private UUID invID = UUID.randomUUID();
    private final UserSession c;
    private double bid;
    private boolean topBid;
    private final UserSession.View goBackTo;

    public static Map<Player, AuctionViewGUI> currentGUIs = new HashMap<>();

    @Override
    public void run() {
        decorate(c.getPlayer());
    }

    public void update() {
        TaskManager.cancelTask(invID);
        Bukkit.getScheduler().runTask(AuctionHouse.getPlugin(), () -> decorate(c.getPlayer()));
        invID = UUID.randomUUID();
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
    }

    public AuctionViewGUI(AuctionItem note, UserSession configuration, double bid, UserSession.View backTo) {
        super();
        this.note = note;
        c = configuration;
        this.goBackTo = backTo;
        c.setView(UserSession.View.AUCTION_VIEW);
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
        this.bid = bid;
        if (this.bid == 0)
            this.bid = note.hasBidHistory() ? Bid.nextMinBid(note.getPrice()) : note.getPrice();
        currentGUIs.put(c.getPlayer(), this);
    }

    @Override
    protected Inventory createInventory() {
        int size = SlotConfigManager.getSize(GUI_NAME);
        return Bukkit.createInventory(null, size, GuiConfigManager.auctionView().getTitle("auction-view"));
    }

    @Override
    public void decorate(Player player) {
        topBid = Objects.equals(note.getLastBidder(), player.getUniqueId());
        double newBid = note.hasBidHistory() ? Bid.nextMinBid(note.getPrice()) : note.getPrice();
        if (this.bid < newBid)
            this.bid = newBid;

        // Fill with filler
        fillFiller();

        // Item display
        int itemSlot = SlotConfigManager.getSlot(GUI_NAME, "item-display");
        this.addButton(itemSlot, buyingItem());

        if (note.isBIDAuction())
            decorateBID(player);
        else
            decorateBIN(player);

        if (c.shouldKeepOpen()) {
            int backSlot = SlotConfigManager.getSlot(GUI_NAME, "back");
            this.addButton(backSlot, back());
        }
        super.decorate(player);
    }

    private void fillFiller() {
        int size = SlotConfigManager.getSize(GUI_NAME);
        ItemStack filler = SlotConfigManager.createFillerItem(GUI_NAME);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < size; i++) {
            int slot = i;
            this.addButton(slot, new InventoryButton()
                    .creator(player -> filler)
                    .consumer(event -> {
                    }));
        }
    }

    private void decorateBID(Player player) {
        int bidHistorySlot = SlotConfigManager.getSlot(GUI_NAME, "bid-history");
        int submitBidSlot = SlotConfigManager.getSlot(GUI_NAME, "submit-bid");
        int bidExplanationSlot = SlotConfigManager.getSlot(GUI_NAME, "bid-explanation");
        int cancelSlot = SlotConfigManager.getSlot(GUI_NAME, "cancel-auction");

        this.addButton(bidHistorySlot, bidHistory());
        if (topBid) {
            this.addButton(submitBidSlot, topBid());
            return;
        }
        double increase = bid - note.getBid(player);
        if (note.getPlayerUUID().equals(player.getUniqueId())) {
            if (!note.hasBidHistory())
                this.addButton(cancelSlot, cancelAuction());
            this.addButton(submitBidSlot, submitBid());
            return;
        }
        if (VaultHook.getEconomy().getBalance(player) < increase) {
            this.addButton(submitBidSlot, cannotAffordBid());
        } else {
            this.addButton(submitBidSlot, submitBid());
            this.addButton(bidExplanationSlot, bidExplanation());
        }
    }

    private void decorateBIN(Player player) {
        int buySlot = SlotConfigManager.getSlot(GUI_NAME, "buy-button");
        int partialSlot = SlotConfigManager.getSlot(GUI_NAME, "partial-buy-button");
        int setAmountSlot = SlotConfigManager.getSlot(GUI_NAME, "set-amount");

        int slot = SettingManager.partialSelling && note.getCurrentAmount() > 1 ? partialSlot : buySlot;
        if (VaultHook.getEconomy().getBalance(player) < note.getPrice()) {
            this.addButton(slot, armadilloScute());
        } else {
            this.addButton(slot, turtleScute());
        }
        if (slot == partialSlot) {
            this.addButton(setAmountSlot, sign());
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        TaskManager.cancelTask(invID);
        currentGUIs.remove(c.getPlayer());
    }

    private InventoryButton buyingItem() {
        ItemStack item = ItemManager.createItemFromNote(note, c.getPlayer(), false, false);
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    if (ItemManager.isShulkerBox(item) && event.isRightClick()) {
                        Sounds.openShulker(event);
                        AuctionHouse.getGuiManager().openGUI(new ShulkerViewGUI(note, c, goBackTo), c.getPlayer());
                    }
                });
    }

    private InventoryButton back() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "back"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionView().getItemName("back"));
            meta.setLore(GuiConfigManager.auctionView().getLore("back-lore"));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Player p = (Player) event.getWhoClicked();
                    Sounds.click(event);
                    openGUI(p);
                });
    }

    private InventoryButton armadilloScute() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "buy-cannot-afford"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionView().getItemName("cannot-afford"));
            meta.setLore(GuiConfigManager.auctionView().getLore("not-enough-money-lore", "{price}",
                    String.format("%.2f", note.getPrice())));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(Sounds::villagerDeny);
    }

    private InventoryButton turtleScute() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "buy-afford"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionView().getItemName("buy"));
            meta.setLore(GuiConfigManager.auctionView().getLore("buy-item-lore", "{price}",
                    String.format("%.2f", note.getCurrentPrice())));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Sounds.click(event);
                    if (note.getPlayerName().equals(event.getWhoClicked().getName())) {
                        event.getWhoClicked().sendMessage(M.getFormatted("chat.own-auction"));
                        return;
                    }
                    ItemStack auctionItem = note.getItem();
                    auctionItem.setAmount(note.getCurrentAmount());
                    AuctionHouse.getGuiManager().openGUI(new ConfirmBuyGUI(note, c, auctionItem),
                            (Player) event.getWhoClicked());
                });
    }

    private InventoryButton sign() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "set-amount"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionView().getItemName("set-amount"));
            meta.setLore(GuiConfigManager.auctionView().getLore("choose-amount-lore"));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Player p = (Player) event.getWhoClicked();
                    Sounds.click(event);
                    if (note.getPlayerName().equals(p.getName())) {
                        p.sendMessage(M.getFormatted("chat.own-auction"));
                    }
                    InputManager.openSetAmount(p, (player, typedText) -> {
                        try {
                            int amount = Integer.parseInt(typedText);
                            if (amount <= 0 || amount > note.getCurrentAmount())
                                throw new RuntimeException();
                            if (note.getPrice() / note.getItem().getAmount() * amount > VaultHook.getEconomy()
                                    .getBalance(player)) {
                                AuctionHouse.getGuiManager().openGUI(new AuctionViewGUI(note, c, 0, goBackTo), player);
                                player.sendMessage(M.getFormatted("chat.not-enough-money"));
                                Sounds.villagerDeny(event);
                                return;
                            }
                            ItemStack auctionItem = note.getItem();
                            auctionItem.setAmount(amount);
                            AuctionHouse.getGuiManager().openGUI(new ConfirmBuyGUI(note, c, auctionItem), player);
                        } catch (Exception e) {
                            AuctionHouse.getGuiManager().openGUI(new AuctionViewGUI(note, c, 0, goBackTo), player);
                            player.sendMessage(M.getFormatted("chat.invalid-amount"));
                            Sounds.villagerDeny(event);
                        }
                    });
                });
    }

    private InventoryButton bidHistory() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "bid-history"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionView().getItemName("bid-history"));
            List<String> lore = new ArrayList<>();
            for (Bid bid : note.getBidHistoryList()) {
                lore.add(GuiConfigManager.auctionView().getLoreLine("bid-history-entry",
                        "{player}", bid.getPlayerName(),
                        "{amount}", String.format("%.2f", bid.getPrice())));
            }
            if (lore.isEmpty()) {
                lore = GuiConfigManager.auctionView().getLore("bid-history-empty-lore");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                });
    }

    private InventoryButton bidExplanation() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "bid-explanation"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionView().getItemName("bid-explanation"));
            meta.setLore(GuiConfigManager.auctionView().getLore("bid-explanation-lore", "{bid}",
                    String.format("%.2f", bid)));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Sounds.click(event);
                    InputManager.openSetBid(c.getPlayer(), (p, typedText) -> {
                        double amount = StringUtils.parsePositiveNumber(typedText);
                        if (amount <= bid) {
                            p.sendMessage(M.getFormatted("chat.invalid-amount"));
                            Sounds.villagerDeny(event);
                            AuctionHouse.getGuiManager().openGUI(new AuctionViewGUI(note, c, 0, goBackTo), p);
                            return;
                        }
                        if (amount > VaultHook.getEconomy().getBalance(p)) {
                            p.sendMessage(M.getFormatted("chat.not-enough-money"));
                            Sounds.villagerDeny(event);
                            AuctionHouse.getGuiManager().openGUI(new AuctionViewGUI(note, c, 0, goBackTo), p);
                        } else {
                            bid = amount;
                            AuctionHouse.getGuiManager().openGUI(new AuctionViewGUI(note, c, bid, goBackTo), p);
                        }
                    });
                });
    }

    private InventoryButton submitBid() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "submit-bid"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean isOwner = note.getPlayerUUID().equals(c.getPlayer().getUniqueId());
            if (isOwner) {
                meta.setItemName(GuiConfigManager.auctionView().getItemName("own-bid"));
                meta.setLore(
                        GuiConfigManager.auctionView().getLore("own-bid-lore", "{bid}", String.format("%.2f", bid)));
            } else {
                meta.setItemName(GuiConfigManager.auctionView().getItemName("submit-bid"));
                double currentBid = note.getBid(c.getPlayer());
                meta.setLore(GuiConfigManager.auctionView().getLore("submit-bid-lore",
                        "{bid}", String.format("%.2f", bid),
                        "{current}", String.format("%.2f", currentBid)));
            }
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Sounds.click(event);

                    if (note.getPlayerUUID().equals(event.getWhoClicked().getUniqueId())) {
                        event.getWhoClicked().sendMessage(M.getFormatted("chat.own-auction"));
                        return;
                    }
                    if (note.isExpired()) {
                        event.getWhoClicked().sendMessage(M.getFormatted("chat.expired"));
                        return;
                    }
                    AuctionHouse.getGuiManager().openGUI(
                            new ConfirmBidGUI(note, c, bid, goBackTo == UserSession.View.AUCTION_HOUSE),
                            c.getPlayer());
                });
    }

    private InventoryButton cannotAffordBid() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "cannot-afford-bid"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionView().getItemName("cannot-afford-bid"));
            meta.setLore(GuiConfigManager.auctionView().getLore("cannot-afford-bid-lore", "{bid}",
                    String.format("%.2f", bid)));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(Sounds::villagerDeny);
    }

    private InventoryButton topBid() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "top-bid"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionView().getItemName("top-bid"));
            meta.setLore(GuiConfigManager.auctionView().getLore("top-bid-lore",
                    "{price}", String.format("%.2f", note.getPrice()),
                    "{bid}", String.format("%.2f", bid)));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> c.getPlayer().sendMessage(M.getFormatted("chat.already-top-bid")));
    }

    private InventoryButton cancelAuction() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "cancel-auction"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionView().getItemName("cancel-auction"));
            meta.setLore(GuiConfigManager.auctionView().getLore("cancel-auction-lore"));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Player p = (Player) event.getWhoClicked();
                    // check if inventory is full
                    if (p.getInventory().firstEmpty() == -1) {
                        p.sendMessage(M.getFormatted("chat.inventory-full"));
                        Sounds.villagerDeny(event);
                        return;
                    }
                    if (note.hasBidHistory()) {
                        p.sendMessage(M.getFormatted("chat.already-sold3"));
                        Sounds.villagerDeny(event);
                        return;
                    }
                    Sounds.experience(event);
                    Sounds.breakWood(event);
                    p.getInventory().addItem(note.getItem());
                    AuctionManager.getInstance().deleteAuction(note);
                    openGUI(p);
                    p.sendMessage(M.getFormatted("chat.auction-canceled"));
                });
    }

    private void openGUI(Player p) {
        if (goBackTo == UserSession.View.AUCTION_HOUSE)
            AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), p);
        else if (goBackTo == UserSession.View.MY_AUCTIONS)
            AuctionHouse.getGuiManager().openGUI(new MyAuctionsGUI(c), p);
        else if (goBackTo == UserSession.View.MY_BIDS)
            AuctionHouse.getGuiManager().openGUI(new MyBidsGUI(c, 0), p);
    }
}
