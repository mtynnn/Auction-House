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
import me.elaineqheart.auctionHouse.vault.VaultHook;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;

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
        price = note.getPrice() / note.getItem().getAmount() * item.getAmount();
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

                    AuctionItem test = AuctionManager.getInstance().getAuction(note.getNoteID());
                    if (test == null) {
                        p.sendMessage(M.getFormatted("chat.non-existent2"));
                        Sounds.villagerDeny(event);
                        return;
                    }
                    if (!test.isOnAuction() || test.getCurrentAmount() < item.getAmount()) {
                        p.sendMessage(M.getFormatted("chat.already-sold2"));
                        Sounds.villagerDeny(event);
                        return;
                    }
                    Economy eco = VaultHook.getEconomy();
                    Bukkit.getScheduler().runTask(AuctionHouse.getPlugin(), p::closeInventory);
                    if (eco.getBalance(p) < price) {
                        p.sendMessage(M.getFormatted("chat.not-enough-money"));
                        Sounds.villagerDeny(event);
                        return;
                    }
                    eco.withdrawPlayer(p, price);
                    Sounds.experience(event);
                    p.getInventory().addItem(item);
                    note.setSold(true);
                    note.setBuyerName(p.getDisplayName());
                    if (price != note.getPrice()) {
                        if (note.getPartiallySoldAmountLeft() == 0) {
                            note.setPartiallySoldAmountLeft(note.getItem().getAmount() - item.getAmount());
                        } else {
                            note.setPartiallySoldAmountLeft(note.getPartiallySoldAmountLeft() - item.getAmount());
                        }
                    }
                    AuctionManager.getInstance().updateAuction(note);
                    p.sendMessage(M.getFormatted("chat.purchase-auction",
                            "%player%", StringUtils.escapeMiniMessage(note.getPlayerName()),
                            "%item%", note.getItemName()));
                    Player seller = Bukkit.getPlayer(note.getPlayerUUID());
                    if (SettingManager.soldMessageEnabled && seller != null
                            && seller.isOnline()) {
                        if (SettingManager.autoCollect) {
                            seller.sendMessage(M.getFormatted("chat.sold-message.auto-collect", price,
                                    "%player%", StringUtils.escapeMiniMessage(p.getDisplayName()),
                                    "%item%", itemName,
                                    "%amount%", String.valueOf(item.getAmount())));
                        } else {
                            // ...
                        }
                    }
                    if (SettingManager.autoCollect
                            && Bukkit.getOnlinePlayers().contains(Bukkit.getPlayer(note.getPlayerUUID()))) {
                        Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getPlugin(),
                                () -> AuctionManager.getInstance().claimSoldItemMoney(
                                        Bukkit.getOfflinePlayer(note.getPlayerUUID()),
                                        note));
                    }
                    ConfigManager.transactionLogger.logTransaction(
                            p.getUniqueId(),
                            note.getPlayerUUID(),
                            item.getType().name(),
                            price,
                            item.getAmount(),
                            !note.isBIDAuction());
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
