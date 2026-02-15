package me.elaineqheart.auctionHouse.GUI.impl;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.GUI.InventoryButton;
import me.elaineqheart.auctionHouse.GUI.InventoryGUI;
import me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager;

import me.elaineqheart.auctionHouse.GUI.other.InputManager;
import me.elaineqheart.auctionHouse.GUI.other.Sounds;
import me.elaineqheart.auctionHouse.TaskManager;
import me.elaineqheart.auctionHouse.manager.AuctionManager;
import me.elaineqheart.auctionHouse.configuration.SettingManager;
import me.elaineqheart.auctionHouse.configuration.SlotConfigManager;
import me.elaineqheart.auctionHouse.model.UserSession;
import me.elaineqheart.auctionHouse.manager.ItemManager;
import me.elaineqheart.auctionHouse.model.AuctionItem;
import me.elaineqheart.auctionHouse.util.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class AuctionHouseGUI extends InventoryGUI implements Runnable {

    private static final String GUI_NAME = "AuctionHouseGUI";

    public final UserSession c;
    private UUID invID = UUID.randomUUID();
    private int noteSize;
    private int screenSize;

    @Override
    public void run() {
        decorate(c.getPlayer());
    }

    public enum Sort {
        RECENTLY_POSTED,
        HIGHEST_PRICE,
        LOWEST_PRICE,
        ENDING_SOON,
        ALPHABETICAL
    }

    public AuctionHouseGUI(int page, Sort sort, String search, Player p, boolean isAdmin) {
        super();
        this.c = new UserSession(page, sort, search, p, isAdmin);
        c.setView(UserSession.View.AUCTION_HOUSE);
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
    }

    public AuctionHouseGUI(Player p) {
        super();
        this.c = UserSession.getInstance(p).setPlayer(p.getUniqueId());
        c.setView(UserSession.View.AUCTION_HOUSE);
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
    }

    public AuctionHouseGUI(UserSession configuration) {
        super();
        this.c = configuration;
        c.setView(UserSession.View.AUCTION_HOUSE);
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
    }

    @Override
    protected Inventory createInventory() {
        int size = SlotConfigManager.getSize(GUI_NAME);
        int pages = 0;
        if (screenSize > 0) {
            pages = (noteSize - 1) / screenSize;
        }
        if (pages < 0)
            pages = 0; // handle empty list

        return Bukkit.createInventory(null, size, GuiConfigManager.auctionHouse().getTitle());
    }

    @Override
    public void decorate(Player player) {
        // Fill with filler items
        fillFiller();

        // Place auction items
        List<Integer> itemSlots = SlotConfigManager.getItemSlots(GUI_NAME);
        fillOutItems(c.getCurrentSort(), itemSlots);

        // Place control buttons
        placeControlButtons();

        super.decorate(player);
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        TaskManager.cancelTask(invID);

        // Reset filters when the player actually closes the Auction House (not when navigating to another AH GUI)
        if (event.getPlayer() instanceof Player p) {
            Bukkit.getScheduler().runTaskLater(AuctionHouse.getPlugin(), () -> {
                if (!p.isOnline()) {
                    return;
                }
                Inventory top = p.getOpenInventory().getTopInventory();
                if (AuctionHouse.getGuiManager().isHandledInventory(top)) {
                    return; // switched to another plugin GUI (back/view/etc.)
                }

                c.setCurrentSearch("");
                c.setBinFilter(UserSession.BINFilter.ALL);
                c.setWhitelist(null, null);
                c.setCurrentPage(0);
            }, 1L);
        }
    }

    private void update() {
        TaskManager.cancelTask(invID);
        Bukkit.getScheduler().runTask(AuctionHouse.getPlugin(), () -> decorate(c.getPlayer()));
        invID = UUID.randomUUID();
        TaskManager.addTaskID(invID,
                Bukkit.getScheduler().runTaskTimer(AuctionHouse.getPlugin(), this, 20, 20).getTaskId());
    }

    private void fillFiller() {
        int size = SlotConfigManager.getSize(GUI_NAME);
        List<Integer> itemSlots = SlotConfigManager.getItemSlots(GUI_NAME);
        Set<Integer> reservedSlots = new HashSet<>(itemSlots);

        // Add control button slots
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "search"));
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "sort"));
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "previous-page"));
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "refresh"));
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "next-page"));
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "filter"));
        reservedSlots.add(SlotConfigManager.getSlot(GUI_NAME, "my-auctions"));

        ItemStack filler = SlotConfigManager.createFillerItem(GUI_NAME);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < size; i++) {
            if (!reservedSlots.contains(i)) {
                int slot = i;
                this.addButton(slot, new InventoryButton()
                        .creator(player -> filler)
                        .consumer(event -> {
                        }));
            }
        }
    }

    private void placeControlButtons() {
        // Search
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "search"), searchOption());

        // Sort
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "sort"), sortButton());

        // Pagination
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "previous-page"), previousPage());
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "refresh"), refresh());
        this.addButton(SlotConfigManager.getSlot(GUI_NAME, "next-page"), nextPage());

        // Filter (only if both auction types enabled)
        int filterSlot = SlotConfigManager.getSlot(GUI_NAME, "filter");
        if (SettingManager.BINAuctions && SettingManager.BIDAuctions) {
            this.addButton(filterSlot, BINFilter());
        } else {
            this.addButton(filterSlot, fillerButton());
        }

        // My Auctions
        int myAuctionsSlot = SlotConfigManager.getSlot(GUI_NAME, "my-auctions");
        this.addButton(myAuctionsSlot, myAuctions());
    }

    private void fillOutItems(Sort sort, List<Integer> itemSlots) {
        switch (sort) {
            case RECENTLY_POSTED -> createButtonsForAuctionItems(AuctionManager.SortMode.RECENTLY_POSTED, itemSlots);
            case HIGHEST_PRICE -> createButtonsForAuctionItems(AuctionManager.SortMode.PRICE_DESC, itemSlots);
            case LOWEST_PRICE -> createButtonsForAuctionItems(AuctionManager.SortMode.PRICE_ASC, itemSlots);
            case ENDING_SOON -> createButtonsForAuctionItems(AuctionManager.SortMode.DATE, itemSlots);
            case ALPHABETICAL -> createButtonsForAuctionItems(AuctionManager.SortMode.NAME, itemSlots);
        }
    }

    private void createButtonsForAuctionItems(AuctionManager.SortMode mode, List<Integer> itemSlots) {
        List<AuctionItem> auctions = AuctionManager.getInstance().getSortedList(mode, c);
        if (c.getWhitelist() != null)
            AuctionManager.getInstance().applyWhitelist(auctions, c.getWhitelist());

        noteSize = auctions.size();
        screenSize = itemSlots.size();

        if (noteSize == 0 && hasActiveFilters()) {
            // Show a helpful "empty state" button so players can recover easily
            int firstSlot = itemSlots.isEmpty() ? -1 : itemSlots.get(0);
            if (firstSlot != -1) {
                this.addButton(firstSlot, noResultsButton());
            }
        }

        int start = c.getCurrentPage() * screenSize;
        int stop = start + screenSize;
        int end = Math.min(noteSize, stop);

        if (start >= noteSize && noteSize > 0) {
            c.setCurrentPage(0);
            start = 0;
            stop = screenSize;
            end = Math.min(noteSize, stop);
        }

        auctions = auctions.subList(start, end);
        int size = auctions.size();

        for (int i = 0; i < screenSize; ++i) {
            int slot = itemSlots.get(i);
            if (size - 1 < i) {
                this.addButton(slot, new InventoryButton()
                        .creator(player -> null)
                        .consumer(event -> {
                        }));
                continue;
            }
            AuctionItem note = auctions.stream().skip(i).findFirst().orElse(null);
            if (note == null)
                continue;
            this.addButton(slot, auctionItem(note));
        }
    }

    private boolean hasActiveFilters() {
        return !c.getCurrentSearch().isEmpty()
                || c.getBinFilter() != UserSession.BINFilter.ALL
                || c.getWhitelist() != null;
    }

    private InventoryButton noResultsButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(StringUtils.colorize("<red>Sin resultados"));
            List<String> lore = new ArrayList<>();
            lore.add(StringUtils.colorize("<gray>No se encontraron ítems con tus filtros."));
            if (!c.getCurrentSearch().isEmpty()) {
                lore.add(StringUtils.colorize("<gray>Búsqueda: <color:#FFD180>" + StringUtils.escapeMiniMessage(c.getCurrentSearch())));
            }
            if (c.getBinFilter() != UserSession.BINFilter.ALL) {
                lore.add(StringUtils.colorize("<gray>Tipo: <color:#FFD180>" + c.getBinFilter().name()));
            }
            lore.add("");
            lore.add(StringUtils.colorize("<yellow>Clic para limpiar filtros"));
            lore.add(StringUtils.colorize("<gray>(o clic derecho en la lupa)"));
            meta.setLore(lore);
            meta.setHideTooltip(true);
            item.setItemMeta(meta);
        }

        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Sounds.breakWood(event);
                    c.setCurrentSearch("");
                    c.setBinFilter(UserSession.BINFilter.ALL);
                    c.setWhitelist(null, null);
                    c.setCurrentPage(0);
                    update();
                });
    }

    private InventoryButton auctionItem(AuctionItem note) {
        boolean isAdmin = c.isAdmin();
        ItemStack item = ItemManager.createItemFromNote(note, c.getPlayer(), false, isAdmin);
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    // Admin Q key opens admin action menu (expire/delete options)
                    if (isAdmin && event.getClick() == org.bukkit.event.inventory.ClickType.DROP) {
                        Sounds.click(event);
                        AuctionHouse.getGuiManager().openGUI(new AdminActionGUI(note, c), c.getPlayer());
                        return;
                    }
                    if (ItemManager.isShulkerBox(item) && event.isRightClick()) {
                        Sounds.openShulker(event);
                        AuctionHouse.getGuiManager().openGUI(
                                new ShulkerViewGUI(note, c, UserSession.View.AUCTION_HOUSE), c.getPlayer());
                        return;
                    }
                    Sounds.click(event);
                    // Admin can also buy items (same as regular users)
                    if (Objects.equals(Bukkit.getPlayer(note.getPlayerUUID()), c.getPlayer()) && !note.isBIDAuction()) {
                        AuctionHouse.getGuiManager().openGUI(new CancelAuctionGUI(note, c), c.getPlayer());
                        return;
                    }
                    AuctionHouse.getGuiManager()
                            .openGUI(new AuctionViewGUI(note, c, 0, UserSession.View.AUCTION_HOUSE), c.getPlayer());
                });
    }

    private InventoryButton fillerButton() {
        ItemStack filler = SlotConfigManager.createFillerItem(GUI_NAME);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            filler.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> filler)
                .consumer(event -> {
                });
    }

    private InventoryButton refresh() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "refresh"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionHouse().getItemName("refresh"));
            meta.setLore(GuiConfigManager.auctionHouse().getLore("refresh-lore"));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), c.getPlayer());
                    Sounds.click(event);
                });
    }

    private InventoryButton nextPage() {
        int pages = (noteSize - 1) / screenSize;
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "next-page"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionHouse().getItemName("next-page"));
            meta.setLore(GuiConfigManager.auctionHouse().getLore("next-page-lore",
                    "{page}", String.valueOf(c.getCurrentPage() + 1),
                    "{pages}", String.valueOf(pages + 1))); // GuiConfig uses {page} in example, checking
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    if (c.getCurrentPage() == pages)
                        return;
                    if (event.isRightClick())
                        c.setCurrentPage(pages);
                    else
                        c.setCurrentPage(c.getCurrentPage() + 1);
                    Sounds.click(event);
                    update();
                });
    }

    private InventoryButton previousPage() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "previous-page"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionHouse().getItemName("prev-page"));
            meta.setLore(GuiConfigManager.auctionHouse().getLore("prev-page-lore",
                    "{page}", String.valueOf(c.getCurrentPage() + 1),
                    "{pages}", String.valueOf((noteSize - 1) / screenSize + 1)));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    if (c.getCurrentPage() == 0)
                        return;
                    if (event.isRightClick())
                        c.setCurrentPage(0);
                    else
                        c.setCurrentPage(c.getCurrentPage() - 1);
                    Sounds.click(event);
                    update();
                });
    }

    private InventoryButton searchOption() {
        boolean hasSearch = !c.getCurrentSearch().isEmpty();
        ItemStack item = new ItemStack(hasSearch
                ? SlotConfigManager.getMaterial(GUI_NAME, "search-active")
                : SlotConfigManager.getMaterial(GUI_NAME, "search"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionHouse().getItemName("search"));
            meta.setLore(GuiConfigManager.auctionHouse().getLore("search-lore", "{filter}", c.getCurrentSearch()));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    if (event.isRightClick()) {
                        Sounds.breakWood(event);
                        c.setCurrentSearch("");
                        c.setCurrentPage(0);
                        update();
                    } else {
                        Sounds.click(event);
                        InputManager.openSearch(c.getPlayer(), c.isAdmin(), (p, typedText) -> {
                            c.setCurrentSearch(typedText);
                            AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), p);
                        });
                    }
                });
    }

    private InventoryButton sortButton() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "sort"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionHouse().getItemName("filter"));
            List<String> lore = new ArrayList<>();
            lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "divider-top"));

            // Recently Posted
            if (c.getCurrentSort() == Sort.RECENTLY_POSTED) {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "recently-posted"));
            } else {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "recently-posted-inactive"));
            }

            // Highest Price
            if (c.getCurrentSort() == Sort.HIGHEST_PRICE) {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "highest-price"));
            } else {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "highest-price-inactive"));
            }

            // Lowest Price
            if (c.getCurrentSort() == Sort.LOWEST_PRICE) {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "lowest-price"));
            } else {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "lowest-price-inactive"));
            }

            // Ending Soon
            if (c.getCurrentSort() == Sort.ENDING_SOON) {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "ending-soon"));
            } else {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "ending-soon-inactive"));
            }

            // Alphabetical
            if (c.getCurrentSort() == Sort.ALPHABETICAL) {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "alphabetical"));
            } else {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "alphabetical-inactive"));
            }

            lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "divider-bottom"));
            lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "click-to-switch"));
            lore.add(GuiConfigManager.auctionHouse().getLoreLine("filter-lore", "right-click"));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Sounds.click(event);
                    if (event.isRightClick())
                        c.setCurrentSort(previousSort(c.getCurrentSort()));
                    else
                        c.setCurrentSort(nextSort(c.getCurrentSort()));
                    c.setCurrentPage(0);
                    update();
                });
    }

    private InventoryButton myAuctions() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "my-auctions"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionHouse().getItemName("my-auctions"));
            meta.setLore(GuiConfigManager.auctionHouse().getLore("my-auctions-lore",
                    "{listings_count}",
                    String.valueOf(AuctionManager.getInstance().getNumberOfAuctions(c.getPlayer().getUniqueId()))));
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    Sounds.openEnderChest(event);
                    AuctionHouse.getGuiManager().openGUI(new MyAuctionsGUI(c), (Player) event.getWhoClicked());
                });
    }

    private InventoryButton BINFilter() {
        ItemStack item = new ItemStack(SlotConfigManager.getMaterial(GUI_NAME, "bin-filter-all"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemName(GuiConfigManager.auctionHouse().getItemName("bin-filter-all"));

            List<String> lore = new ArrayList<>();
            lore.add(GuiConfigManager.auctionHouse().getLoreLine("bin-filter-lore", "divider-top"));

            // All
            if (c.getBinFilter() == UserSession.BINFilter.ALL) {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("bin-filter-lore", "all"));
            } else {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("bin-filter-lore", "all-inactive"));
            }

            // BIN Only
            if (c.getBinFilter() == UserSession.BINFilter.BIN_ONLY) {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("bin-filter-lore", "bin"));
            } else {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("bin-filter-lore", "bin-inactive"));
            }

            // Auctions Only
            if (c.getBinFilter() == UserSession.BINFilter.AUCTIONS_ONLY) {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("bin-filter-lore", "auctions"));
            } else {
                lore.add(GuiConfigManager.auctionHouse().getLoreLine("bin-filter-lore", "auctions-inactive"));
            }

            lore.add(GuiConfigManager.auctionHouse().getLoreLine("bin-filter-lore", "divider-bottom"));
            lore.add(GuiConfigManager.auctionHouse().getLoreLine("bin-filter-lore", "click-to-switch"));
            lore.add(GuiConfigManager.auctionHouse().getLoreLine("bin-filter-lore", "right-click"));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {
                    if (event.isRightClick())
                        c.setBinFilter(previousBINFilter(c.getBinFilter()));
                    else
                        c.setBinFilter(nextBINFilter(c.getBinFilter()));
                    Sounds.click(event);
                    update();
                });
    }

    private Sort nextSort(Sort input) {
        return switch (input) {
            case RECENTLY_POSTED -> Sort.HIGHEST_PRICE;
            case HIGHEST_PRICE -> Sort.LOWEST_PRICE;
            case LOWEST_PRICE -> Sort.ENDING_SOON;
            case ENDING_SOON -> Sort.ALPHABETICAL;
            case ALPHABETICAL -> Sort.RECENTLY_POSTED;
        };
    }

    private Sort previousSort(Sort input) {
        return switch (input) {
            case RECENTLY_POSTED -> Sort.ALPHABETICAL;
            case ALPHABETICAL -> Sort.ENDING_SOON;
            case ENDING_SOON -> Sort.LOWEST_PRICE;
            case LOWEST_PRICE -> Sort.HIGHEST_PRICE;
            case HIGHEST_PRICE -> Sort.RECENTLY_POSTED;
        };
    }

    private UserSession.BINFilter nextBINFilter(UserSession.BINFilter input) {
        return switch (input) {
            case ALL -> UserSession.BINFilter.BIN_ONLY;
            case BIN_ONLY -> UserSession.BINFilter.AUCTIONS_ONLY;
            case AUCTIONS_ONLY -> UserSession.BINFilter.ALL;
        };
    }

    private UserSession.BINFilter previousBINFilter(UserSession.BINFilter input) {
        return switch (input) {
            case ALL -> UserSession.BINFilter.AUCTIONS_ONLY;
            case AUCTIONS_ONLY -> UserSession.BINFilter.BIN_ONLY;
            case BIN_ONLY -> UserSession.BINFilter.ALL;
        };
    }
}
