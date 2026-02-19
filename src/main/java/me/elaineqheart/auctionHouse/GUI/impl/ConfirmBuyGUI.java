package me.elaineqheart.auctionHouse.GUI.impl;

import me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.GUI.InventoryButton;
import me.elaineqheart.auctionHouse.GUI.InventoryGUI;
import me.elaineqheart.auctionHouse.GUI.other.Sounds;
import me.elaineqheart.auctionHouse.manager.AuctionManager;
import me.elaineqheart.auctionHouse.configuration.SettingManager;
import me.elaineqheart.auctionHouse.configuration.M;
import me.elaineqheart.auctionHouse.configuration.SlotConfigManager;
import me.elaineqheart.auctionHouse.configuration.ConfigManager;
import me.elaineqheart.auctionHouse.model.UserSession;
import me.elaineqheart.auctionHouse.manager.ItemManager;
import me.elaineqheart.auctionHouse.model.AuctionItem;
import me.elaineqheart.auctionHouse.util.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ConfirmBuyGUI extends InventoryGUI {

    private static final String GUI_NAME = "ConfirmBuyGUI";

    private final AuctionItem note;
    private final ItemStack item;
    private final UserSession c;
    private final double price;

    public ConfirmBuyGUI(AuctionItem note, UserSession configuration, ItemStack item) {
        super();
        this.note = note;
        this.item = item;
        c = configuration;
        ItemStack noteItem = note.getItem();
        int baseAmount = (noteItem == null || noteItem.getAmount() <= 0) ? Math.max(1, item.getAmount()) : noteItem.getAmount();
        price = note.getPrice() / baseAmount * item.getAmount();
    }

    @Override
    protected Inventory createInventory() {
        int size = SlotConfigManager.getSize(GUI_NAME);
        return Bukkit.createInventory(null, size, GuiConfigManager.confirmBuy().getTitle());
    }

    @Override
    public void decorate(Player player) {
        fillFiller();
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "confirm"), confirm());
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "item-display"), buyingItem());
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "cancel"), cancel());
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
                    .creator(p -> filler)
                    .consumer(event -> {
                    }));
        }
    }

    private InventoryButton buyingItem() {
        return new InventoryButton()
                .creator(player -> ItemManager.createBuyingItemDisplay(item.clone()))
                .consumer(event -> {
                });
    }

    private InventoryButton confirm() {
        ItemStack confirmItem = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "confirm"));
        ItemMeta meta = confirmItem.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.confirmBuy().getItemName("confirm"));
            // ConfirmBuyGUI.yml likely has {price} placeholder
            meta.setLore(
                    GuiConfigManager.confirmBuy().getLore("confirm-lore", "{price}", String.format("%.2f", price)));
            confirmItem.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> confirmItem)
                .consumer(event -> {
                    Player p = (Player) event.getWhoClicked();
                    // check if inventory is full
                    if (p.getInventory().firstEmpty() == -1) {
                        p.sendMessage(M.getFormatted("chat.inventory-full"));
                        Sounds.villagerDeny(event);
                        return;
                    }
                    String itemName = note.getItemName();

                    AuctionManager.PurchaseResult result = AuctionManager.getInstance()
                            .purchaseBin(p, note, item.getAmount());
                    AuctionItem liveNote = result.getNote();

                    switch (result.getStatus()) {
                        case NOT_FOUND -> {
                            p.sendMessage(M.getFormatted("chat.non-existent2"));
                            Sounds.villagerDeny(event);
                            return;
                        }
                        case OWN_AUCTION -> {
                            p.sendMessage(M.getFormatted("chat.own-auction"));
                            Sounds.villagerDeny(event);
                            return;
                        }
                        case INSUFFICIENT_FUNDS -> {
                            p.sendMessage(M.getFormatted("chat.not-enough-money"));
                            Sounds.villagerDeny(event);
                            return;
                        }
                        case NOT_AVAILABLE -> {
                            p.sendMessage(M.getFormatted("chat.already-sold2"));
                            Sounds.villagerDeny(event);
                            return;
                        }
                        case CORRUPTED_ITEM, INVALID_AMOUNT -> {
                            p.sendMessage(M.getFormatted("chat.error-occurred"));
                            Sounds.villagerDeny(event);
                            return;
                        }
                        case SUCCESS -> {
                        }
                    }

                    Sounds.experience(event);
                    ItemStack tracedItem = ItemManager.stampAuctionPurchase(result.getBoughtItem(), liveNote, p);
                    p.getInventory().addItem(tracedItem);
                    p.sendMessage(M.getFormatted("chat.purchase-auction",
                            "%player%", StringUtils.escapeMiniMessage(liveNote.getPlayerName()),
                            "%item%", liveNote.getItemName(),
                            "%price%", StringUtils.formatPrice(result.getPricePaid())));
                    Player seller = Bukkit.getPlayer(liveNote.getPlayerUUID());
                    if (SettingManager.soldMessageEnabled && seller != null
                            && seller.isOnline()) {
                        if (SettingManager.autoCollect) {
                            seller.sendMessage(M.getFormatted("chat.sold-message.auto-collect", result.getPricePaid(),
                                    "%player%", StringUtils.escapeMiniMessage(p.getDisplayName()),
                                    "%item%", itemName,
                                    "%amount%", String.valueOf(item.getAmount())));
                        } else {
                            // ...
                        }
                    }
                    if (SettingManager.autoCollect
                            && Bukkit.getOnlinePlayers().contains(Bukkit.getPlayer(liveNote.getPlayerUUID()))) {
                        AuctionManager.getInstance().claimSoldItemMoney(
                                Bukkit.getOfflinePlayer(liveNote.getPlayerUUID()),
                                liveNote);
                    }
                    ConfigManager.transactionLogger.logTransaction(
                            p.getUniqueId(),
                            liveNote.getPlayerUUID(),
                            item.getType().name(),
                            result.getPricePaid(),
                            item.getAmount(),
                            !liveNote.isBIDAuction());

                    // Keep AH flow continuous after buying.
                    Bukkit.getScheduler().runTask(AuctionHouse.getPlugin(),
                            () -> AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), p));
                });
    }

    private InventoryButton cancel() {
        ItemStack cancelItem = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "cancel"));
        ItemMeta meta = cancelItem.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.confirmBuy().getItemName("cancel"));
            meta.setLore(GuiConfigManager.confirmBuy().getLore("cancel-lore"));
            cancelItem.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> cancelItem)
                .consumer(event -> {
                    Player p = (Player) event.getWhoClicked();
                    Sounds.click(event);
                    AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), p);
                });
    }
}
