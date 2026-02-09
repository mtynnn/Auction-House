package me.elaineqheart.auctionHouse.GUI.impl;

import me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.GUI.InventoryButton;
import me.elaineqheart.auctionHouse.GUI.InventoryGUI;
import me.elaineqheart.auctionHouse.GUI.other.Sounds;
import me.elaineqheart.auctionHouse.manager.AuctionManager;
import me.elaineqheart.auctionHouse.configuration.M;
import me.elaineqheart.auctionHouse.configuration.SlotConfigManager;
import me.elaineqheart.auctionHouse.model.UserSession;
import me.elaineqheart.auctionHouse.manager.ItemManager;
import me.elaineqheart.auctionHouse.model.AuctionItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AdminConfirmGUI extends InventoryGUI {

    private static final String GUI_NAME = "AdminConfirmGUI";

    private final AuctionItem note;
    private final UserSession c;
    private final UserSession.AdminAction action;
    private final String reason;
    private final ItemStack item;

    public AdminConfirmGUI(AuctionItem note, UserSession configuration, UserSession.AdminAction action, String reason) {
        super();
        this.note = note;
        c = configuration;
        this.action = action;
        this.reason = reason;
        if (this.action == UserSession.AdminAction.DELETE_ITEM)
            this.item = ItemManager.createAdminDeleteItem(note, reason);
        else
            this.item = ItemManager.createAdminExpireItem(note, reason);
        c.setView(UserSession.View.ADMIN_CONFIRM);
    }

    @Override
    protected Inventory createInventory() {
        int size = SlotConfigManager.getSize(GUI_NAME);
        return Bukkit.createInventory(null, size, GuiConfigManager.adminConfirm().getTitle());
    }

    @Override
    public void decorate(Player player) {
        fillFiller();
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "item-display"), Item());
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "confirm"), confirm());
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

    private InventoryButton confirmExpireItem() {
        ItemStack confirmItem = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "confirm"));
        ItemMeta meta = confirmItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(GuiConfigManager.adminConfirm().getItemName("confirm"));
            meta.setLore(GuiConfigManager.adminConfirm().getLore("confirm-expire-lore"));
            confirmItem.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> confirmItem)
                .consumer(event -> {
                    Player p = (Player) event.getWhoClicked();

                    if (!note.isOnAuction()) {
                        p.sendMessage(M.getFormatted("chat.already-sold"));
                        Sounds.villagerDeny(event);
                        return;
                    }

                    Sounds.experience(event);
                    Sounds.breakWood(event);
                    p.closeInventory();

                    AuctionManager.getInstance().expireAuction(note, reason);

                    p.sendMessage(M.getFormatted("chat.admin-expire-auction", "%reason%", reason));
                });
    }

    private InventoryButton confirmDeleteItem() {
        ItemStack confirmItem = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "confirm"));
        ItemMeta meta = confirmItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(GuiConfigManager.adminConfirm().getItemName("confirm"));
            meta.setLore(GuiConfigManager.adminConfirm().getLore("confirm-delete-lore"));
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

                    if (!note.isOnAuction()) {
                        p.sendMessage(M.getFormatted("chat.already-sold"));
                        Sounds.villagerDeny(event);
                        return;
                    }

                    p.closeInventory();
                    p.getInventory().addItem(note.getItem());
                    Sounds.experience(event);
                    Sounds.breakWood(event);

                    AuctionManager.getInstance().deleteAuction(note);

                    // Send message to seller if online? AuctionManager might handle it or we do it
                    // here.
                    // The original code sent a message to seller.
                    Player seller = Bukkit.getPlayer(note.getPlayerUUID());
                    if (seller != null) {
                        seller.sendMessage(M.getFormatted("items.admin-delete-item.player-message",
                                "%item%", note.getItemName(),
                                "%reason%", reason));
                    }

                    p.sendMessage(M.getFormatted("chat.admin-delete-auction", "%reason%", reason));
                });
    }

    private InventoryButton confirm() {
        if (this.action == UserSession.AdminAction.DELETE_ITEM) {
            return confirmDeleteItem();
        } else {
            return confirmExpireItem();
        }
    }

    private InventoryButton Item() {
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                });
    }

    private InventoryButton back() {
        ItemStack backItem = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "cancel"));
        ItemMeta meta = backItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(GuiConfigManager.adminConfirm().getItemName("cancel"));
            meta.setLore(GuiConfigManager.adminConfirm().getLore("cancel-lore"));
            backItem.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> backItem)
                .consumer(event -> {
                    Sounds.click(event);
                    AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), c.getPlayer());
                });
    }
}
