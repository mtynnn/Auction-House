package me.elaineqheart.auctionHouse.GUI.impl;

import me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.GUI.InventoryButton;
import me.elaineqheart.auctionHouse.GUI.InventoryGUI;
import me.elaineqheart.auctionHouse.GUI.other.InputManager;
import me.elaineqheart.auctionHouse.GUI.other.Sounds;
import me.elaineqheart.auctionHouse.TaskManager;
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

/**
 * Simple admin action GUI with two options:
 * - Expire: Remove from auction and return to seller
 * - Delete: Remove from auction without returning (item is destroyed)
 */
public class AdminActionGUI extends InventoryGUI implements Runnable {

    private static final String GUI_NAME = "AdminActionGUI";

    private final AuctionItem note;
    private UUID invID = UUID.randomUUID();
    private final UserSession c;
    private final ItemStack item;

    @Override
    public void run() {
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "item-display"), Item());
        super.decorate(c.getPlayer());
    }

    public AdminActionGUI(AuctionItem note, UserSession configuration) {
        super();
        this.note = note;
        c = configuration;
        c.setView(UserSession.View.ADMIN_ACTION); // Assuming ADMIN_ACTION exists in UserSession.View, if not I will add
                                                  // it or use a default
        this.item = ItemManager.createItemFromNote(note, c.getPlayer(), true, true);
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
    }

    @Override
    protected Inventory createInventory() {
        int size = SlotConfigManager.getSize(GUI_NAME);
        return Bukkit.createInventory(null, size, GuiConfigManager.adminAction().getTitle());
    }

    @Override
    public void decorate(Player player) {
        fillFiller();
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "item-display"), Item());
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "expire"), forceReturn());
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "delete"), forceRemove());
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
            meta.setItemName(GuiConfigManager.adminAction().getItemName("cancel"));
            meta.setLore(GuiConfigManager.adminAction().getLore("cancel-lore"));
            backItem.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> backItem)
                .consumer(event -> {
                    Player p = (Player) event.getWhoClicked();
                    Sounds.click(event);
                    AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), p);
                });
    }

    private InventoryButton forceRemove() {
        ItemStack confirmItem = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "delete"));
        ItemMeta meta = confirmItem.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.adminAction().getItemName("delete"));
            meta.setLore(GuiConfigManager.adminAction().getLore("delete-lore"));
            confirmItem.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> confirmItem)
                .consumer(event -> {
                    Player p = (Player) event.getWhoClicked();
                    Sounds.click(event);
                    UserSession.AdminAction action = UserSession.AdminAction.DELETE_ITEM;
                    InputManager.openAdminReason(p, true, (player, typedText) -> {
                        AuctionHouse.getGuiManager().openGUI(
                                new AdminConfirmGUI(note, c, action, typedText),
                                player);
                    });
                });
    }

    private InventoryButton forceReturn() {
        ItemStack confirmItem = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "expire"));
        ItemMeta meta = confirmItem.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.adminAction().getItemName("expire"));
            meta.setLore(GuiConfigManager.adminAction().getLore("expire-lore"));
            confirmItem.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> confirmItem)
                .consumer(event -> {
                    Player p = (Player) event.getWhoClicked();
                    Sounds.click(event);
                    UserSession.AdminAction action = UserSession.AdminAction.RETURN_ITEM;
                    InputManager.openAdminReason(p, false, (player, typedText) -> {
                        AuctionHouse.getGuiManager().openGUI(
                                new AdminConfirmGUI(note, c, action, typedText),
                                player);
                    });
                });
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        TaskManager.cancelTask(invID);
    }
}
