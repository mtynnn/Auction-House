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
import me.elaineqheart.auctionHouse.model.*;
import me.elaineqheart.auctionHouse.manager.ItemManager;
import me.elaineqheart.auctionHouse.model.AuctionItem;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class CollectSoldItemGUI extends InventoryGUI implements Runnable {

    private static final String GUI_NAME = "CollectSoldItemGUI";

    private final AuctionItem note;
    private final UUID invID = UUID.randomUUID();
    private final UserSession c;
    private final ItemStack item;
    private final boolean goBackToAuctionHouse;

    @Override
    public void run() {
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "item-display"), Item());
        super.decorate(c.getPlayer());
    }

    public CollectSoldItemGUI(AuctionItem note, UserSession configuration) {
        super();
        this.note = note;
        c = configuration;
        goBackToAuctionHouse = c.getView() == UserSession.View.AUCTION_HOUSE;
        c.setView(UserSession.View.COLLECT_SOLD_ITEM);
        // This is a SOLD item, so we show the collecting item (gold blocks/ingots
        // usually, representing money)
        // OR the item itself?
        // original was ItemManager.createCollectingItemFromNote(note, c.getPlayer())
        // But wait, in the view file it was `createItemFromNote`?
        // Let's check the viewed file...
        // Line 48: this.item = ItemManager.createCollectingItemFromNote(note,
        // c.getPlayer());
        this.item = ItemManager.createCollectingItemFromNote(note, c.getPlayer());
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
    }

    @Override
    protected Inventory createInventory() {
        int size = SlotConfigManager.getSize(GUI_NAME);
        return Bukkit.createInventory(null, size, GuiConfigManager.collectSold().getTitle());
    }

    @Override
    public void decorate(Player player) {
        fillFiller();
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "item-display"), Item());
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "confirm"), collectItem());
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "return"), back());
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

    private InventoryButton Item() {
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    if (ItemManager.isShulkerBox(item) && event.isRightClick()) {
                        Sounds.openShulker(event);
                        AuctionHouse.getGuiManager().openGUI(
                                new ShulkerViewGUI(note, c, UserSession.View.MY_AUCTIONS), c.getPlayer());
                    }
                });
    }

    private InventoryButton back() {
        ItemStack backItem = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "return"));
        ItemMeta meta = backItem.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.collectSold().getItemName("return"));
            meta.setLore(GuiConfigManager.collectSold().getLore("return-lore"));
            backItem.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> backItem)
                .consumer(event -> {
                    Player p = (Player) event.getWhoClicked();
                    Sounds.click(event);
                    if (goBackToAuctionHouse)
                        AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), p);
                    else
                        AuctionHouse.getGuiManager().openGUI(new MyAuctionsGUI(c), p);
                });
    }

    private InventoryButton collectItem() {
        ItemStack confirmItem = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "confirm"));
        ItemMeta meta = confirmItem.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.collectSold().getItemName("confirm"));
            meta.setLore(GuiConfigManager.collectSold().getLore("confirm-lore"));
            confirmItem.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> confirmItem)
                .consumer(event -> {
                    Player p = (Player) event.getWhoClicked();
                    // We need to claim the money.

                    if (note.isBIDAuction() && !note.isSold()) {
                        return; // Sanity check
                    }

                    boolean success = AuctionManager.getInstance().claimSoldItemMoney(p, note);

                    if (success) {
                        Sounds.experience(event);
                        p.sendMessage(M.getFormatted("chat.claimed-money"));
                        if (goBackToAuctionHouse)
                            AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), p);
                        else
                            AuctionHouse.getGuiManager().openGUI(new MyAuctionsGUI(c), p);
                    } else {
                        Sounds.villagerDeny(event);
                    }
                });
    }

    private static double getProfit(double price) {
        return price; // No tax applied
    }
}
