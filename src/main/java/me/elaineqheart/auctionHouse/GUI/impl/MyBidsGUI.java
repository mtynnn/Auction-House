package me.elaineqheart.auctionHouse.GUI.impl;

import me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.GUI.InventoryButton;
import me.elaineqheart.auctionHouse.GUI.InventoryGUI;
import me.elaineqheart.auctionHouse.GUI.other.Sounds;
import me.elaineqheart.auctionHouse.manager.AuctionManager;
import me.elaineqheart.auctionHouse.TaskManager;
import me.elaineqheart.auctionHouse.configuration.SlotConfigManager;
import me.elaineqheart.auctionHouse.configuration.ConfigManager;
import me.elaineqheart.auctionHouse.model.*;
import me.elaineqheart.auctionHouse.manager.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class MyBidsGUI extends InventoryGUI implements Runnable {

    private static final String GUI_NAME = "MyBidsGUI";

    private UUID invID = UUID.randomUUID();
    private final UserSession c;
    private int noteSize;
    private int screenSize;
    private final int rows;
    private int page;

    @Override
    public void run() {
        decorate(c.getPlayer());
    }

    public MyBidsGUI(UserSession c, int page) {
        super(Bukkit.createInventory(null,
                9 * switch (AuctionManager.getInstance().getMyBids(c.getPlayer().getUniqueId()).size() / 7) {
                    case 0 -> 3;
                    case 1 -> 4;
                    case 2 -> 5;
                    default -> 6;
                }, GuiConfigManager.myBids().getTitle()));
        this.c = c;
        this.c.setView(UserSession.View.MY_BIDS);
        this.page = page;
        rows = getInventory().getSize() / 9;
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
    }

    @Override
    public void decorate(Player player) {
        fillOutPlaces(player);
        super.decorate(player);
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        TaskManager.cancelTask(invID);
    }

    @Override
    protected Inventory createInventory() {
        return null;
    }

    private void update() {
        decorate(c.getPlayer());
        TaskManager.cancelTask(invID);
        invID = UUID.randomUUID();
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
    }

    private void fillOutPlaces(Player player) {
        int size = rows * 9;

        // Calculate item slots dynamically (inner slots, rows 1 to rows-2)
        List<Integer> itemSlots = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col <= 7; col++) {
                itemSlots.add(row * 9 + col);
            }
        }

        List<AuctionItem> bidList = AuctionManager.getInstance().getMyBids(player.getUniqueId());
        createButtonsForAuctionItems(bidList, itemSlots);

        // Fill filler slots
        Set<Integer> reservedSlots = new HashSet<>(itemSlots);
        int backSlot = size - 5; // Center of bottom row
        reservedSlots.add(backSlot);

        ItemStack filler = SlotConfigManager.createFillerItem(GUI_NAME);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setHideTooltip(true);
            filler.setItemMeta(fillerMeta);
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

        // Back button
        this.addButton(backSlot, back());

        // Pagination if needed
        if (bidList.size() > screenSize) {

            // Use dynamic slots for this dynamic GUI
            this.addButton(size - 9, previousPage()); // First slot of last row
            this.addButton(size - 1, nextPage()); // Last slot of last row
        }
    }

    private void createButtonsForAuctionItems(List<AuctionItem> myAuctions, List<Integer> itemSlots) {
        noteSize = myAuctions.size();
        screenSize = itemSlots.size();
        int start = page * screenSize;
        int stop = start + screenSize;
        int end = Math.min(noteSize, stop);
        myAuctions = myAuctions.subList(start, end);
        int size = myAuctions.size();
        for (int i = 0; i < screenSize; ++i) {
            int j = itemSlots.get(i);
            if (size - 1 < i) {
                if (ConfigManager.permissions.getAuctionSlots(c.getPlayer()) <= i)
                    continue;
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
        ItemStack item = ItemManager.createItemFromNote(note, c.getPlayer(), true, false);
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    if (ItemManager.isShulkerBox(item) && event.isRightClick()) {
                        Sounds.openShulker(event);
                        AuctionHouse.getGuiManager()
                                .openGUI(new ShulkerViewGUI(note, c, UserSession.View.MY_AUCTIONS), c.getPlayer());
                        return;
                    }
                    Sounds.click(event);
                    if (note.isExpired()) {
                        AuctionHouse.getGuiManager().openGUI(new EndedAuctionGUI(note, c, UserSession.View.MY_BIDS),
                                c.getPlayer());
                    } else {
                        AuctionHouse.getGuiManager()
                                .openGUI(new AuctionViewGUI(note, c, 0, UserSession.View.MY_BIDS), c.getPlayer());
                    }
                });
    }

    private InventoryButton back() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "back"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.myBids().getItemName("back"));
            meta.setLore(GuiConfigManager.myBids().getLore("back-lore"));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Sounds.click(event);
                    AuctionHouse.getGuiManager().openGUI(new MyAuctionsGUI(c), c.getPlayer());
                });
    }

    private InventoryButton nextPage() {
        int pages = (noteSize - 1) / screenSize;
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "next-page"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.myBids().getItemName("next-page"));
            meta.setLore(GuiConfigManager.myBids().getLore("next-page-lore",
                    "{page}", String.valueOf(page + 1),
                    "{pages}", String.valueOf(pages + 1)));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    if (page == pages)
                        return;
                    if (event.isRightClick())
                        page = pages;
                    else
                        page++;
                    Sounds.click(event);
                    update();
                });
    }

    private InventoryButton previousPage() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "previous-page"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.myBids().getItemName("prev-page"));
            meta.setLore(GuiConfigManager.myBids().getLore("prev-page-lore",
                    "{page}", String.valueOf(page + 1),
                    "{pages}", String.valueOf((noteSize - 1) / screenSize + 1)));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    if (page == 0)
                        return;
                    if (event.isRightClick())
                        page = 0;
                    else
                        page--;
                    Sounds.click(event);
                    update();
                });
    }
}
