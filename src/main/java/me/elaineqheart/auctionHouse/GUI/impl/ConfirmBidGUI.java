package me.elaineqheart.auctionHouse.GUI.impl;

import me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.GUI.InventoryButton;
import me.elaineqheart.auctionHouse.GUI.InventoryGUI;
import me.elaineqheart.auctionHouse.GUI.other.Sounds;
import me.elaineqheart.auctionHouse.manager.AuctionManager;
import me.elaineqheart.auctionHouse.configuration.M;
import me.elaineqheart.auctionHouse.configuration.SlotConfigManager;
import me.elaineqheart.auctionHouse.model.*;
import me.elaineqheart.auctionHouse.manager.ItemManager;
import me.elaineqheart.auctionHouse.util.StringUtils;
import me.elaineqheart.auctionHouse.vault.VaultHook;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Set;
import java.util.UUID;

public class ConfirmBidGUI extends InventoryGUI {

    private static final String GUI_NAME = "ConfirmBidGUI";

    private final AuctionItem note;
    private final UserSession c;
    private final double price;
    private final boolean goBackToAuctionHouse;

    public ConfirmBidGUI(AuctionItem note, UserSession configuration, double bid, boolean goBackToAuctionHouse) {
        super();
        this.note = note;
        c = configuration;
        price = bid;
        this.goBackToAuctionHouse = goBackToAuctionHouse;
    }

    @Override
    protected Inventory createInventory() {
        int size = SlotConfigManager.getSize(GUI_NAME);
        return Bukkit.createInventory(null, size, GuiConfigManager.confirmBid().getTitle());
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
                .creator(player -> ItemManager.createBuyingItemDisplay(note.getItem()))
                .consumer(event -> {
                });
    }

    private InventoryButton confirm() {
        ItemStack confirmItem = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "confirm"));
        ItemMeta meta = confirmItem.getItemMeta();
        if (meta != null) {
            double increase = price - note.getBid(c.getPlayer());
            meta.setItemName(GuiConfigManager.confirmBid().getItemName("confirm"));
            meta.setLore(
                    GuiConfigManager.confirmBid().getLore("confirm-lore", "{price}", String.format("%.2f", increase)));
            confirmItem.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> confirmItem)
                .consumer(event -> {
                    Player p = (Player) event.getWhoClicked();

                    AuctionItem test = AuctionManager.getInstance().getAuction(note.getNoteID());
                    if (test == null) {
                        p.sendMessage(M.getFormatted("chat.non-existent2"));
                        Sounds.villagerDeny(event);
                        return;
                    }
                    if (!test.isOnAuction()) {
                        p.sendMessage(M.getFormatted("chat.already-sold2"));
                        Sounds.villagerDeny(event);
                        return;
                    }
                    if (test.isExpired()) {
                        event.getWhoClicked().sendMessage(M.getFormatted("chat.expired"));
                        return;
                    }
                    if ((note.hasBidHistory() ? Bid.nextMinBid(note.getPrice()) : note.getPrice()) > price) {
                        p.sendMessage(M.getFormatted("chat.already-sold3"));
                        Sounds.villagerDeny(event);
                        return;
                    }
                    double increase = price - note.getBid(p);
                    Economy eco = VaultHook.getEconomy();
                    if (eco.getBalance(p) < increase) {
                        p.sendMessage(M.getFormatted("chat.not-enough-money"));
                        Sounds.villagerDeny(event);
                        return;
                    }
                    eco.withdrawPlayer(p, increase);
                    Sounds.experience(event);
                    AuctionManager.getInstance().addBid(note, p, price);
                    p.sendMessage(M.getFormatted("chat.placed-bid",
                            "%price%", String.format("%.2f", price),
                            "%item%", note.getItemName()));
                    if (c.shouldKeepOpen())
                        AuctionHouse.getGuiManager().openGUI(
                                new AuctionViewGUI(note, c, 0, goBackToAuctionHouse ? UserSession.View.AUCTION_HOUSE
                                        : UserSession.View.MY_AUCTIONS),
                                p);
                    else
                        Bukkit.getScheduler().runTask(AuctionHouse.getPlugin(), p::closeInventory);

                    Set<UUID> bidders = note.getBidders();
                    bidders.remove(p.getUniqueId());
                    for (UUID id : bidders) {
                        Player bidder = Bukkit.getPlayer(id);
                        if (bidder == null)
                            continue;
                        double difference = price - note.getBid(bidder);
                        bidder.sendMessage(M.getFormatted("chat.outbid.prefix",
                                "%price%", String.format("%.2f", difference),
                                "%player%", StringUtils.escapeMiniMessage(p.getDisplayName()),
                                "%item%", note.getItemName()));
                        TextComponent click = new TextComponent(M.getFormatted("chat.outbid.interaction"));
                        click.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/ah view " + note.getNoteID().toString()));
                        bidder.spigot().sendMessage(click);
                        if (AuctionViewGUI.currentGUIs.get(bidder) == null)
                            continue;
                        AuctionViewGUI.currentGUIs.get(bidder).update();
                    }
                    Player itemOwner = Bukkit.getPlayer(note.getPlayerUUID());
                    if (itemOwner != null && AuctionViewGUI.currentGUIs.get(itemOwner) != null)
                        AuctionViewGUI.currentGUIs.get(itemOwner).update();
                });
    }

    private InventoryButton cancel() {
        ItemStack cancelItem = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "cancel"));
        ItemMeta meta = cancelItem.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.confirmBid().getItemName("cancel"));
            meta.setLore(GuiConfigManager.confirmBid().getLore("cancel-lore"));
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
