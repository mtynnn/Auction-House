package me.elaineqheart.auctionHouse.GUI.other;

import org.bukkit.entity.Player;
import java.util.function.BiConsumer;

/**
 * Unified input manager that forces SignGUI for all inputs.
 */
public class InputManager {

    /**
     * Open an input dialog for the player
     * 
     * @param player            The player
     * @param inventoryTitleKey Unused (legacy AnvilGUI)
     * @param signTitle         Title to show on sign (line 2/3)
     * @param callback          Handler for when input is received
     */
    public static void openInput(Player player, String inventoryTitleKey, String signTitle,
            BiConsumer<Player, String> callback) {
        SignInput.openNumericInput(player, signTitle, callback);
    }

    /**
     * Open a search input dialog
     * 
     * @param player   The player
     * @param isAdmin  Whether this is an admin search
     * @param callback Handler for the search text
     */
    public static void openSearch(Player player, boolean isAdmin, BiConsumer<Player, String> callback) {
        SignInput.openSearch(player, callback);
    }

    /**
     * Open an admin reason input (for expire/delete)
     * 
     * @param player   The player
     * @param isDelete True for delete, false for expire
     * @param callback Handler for the reason text
     */
    public static void openAdminReason(Player player, boolean isDelete, BiConsumer<Player, String> callback) {
        String action = isDelete ? "Eliminar" : "Expirar";
        SignInput.openAdminReason(player, action, callback);
    }

    /**
     * Open set amount input
     * 
     * @param player   The player
     * @param callback Handler for the amount text
     */
    public static void openSetAmount(Player player, BiConsumer<Player, String> callback) {
        openInput(player, "inventory-titles.anvil-set-amount", "Cantidad", callback);
    }

    /**
     * Open set bid input
     * 
     * @param player   The player
     * @param callback Handler for the bid text
     */
    public static void openSetBid(Player player, BiConsumer<Player, String> callback) {
        openInput(player, "inventory-titles.anvil-set-bid", "Puja", callback);
    }
}
