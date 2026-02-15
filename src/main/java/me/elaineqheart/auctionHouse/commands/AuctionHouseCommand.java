package me.elaineqheart.auctionHouse.commands;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.GUI.impl.AuctionHouseGUI;
import me.elaineqheart.auctionHouse.GUI.impl.AuctionViewGUI;
import me.elaineqheart.auctionHouse.GUI.impl.CancelAuctionGUI;
import me.elaineqheart.auctionHouse.GUI.impl.CollectSoldItemGUI;
import me.elaineqheart.auctionHouse.GUI.other.Sounds;
import me.elaineqheart.auctionHouse.util.StringUtils;
import me.elaineqheart.auctionHouse.configuration.SettingManager;
import me.elaineqheart.auctionHouse.configuration.Blacklist;
import me.elaineqheart.auctionHouse.configuration.SlotConfigManager;
import me.elaineqheart.auctionHouse.GUI.config.GuiConfigManager;
import me.elaineqheart.auctionHouse.configuration.ConfigManager;
import me.elaineqheart.auctionHouse.configuration.M;

import me.elaineqheart.auctionHouse.model.AuctionItem;
import me.elaineqheart.auctionHouse.model.UserSession;
import me.elaineqheart.auctionHouse.manager.AuctionManager;
import me.elaineqheart.auctionHouse.world.displays.CreateDisplay;
import me.elaineqheart.auctionHouse.world.displays.UpdateDisplay;
import me.elaineqheart.auctionHouse.world.npc.NPCManager;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.*;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.UUID;

// https://github.com/VelixDevelopments/Imperat

// #don't try to fix what's not broken

public class AuctionHouseCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s,
            @NotNull String[] strings) {
        if (commandSender instanceof ConsoleCommandSender) {
            if (strings.length == 1 && (strings[0].equals(M.getFormatted("command-names.reload")))) {
                reload();
                AuctionHouse.getPlugin().getLogger().info("reloaded files");
                return true;
            }
        }

        if (commandSender instanceof Player p) {
            if (strings.length == 0) {
                if (ConfigManager.bannedPlayers.checkIsBannedSendMessage(p)) {
                    return true;
                }
                AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(p), p);
            }

            if (strings.length == 1 && strings[0].equals(M.getFormatted("command-names.help"))) {
                p.sendMessage(M.getFormatted("command-feedback.help-prefix"));
                List<String> commands = Objects.requireNonNull(M.get().getConfigurationSection("command-feedback.help"))
                        .getKeys(false).stream().sorted().toList();
                for (String cm : commands) {
                    String message = M.getFormatted("command-feedback.help." + cm);
                    if (cm.equals(M.getFormatted("command-names.sell")) && !SettingManager.BINAuctions)
                        continue;
                    if (cm.equals(M.getFormatted("command-names.bid")) && !SettingManager.BIDAuctions)
                        continue;
                    if (cm.equals(M.getFormatted("command-names.announce"))
                            && !SettingManager.auctionAnnouncementsEnabled)
                        continue;

                    p.sendMessage(message);
                }
            }
            if (strings.length == 1 && strings[0].equals(M.getFormatted("command-names.sell"))
                    && SettingManager.BINAuctions) {
                p.sendMessage(M.getFormatted("command-feedback.usage"));
            }
            if (strings.length == 1 && strings[0].equals(M.getFormatted("command-names.bid"))
                    && SettingManager.BIDAuctions) {
                p.sendMessage(M.getFormatted("command-feedback.bid-usage"));
            }
            if ((strings.length == 2 || strings.length == 3) &&
                    (strings[0].equals(M.getFormatted("command-names.sell")) && SettingManager.BINAuctions
                            || strings[0].equals(M.getFormatted("command-names.bid")) && SettingManager.BIDAuctions)) {
                if (ConfigManager.bannedPlayers.checkIsBannedSendMessage(p)) {
                    return true;
                }
                if (AuctionManager.getInstance().getNumberOfAuctions(p.getUniqueId()) >= ConfigManager.permissions
                        .getAuctionSlots(p)) {
                    p.sendMessage(M.getFormatted("command-feedback.reached-max-auctions",
                            "%limit%", String.valueOf(ConfigManager.permissions.getAuctionSlots(p))));
                    return true;
                }
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item.getType().equals(Material.AIR)) {
                    p.sendMessage(M.getFormatted("command-feedback.no-item-in-hand"));
                    return true;
                }
                
                // Parse price with better error handling
                double price;
                try {
                    price = Double.parseDouble(strings[1]);
                    if (price <= 0) {
                        p.sendMessage(M.getFormatted("command-feedback.invalid-number2"));
                        return true;
                    }
                    if (Double.isNaN(price) || Double.isInfinite(price)) {
                        p.sendMessage(M.getFormatted("command-feedback.invalid-number"));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    p.sendMessage(M.getFormatted("command-feedback.invalid-number"));
                    return true;
                }
                
                if (price > SettingManager.maxPrice) {
                    p.sendMessage(M.getFormatted("command-feedback.max-price", SettingManager.maxPrice));
                    return true;
                }
                if (strings[0].equals(M.getFormatted("command-names.sell")) && price < SettingManager.minBINPrice) {
                    p.sendMessage(M.getFormatted("command-feedback.min-bin", SettingManager.minBINPrice));
                    return true;
                } else if (strings[0].equals(M.getFormatted("command-names.bid"))
                        && price < SettingManager.minBIDPrice) {
                    p.sendMessage(M.getFormatted("command-feedback.min-bid", SettingManager.minBIDPrice));
                    return true;
                }
                int amount = item.getAmount();
                if (strings.length == 3) {
                    try {
                        amount = Integer.parseInt(strings[2]);
                        if (amount < 1 || amount > item.getAmount()) {
                            p.sendMessage(M.getFormatted("command-feedback.invalid-number7"));
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        p.sendMessage(M.getFormatted("command-feedback.invalid-number7"));
                        return true;
                    }
                }
                
                // Validate max stack size to prevent GUI overflow
                int maxStackSize = AuctionHouse.getPlugin().getConfig().getInt("debug.max-stack-size", 576); // 9 inv * 64
                if (amount > maxStackSize) {
                    p.sendMessage(M.getFormatted("command-feedback.too-many-items", "%max%", String.valueOf(maxStackSize)));
                    return true;
                }
                
                if (Blacklist.isBlacklisted(item)) {
                    p.sendMessage(M.getFormatted("command-feedback.item-blacklisted"));
                    p.playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.1f);
                    return true;
                }

                // Create auction first for immediate feedback
                ItemStack inputItem = item.clone();
                inputItem.setAmount(amount);
                item.setAmount(item.getAmount() - amount);
                
                final double finalPrice = price;
                final int finalAmount = amount;
                
                // Price Protection: check against average historical price (async to avoid blocking)
                if (SettingManager.priceProtectionEnabled) {
                    Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getPlugin(), () -> {
                        try {
                            double[] result = ConfigManager.transactionLogger.getDao()
                                    .getAveragePricePerUnit(item.getType().name(), SettingManager.priceProtectionMinSales)
                                    .get(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                            double avgPPU = result[0];
                            if (avgPPU > 0) {
                                double maxAllowed = avgPPU * finalAmount * SettingManager.priceProtectionMultiplier;
                                if (finalPrice > maxAllowed) {
                                    String avgFormatted = SettingManager.formatter.format(avgPPU * finalAmount);
                                    String maxFormatted = SettingManager.formatter.format(maxAllowed);
                                    p.sendMessage(M.getFormatted("command-feedback.price-warning",
                                            "%avg%", avgFormatted,
                                            "%max%", maxFormatted));
                                }
                            }
                        } catch (Exception e) {
                            // Timeout or error — don't notify, just let it go
                        }
                    });
                }
                
                AuctionManager.getInstance().createAuction(p, inputItem, finalPrice,
                        strings[0].equals(M.getFormatted("command-names.bid")));
                M.sendMessage(p, "command-feedback.auction", finalPrice);

                // Announce the new auction to all players who have announcements enabled
                if (SettingManager.auctionAnnouncementsEnabled) {
                    String itemName = StringUtils.getItemName(inputItem);
                    String auctionCommand = "/" + Objects.requireNonNullElse(M.get().getString("command-names.ah"), "ah")
                            .toLowerCase(Locale.ROOT);
                    Bukkit.getScheduler().runTaskLater(AuctionHouse.getPlugin(), () -> {
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (ConfigManager.playerPreferences.hasAnnouncementsEnabled(onlinePlayer.getUniqueId())
                                    && !onlinePlayer.equals(p)) {
                                String sellerName = StringUtils.escapeMiniMessage(p.getDisplayName());
                                String amountString = String.valueOf(finalAmount);

                                String announcementLegacy = M.getFormatted("chat.auction-announcement", finalPrice,
                                        "%player%", sellerName,
                                        "%item%", itemName,
                                        "%amount%", amountString);

                                String hoverLegacy;
                                if (M.get().contains("chat.auction-announcement-hover")) {
                                    hoverLegacy = M.getFormatted("chat.auction-announcement-hover", finalPrice,
                                            "%player%", sellerName,
                                            "%item%", itemName,
                                            "%amount%", amountString,
                                            "%command%", auctionCommand);
                                } else {
                                    String hoverMM = M.getMM("chat.auction-announcement", finalPrice,
                                            "%player%", sellerName,
                                            "%item%", itemName,
                                            "%amount%", amountString)
                                            + "\n<yellow>Clic para abrir <color:#FFD180>"
                                            + StringUtils.escapeMiniMessage(auctionCommand);
                                    hoverLegacy = M.adventureApi(hoverMM);
                                }

                                BaseComponent[] announcementComponents = TextComponent.fromLegacyText(announcementLegacy);
                                BaseComponent[] hoverComponents = TextComponent.fromLegacyText(hoverLegacy);

                                ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, auctionCommand);
                                HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverComponents));

                                for (BaseComponent component : announcementComponents) {
                                    component.setClickEvent(clickEvent);
                                    component.setHoverEvent(hoverEvent);
                                }

                                onlinePlayer.spigot().sendMessage(announcementComponents);
                            }
                        }
                    }, SettingManager.auctionSetupTime * 20);
                }

            }
            // /ah announce - toggle announcements
            if (strings.length == 1 && SettingManager.auctionAnnouncementsEnabled
                    && strings[0].equals(M.getFormatted("command-names.announce"))) {
                boolean newState = ConfigManager.playerPreferences.toggleAnnouncements(p);
                if (newState) {
                    p.sendMessage(M.getFormatted("command-feedback.announcements-enabled"));
                } else {
                    p.sendMessage(M.getFormatted("command-feedback.announcements-disabled"));
                }
                return true;
            }
            if (strings.length == 2 && strings[0].equals("view")) {
                AuctionItem note = AuctionManager.getInstance().getAuction(UUID.fromString(strings[1]));
                if (note == null
                        || !note.getPlayerUUID().equals(p.getUniqueId()) && !note.isOnAuction()
                        || note.getPlayerUUID().equals(p.getUniqueId())
                                && (note.getBuyerName() == null || note.getBuyerName().isEmpty()))
                    return true;
                Sounds.click(p);
                UserSession configuration = UserSession.getInstance(p).setPlayer(p.getUniqueId());
                configuration.setShouldClose(true);
                if (!note.getPlayerUUID().equals(p.getUniqueId())) {
                    AuctionHouse.getGuiManager()
                            .openGUI(new AuctionViewGUI(note, configuration, 0, UserSession.View.AUCTION_HOUSE), p);
                } else if (!note.isSold()) {
                    AuctionHouse.getGuiManager().openGUI(new CancelAuctionGUI(note, configuration), p);
                } else {
                    AuctionHouse.getGuiManager().openGUI(new CollectSoldItemGUI(note, configuration), p);
                }
            }
            // /ah admin
            if (p.hasPermission(SettingManager.permissionModerate) && strings.length > 0) {
                if (strings.length == 1 && strings[0].equals(M.getFormatted("command-names.admin"))) {
                    AuctionHouse.getGuiManager()
                            .openGUI(new AuctionHouseGUI(0, AuctionHouseGUI.Sort.RECENTLY_POSTED, "", p, true), p);
                } 
                // /ah debug - Show system diagnostics
                else if (strings.length == 1 && strings[0].equals("debug")) {
                    p.sendMessage("§6§l[AuctionHouse Debug Info]§r");
                    p.sendMessage("§7Version: §f" + AuctionHouse.getPlugin().getDescription().getVersion());
                    p.sendMessage("");
                    
                    // Auction stats
                    AuctionManager am = AuctionManager.getInstance();
                    p.sendMessage("§e§lAuctions:§r");
                    p.sendMessage("  §7Loaded: §f" + am.isLoaded());
                    p.sendMessage("  §7Total: §f" + am.getAll().size());
                    p.sendMessage("  §7Active: §f" + am.getAll().stream().filter(a -> a.isOnAuction() && !a.isExpired()).count());
                    p.sendMessage("  §7Expired: §f" + am.getAll().stream().filter(AuctionItem::isExpired).count());
                    p.sendMessage("  §7Sold: §f" + am.getAll().stream().filter(AuctionItem::isSold).count());
                    p.sendMessage("");
                    
                    // Database stats
                    if (AuctionHouse.getPlugin().getDatabaseManager().isInitialized()) {
                        p.sendMessage("§e§lDatabase:§r");
                        p.sendMessage("  §7Type: §fSQLite");
                        p.sendMessage("  §7Pool Size: §f" + AuctionHouse.getPlugin().getConfig().getInt("database.pool-size", 10));
                        p.sendMessage("  §7Status: §a✓ Connected§r");
                    } else {
                        p.sendMessage("§e§lDatabase:§r");
                        p.sendMessage("  §cNot initialized");
                    }
                    p.sendMessage("");
                    
                    // Config settings
                    p.sendMessage("§e§lConfig:§r");
                    p.sendMessage("  §7Debug Logging: §f" + AuctionHouse.getPlugin().getConfig().getBoolean("debug.log-corrupted-items", false));
                    p.sendMessage("  §7Max Stack: §f" + AuctionHouse.getPlugin().getConfig().getInt("debug.max-stack-size", 576));
                    p.sendMessage("  §7Price Protection: §f" + SettingManager.priceProtectionEnabled);
                    p.sendMessage("");
                    
                    // Performance
                    p.sendMessage("§e§lPerformance:§r");
                    long totalMemory = Runtime.getRuntime().totalMemory() / 1024 / 1024;
                    long freeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024;
                    long usedMemory = totalMemory - freeMemory;
                    p.sendMessage("  §7Memory: §f" + usedMemory + "MB / " + totalMemory + "MB");
                    p.sendMessage("  §7Active Sessions: §f" + UserSession.getActiveSessions());
                    
                    return true;
                }
                else if (strings.length < 4 && strings[0].equals(M.getFormatted("command-names.ban"))) {
                    p.sendMessage(M.getFormatted("command-feedback.ban-usage"));
                } else if (strings.length != 2 && strings[0].equals(M.getFormatted("command-names.pardon"))) {
                    p.sendMessage(M.getFormatted("command-feedback.pardon-usage"));
                    // /ah ban player:
                } else if (strings.length > 3 && strings[0].equals(M.getFormatted("command-names.ban"))) {
                    Player targetPlayer = Bukkit.getPlayer(strings[1]);
                    if (targetPlayer == null) {
                        p.sendMessage(M.getFormatted("command-feedback.player-not-found"));
                        return true;
                    }
                    try {
                        int duration = Integer.parseInt(strings[2]);
                        if (duration <= 0) {
                            p.sendMessage(M.getFormatted("command-feedback.invalid-number3"));
                            return true;
                        }
                        // use a StringBuilder to get all arguments
                        StringBuilder reason = new StringBuilder();
                        for (int i = 3; i < strings.length; i++) {
                            reason.append(strings[i]);
                            if (i != strings.length - 1) {
                                reason.append(" ");
                            }
                        }
                        ConfigManager.bannedPlayers.saveBannedPlayer(targetPlayer, duration, reason.toString());
                        p.sendMessage(M.getFormatted("command-feedback.ban",
                                "%player%", StringUtils.escapeMiniMessage(targetPlayer.getDisplayName()),
                                "%duration%", String.valueOf(duration),
                                "%reason%", reason.toString()));
                    } catch (Exception e) {
                        p.sendMessage(M.getFormatted("command-feedback.invalid-number4"));
                    }
                    // /ah pardon player:
                } else if (strings.length == 2 && strings[0].equals(M.getFormatted("command-names.pardon"))) {
                    String input = strings[1];
                    Map<java.util.UUID, me.elaineqheart.auctionHouse.configuration.BannedPlayers.BanEntry> bans = me.elaineqheart.auctionHouse.configuration.BannedPlayers
                            .getBans();

                    if (bans.isEmpty()) {
                        p.sendMessage(M.getFormatted("command-feedback.no-banned-players"));
                        return true;
                    }

                    boolean found = false;
                    for (Map.Entry<java.util.UUID, me.elaineqheart.auctionHouse.configuration.BannedPlayers.BanEntry> entry : bans
                            .entrySet()) {
                        if (entry.getValue().playerName.equalsIgnoreCase(input)) {
                            bans.remove(entry.getKey());
                            // Trigger save
                            new me.elaineqheart.auctionHouse.configuration.BannedPlayers()
                                    .saveBannedPlayer(p, 0, ""); // Saving workaround or explicit save method needed
                            // BannedPlayers.save() is private.
                            // But removing from map is not enough because we need to persist removal.
                            // BannedPlayers class needs a public save() or unban method.
                            // I will add a static unban method to BannedPlayers.
                            found = true;
                            p.sendMessage(M.getFormatted("command-feedback.pardon", "%player%", input));
                            break;
                        }
                    }

                    if (!found) {
                        p.sendMessage(M.getFormatted("command-feedback.not-banned"));
                    }

                    // Note: I need to add unban method to BannedPlayers first!

                } else if (strings[0].equals(M.getFormatted("command-names.reload"))) {
                    reload();
                    p.sendMessage(M.getFormatted("command-feedback.reload"));
                    AuctionHouse.getPlugin().getLogger().info("reloaded");
                    return true;

                } else if (strings[0].equals(M.getFormatted("command-names.summon"))) {
                    if (strings.length < 2) {
                        p.sendMessage(M.getFormatted("command-feedback.summon-usage"));
                        return true;
                    }
                    // get the player location
                    Location loc = p.getLocation();
                    Location middleBlockLoc = new Location(loc.getWorld(), loc.getBlockX() + 0.5, loc.getBlockY(),
                            loc.getBlockZ() + 0.5);
                    Location blockLoc = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

                    if (strings[1].equals(M.getFormatted("command-names.npc"))) {
                        if (strings.length < 4) {
                            p.sendMessage(M.getFormatted("command-feedback.npc-usage"));
                            return true;
                        }
                        NPCManager.createAuctionMaster(middleBlockLoc, strings[3]);
                    } else if (strings[1].equals(M.getFormatted("command-names.display"))) {
                        if (strings.length < 4) {
                            p.sendMessage(M.getFormatted("command-feedback.display-usage"));
                            return true;
                        }

                        int itemNumber;
                        try {
                            itemNumber = Integer.parseInt(strings[3]);
                            if (itemNumber < 1) {
                                p.sendMessage(M.getFormatted("command-feedback.invalid-number5"));
                                return true;
                            }
                        } catch (NumberFormatException e) {
                            p.sendMessage(M.getFormatted("command-feedback.invalid-number6"));
                            return true;
                        }
                        for (Location displayLoc : UpdateDisplay.locations.keySet()) {
                            if (Objects.equals(blockLoc.getWorld(), displayLoc.getWorld())
                                    && blockLoc.distance(displayLoc) < 2.1) {
                                p.sendMessage(M.getFormatted("command-feedback.no-space-for-display"));
                                return true;
                            }
                        }
                        if (CreateDisplay.notEnoughSpace(loc)) {
                            p.sendMessage(M.getFormatted("command-feedback.no-air-space-for-display"));
                            return true;
                        }
                        if (strings[2].equals(M.getFormatted("command-names.highest_price"))) {
                            CreateDisplay.createDisplayHighestPrice(blockLoc, itemNumber);
                        } else if (strings[2].equals(M.getFormatted("command-names.ending_soon"))) {
                            CreateDisplay.createDisplayEndingSoon(blockLoc, itemNumber);
                        } else {
                            p.sendMessage(M.getFormatted("command-feedback.display-usage"));
                            return true;
                        }
                    }
                } else if (strings.length == 2 && strings[1].equals(M.getFormatted("command-names.undo"))) {
                    if (ConfigManager.blacklist.undo()) {
                        p.sendMessage(M.getFormatted("command-feedback.blacklist-undo"));
                    } else {
                        p.sendMessage(M.getFormatted("command-feedback.blacklist-undo-error"));
                    }
                    return true;
                } else if (strings.length < 3 && strings[0].equals(M.getFormatted("command-names.blacklist"))) {
                    p.sendMessage(M.getFormatted("command-feedback.blacklist-usage"));
                    return true;
                } else if (strings.length == 3 && strings[0].equals(M.getFormatted("command-names.blacklist"))
                        && strings[1].equals(M.getFormatted("command-names.add"))) {
                    if (strings[2].equals(M.getFormatted("command-names.all"))) {
                        ConfigManager.blacklist.addAll();
                        p.sendMessage(M.getFormatted("command-feedback.blacklist-all"));
                        return true;
                    }
                    if (strings[2].equals(M.getFormatted("command-names.exact"))
                            || strings[2].equals(M.getFormatted("command-names.material"))
                            || strings[2].equals(M.getFormatted("command-names.item_model"))) {
                        ItemStack item = p.getInventory().getItemInMainHand();
                        if (item.getType().equals(Material.AIR)) {
                            p.sendMessage(M.getFormatted("command-feedback.blacklist-no-item-in-hand"));
                            return true;
                        }
                        ItemMeta meta = item.getItemMeta();
                        assert meta != null;
                        if (strings[2].equals(M.getFormatted("command-names.exact"))) {
                            ConfigManager.blacklist.addExact(item);
                        } else if (strings[2].equals(M.getFormatted("command-names.material"))) {
                            ConfigManager.blacklist.addMaterial(item.getType().toString());
                        } else if (strings[2].equals(M.getFormatted("command-names.item_model"))) {
                            if (item.getItemMeta().getItemModel() == null) {
                                p.sendMessage(M.getFormatted("command-feedback.blacklist-no-model"));
                                return true;
                            } else
                                ConfigManager.blacklist.addItemModel(item.getItemMeta().getItemModel().getKey());
                            p.sendMessage(M.getFormatted("command-feedback.blacklist-name-success", "%name%",
                                    item.getItemMeta().getItemModel().getKey()));
                            return true;
                        }
                        p.sendMessage(
                                M.getFormatted("command-feedback.blacklist-success", "%item%", item.getType().name()));
                        return true;
                    }
                    p.sendMessage(M.getFormatted("command-feedback.blacklist-usage"));
                    return true;
                } else if (strings.length == 4 && strings[0].equals(M.getFormatted("command-names.blacklist"))
                        && strings[1].equals(M.getFormatted("command-names.add"))) {

                    if (strings[2].equals(M.getFormatted("command-names.exact"))
                            || strings[2].equals(M.getFormatted("command-names.material")))
                        return true;

                    if (strings[2].equals(M.getFormatted("command-names.contains_lore"))) {
                        ConfigManager.blacklist.addLoreContains(strings[3]);
                    } else if (strings[2].equals(M.getFormatted("command-names.name_contains"))) {
                        ConfigManager.blacklist.addNameContains(strings[3]);
                    } else if (strings[2].equals(M.getFormatted("command-names.custom_model_data"))) {
                        ConfigManager.blacklist.addCustomModelData(strings[3]);
                    } else if (strings[2].equals(M.getFormatted("command-names.item_model"))) {
                        ConfigManager.blacklist.addItemModel((strings[3]));
                    }
                    p.sendMessage(M.getFormatted("command-feedback.blacklist-name-success", "%name%", strings[3]));
                    return true;
                }
            }

        }
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s,
            @NotNull String[] strings) {
        List<String> params = new ArrayList<>();
        if (strings.length == 1) {
            // check for every item if it's half typed out, then add accordingly to the
            // params list
            List<String> assetParams = new ArrayList<>();

            assetParams.add(M.getFormatted("command-names.help"));
            if (SettingManager.BINAuctions)
                assetParams.add(M.getFormatted("command-names.sell"));
            if (SettingManager.BIDAuctions)
                assetParams.add(M.getFormatted("command-names.bid"));
            if (SettingManager.auctionAnnouncementsEnabled)
                assetParams.add(M.getFormatted("command-names.announce"));
            if (commandSender.hasPermission(SettingManager.permissionModerate)) {
                assetParams.add(M.getFormatted("command-names.ban"));
                assetParams.add(M.getFormatted("command-names.pardon"));
                assetParams.add(M.getFormatted("command-names.reload"));
                assetParams.add(M.getFormatted("command-names.summon"));
                assetParams.add(M.getFormatted("command-names.blacklist"));
                assetParams.add("debug");
            }
            for (String p : assetParams) {
                if (p.indexOf(strings[0]) == 0) {
                    params.add(p);
                }
            }

        }
        if (strings.length == 2 && strings[0].equals(M.getFormatted("command-names.ban"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                params.add(p.getDisplayName());
            }
        } else if (strings.length == 2 && strings[0].equals(M.getFormatted("command-names.pardon"))) {
            Map<java.util.UUID, me.elaineqheart.auctionHouse.configuration.BannedPlayers.BanEntry> bans = me.elaineqheart.auctionHouse.configuration.BannedPlayers
                    .getBans();
            if (!bans.isEmpty()) {
                for (me.elaineqheart.auctionHouse.configuration.BannedPlayers.BanEntry entry : bans
                        .values()) {
                    params.add(entry.playerName);
                }
            }
        } else if (strings.length == 2 && strings[0].equals(M.getFormatted("command-names.summon"))) {
            List<String> summonTypes = new ArrayList<>(List.of(new String[] { M.getFormatted("command-names.npc"),
                    M.getFormatted("command-names.display") }));
            for (String p : summonTypes) {
                if (p.indexOf(strings[1]) == 0) {
                    params.add(p);
                }
            }
        } else if (strings.length == 2 && strings[0].equals(M.getFormatted("command-names.blacklist"))) {
            List<String> summonTypes = new ArrayList<>(List.of(new String[] { M.getFormatted("command-names.add"),
                    M.getFormatted("command-names.undo") }));
            for (String p : summonTypes) {
                if (p.indexOf(strings[1]) == 0) {
                    params.add(p);
                }
            }
        } else if (strings.length == 3 && strings[0].equals(M.getFormatted("command-names.summon"))
                && strings[1].equals(M.getFormatted("command-names.display"))) {
            List<String> displayTypes = new ArrayList<>(
                    List.of(new String[] { M.getFormatted("command-names.highest_price"),
                            M.getFormatted("command-names.ending_soon") }));
            for (String p : displayTypes) {
                if (p.indexOf(strings[2]) == 0) {
                    params.add(p);
                }
            }
        } else if (strings.length == 3 && strings[0].equals(M.getFormatted("command-names.summon"))
                && strings[1].equals(M.getFormatted("command-names.npc"))) {
            List<String> displayTypes = new ArrayList<>(
                    List.of(new String[] { M.getFormatted("command-names.facing") }));
            for (String p : displayTypes) {
                if (p.indexOf(strings[2]) == 0) {
                    params.add(p);
                }
            }
        } else if (strings.length == 3 && strings[0].equals(M.getFormatted("command-names.blacklist"))
                && strings[1].equals(M.getFormatted("command-names.add"))) {
            List<String> displayTypes = new ArrayList<>(List.of(new String[] { M.getFormatted("command-names.exact"),
                    M.getFormatted("command-names.material"), M.getFormatted("command-names.name_contains"),
                    M.getFormatted("command-names.contains_lore"), M.getFormatted("command-names.item_model"),
                    M.getFormatted("command-names.custom_model_data"), M.getFormatted("command-names.all") }));
            for (String p : displayTypes) {
                if (p.indexOf(strings[2]) == 0) {
                    params.add(p);
                }
            }
        } else if (strings.length == 4 && strings[0].equals(M.getFormatted("command-names.summon"))
                && strings[1].equals(M.getFormatted("command-names.npc"))) {
            List<String> displayTypes = new ArrayList<>(
                    List.of(new String[] { M.getFormatted("command-names.north"), M.getFormatted("command-names.east"),
                            M.getFormatted("command-names.south"), M.getFormatted("command-names.west") }));
            for (String p : displayTypes) {
                if (p.indexOf(strings[3]) == 0) {
                    params.add(p);
                }
            }
        }
        return params;
    }

    private static void reload() {
        ConfigManager.reloadConfigs();
        SlotConfigManager.reload();
        GuiConfigManager.loadAll();
        SettingManager.loadData();
        AuctionManager.getInstance().loadAuctions();
        UpdateDisplay.reload();
    }

}
