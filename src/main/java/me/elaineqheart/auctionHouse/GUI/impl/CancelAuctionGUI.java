package me.elaineqheart.auctionHouse.GUI.impl;

import me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.GUI.InventoryButton;
import me.elaineqheart.auctionHouse.GUI.InventoryGUI;
import me.elaineqheart.auctionHouse.GUI.other.Sounds;
import me.elaineqheart.auctionHouse.TaskManager;
import me.elaineqheart.auctionHouse.manager.AuctionManager;
import me.elaineqheart.auctionHouse.configuration.M;
import me.elaineqheart.auctionHouse.configuration.SlotConfigManager;
import me.elaineqheart.auctionHouse.model.UserSession;
import me.elaineqheart.auctionHouse.manager.ItemManager;
import me.elaineqheart.auctionHouse.model.AuctionItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class CancelAuctionGUI extends InventoryGUI implements Runnable {

    private static final String GUI_NAME = "CancelAuctionGUI";

    private final AuctionItem note;
    private final UUID invID = UUID.randomUUID();
    private final UserSession c;
    private final ItemStack item;
    private final boolean goBackToAuctionHouse;

    @Override
    public void run() {
        Player player = c.getPlayer();
        if (player == null || !player.isOnline()) {
            TaskManager.cancelTask(invID);
            return;
        }
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "item-display"), Item());
        super.decorate(player);
    }

    public CancelAuctionGUI(AuctionItem note, UserSession configuration) {
        super();
        this.note = note;
        c = configuration;
        goBackToAuctionHouse = c.getView() == UserSession.View.AUCTION_HOUSE;
        c.setView(UserSession.View.CANCEL_AUCTION);
        this.item = ItemManager.createItemFromNote(note, c.getPlayer(), true, false);
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
    }

    @Override
    protected Inventory createInventory() {
        int size = SlotConfigManager.getSize(GUI_NAME);
        return Bukkit.createInventory(null, size, GuiConfigManager.cancelAuction().getTitle());
    }

    @Override
    public void decorate(Player player) {
        fillFiller();
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "item-display"), Item());
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "confirm"), collectItem());
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "cancel"), back());
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
                                new ShulkerViewGUI(note, c, UserSession.View.AUCTION_HOUSE), c.getPlayer());
                    }
                });
    }

    private InventoryButton back() {
        ItemStack backItem = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "cancel"));
        ItemMeta meta = backItem.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.cancelAuction().getItemName("cancel"));
            meta.setLore(GuiConfigManager.cancelAuction().getLore("cancel-lore"));
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
            meta.setItemName(GuiConfigManager.cancelAuction().getItemName("confirm"));
            meta.setLore(GuiConfigManager.cancelAuction().getLore("confirm-lore"));
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
                    boolean success = AuctionManager.getInstance().cancelAuctionAndReturnItem(p, note);
                    if (!success) {
                        p.sendMessage(M.getFormatted("chat.already-sold2"));
                        Sounds.villagerDeny(event);
                        return;
                    }
                    Sounds.experience(event);
                    Sounds.breakWood(event);
                    if (goBackToAuctionHouse)
                        AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), p);
                    else
                        AuctionHouse.getGuiManager().openGUI(new MyAuctionsGUI(c), p);
                    p.sendMessage(M.getFormatted("chat.auction-canceled"));
                });
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        TaskManager.cancelTask(invID);
    }
}
