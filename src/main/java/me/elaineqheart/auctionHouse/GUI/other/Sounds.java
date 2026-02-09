package me.elaineqheart.auctionHouse.GUI.other;

import me.elaineqheart.auctionHouse.configuration.SoundsConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Helper class for playing sounds in GUIs.
 * Delegates to SoundsConfig for configurability.
 */
public class Sounds {

    public static void click(InventoryClickEvent event) {
        SoundsConfig.playSound((Player) event.getWhoClicked(), "ui-click");
    }

    public static void openEnderChest(InventoryClickEvent event) {
        SoundsConfig.playSound((Player) event.getWhoClicked(), "open-gui");
    }

    public static void closeEnderChest(InventoryClickEvent event) {
        SoundsConfig.playSound((Player) event.getWhoClicked(), "close-gui");
    }

    public static void breakWood(InventoryClickEvent event) {
        SoundsConfig.playSound((Player) event.getWhoClicked(), "break-wood");
    }

    public static void experience(InventoryClickEvent event) {
        SoundsConfig.playSound((Player) event.getWhoClicked(), "experience");
    }

    public static void villagerDeny(InventoryClickEvent event) {
        SoundsConfig.playSound((Player) event.getWhoClicked(), "error");
    }

    public static void openShulker(InventoryClickEvent event) {
        SoundsConfig.playSound((Player) event.getWhoClicked(), "open-shulker");
    }

    public static void closeShulker(InventoryCloseEvent event) {
        SoundsConfig.playSound((Player) event.getPlayer(), "close-shulker");
    }

    public static void click(Player p) {
        SoundsConfig.playSound(p, "ui-click");
    }

    public static void npcClick(Player p) {
        SoundsConfig.playSound(p, "npc-click");
    }

    public static void success(Player p) {
        SoundsConfig.playSound(p, "success");
    }

    public static void error(Player p) {
        SoundsConfig.playSound(p, "error");
    }
}
