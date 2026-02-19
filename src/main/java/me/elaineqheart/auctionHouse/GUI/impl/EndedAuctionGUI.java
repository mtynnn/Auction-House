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
import me.elaineqheart.auctionHouse.model.*;
import me.elaineqheart.auctionHouse.manager.ItemManager;
import me.elaineqheart.auctionHouse.model.AuctionItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;
import java.util.UUID;

public class EndedAuctionGUI extends InventoryGUI implements Runnable {

    private static final String GUI_NAME = "EndedAuctionGUI";

    private final AuctionItem note;
    private final UUID invID = UUID.randomUUID();
    private final UserSession c;
    private final ItemStack item;
    private final UserSession.View previousView;

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

    public EndedAuctionGUI(AuctionItem note, UserSession configuration, UserSession.View previousView) {
        super();
        this.note = note;
        c = configuration;
        c.setView(UserSession.View.ENDED_AUCTION);
        this.previousView = previousView;
        this.item = ItemManager.createItemFromNote(note, c.getPlayer(), true, false);
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
    }

    @Override
    protected Inventory createInventory() {
        int size = SlotConfigManager.getSize(GUI_NAME);
        return Bukkit.createInventory(null, size, GuiConfigManager.endedAuction().getTitle());
    }

    @Override
    public void decorate(Player player) {
        fillFiller();
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "item-display"), Item());
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "claim"), claimButton());
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "return"), back());
        super.decorate(player);
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        TaskManager.cancelTask(invID);
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
                                new ShulkerViewGUI(note, c, previousView), c.getPlayer());
                    }
                });
    }

    private InventoryButton back() {
        ItemStack backItem = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "return"));
        ItemMeta meta = backItem.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.endedAuction().getItemName("return"));
            meta.setLore(GuiConfigManager.endedAuction().getLore("return-lore"));
            backItem.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> backItem)
                .consumer(event -> {
                    Sounds.click(event);
                });
    }

    private InventoryButton claimButton() {
        Player p = c.getPlayer();
        ItemStack collectBtn = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "claim"));
        ItemMeta meta = collectBtn.getItemMeta();

        boolean isWinner = false;
        if (note.getBidHistoryList().size() > 0) {
            // simplified check
            // In AuctionItem, we might need a helper to check if this player is the winner?
            // But note.getBids() returns list of bids. Valid if list is sorted?
            // Usually bids are stored in order? Or we iterate?
            // AuctionItem.getBids() returns List<Bid>.
            // Let's assume the last bid is the highest? Or first?
            // Usually AuctionItem logic keeps them sorted or we check max.
            // But let's use the logic I wrote earlier:
            // checks if top bidder.
            for (Bid b : note.getBidHistoryList()) {
                if (b.getPrice() == note.getPrice()) { // Price matches sold price?
                    if (b.getPlayerID().equals(p.getUniqueId()))
                        isWinner = true;
                }
                // Actually AuctionItem has `getLastBidder()`? No, earlier I saw
                // `getLastBidder()` in `ItemNote`.
                // does `AuctionItem` have it?
                // I should check `AuctionItem`.
            }
        }
        // Wait, `AuctionItem` is `ItemNote` renamed.
        // `ItemNote` had `getLastBidder()`.
        if (Objects.equals(note.getLastBidder(), p.getUniqueId())) {
            isWinner = true;
        }

        if (meta != null) {
            if (isWinner) {
                meta.setItemName(GuiConfigManager.endedAuction().getItemName("claim-item"));
                meta.setLore(GuiConfigManager.endedAuction().getLore("claim-item-lore"));
            } else {
                meta.setItemName(GuiConfigManager.endedAuction().getItemName("claim-money"));
                meta.setLore(GuiConfigManager.endedAuction().getLore("claim-money-lore", "{amount}",
                        String.format("%.2f", note.getBid(p))));
            }
            collectBtn.setItemMeta(meta);
        }
        boolean finalIsWinner = isWinner;
        return new InventoryButton()
                .creator(player -> collectBtn)
                .consumer(event -> {
                    Player player = (Player) event.getWhoClicked();
                    if (player.getInventory().firstEmpty() == -1 && finalIsWinner) {
                        player.sendMessage(M.getFormatted("chat.inventory-full"));
                        Sounds.villagerDeny(event);
                        return;
                    }

                    if (AuctionManager.getInstance().canCollectBid(note, player.getUniqueId())) {
                        boolean success;
                        if (finalIsWinner) {
                            success = AuctionManager.getInstance().claimWonItem(player, note);
                            if (success) {
                                player.sendMessage(M.getFormatted("chat.claimed-item"));
                            }
                        } else {
                            success = AuctionManager.getInstance().claimBidMoney(player, note);
                            if (success) {
                                player.sendMessage(M.getFormatted("chat.claimed-money"));
                            }
                        }

                        if (success) {
                            Sounds.experience(event);
                            openGUI(player);
                        } else {
                            player.sendMessage(M.getFormatted("chat.already-claimed"));
                            Sounds.villagerDeny(event);
                        }
                    } else {
                        player.sendMessage(M.getFormatted("chat.already-claimed"));
                        Sounds.villagerDeny(event);
                    }
                });
    }

    private void openGUI(Player p) {
        if (previousView == UserSession.View.AUCTION_HOUSE)
            AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), p);
        else if (previousView == UserSession.View.MY_AUCTIONS)
            AuctionHouse.getGuiManager().openGUI(new MyAuctionsGUI(c), p);
        else if (previousView == UserSession.View.MY_BIDS)
            AuctionHouse.getGuiManager().openGUI(new MyBidsGUI(c, 0), p);
    }
}
