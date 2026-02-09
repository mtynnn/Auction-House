package me.elaineqheart.auctionHouse.GUI.other;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.SignGUIAction;
import de.rapha149.signgui.SignGUIResult;
import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.util.StringUtils;
import me.elaineqheart.auctionHouse.configuration.SettingManager;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Helper class for creating SignGUI input dialogs.
 * Uses the Rapha149/SignGUI library.
 */
public class SignInput {

    /**
     * Open a sign input for searching
     * 
     * @param player     The player to open the sign for
     * @param onComplete Callback with (player, inputText)
     */
    public static void openSearch(Player player, BiConsumer<Player, String> onComplete) {
        List<String> lines = SettingManager.searchSignLines;
        String line0 = lines.size() > 0 ? lines.get(0) : "";
        String line1 = lines.size() > 1 ? lines.get(1) : "↑↑↑↑↑↑↑↑↑↑↑↑↑";
        String line2 = lines.size() > 2 ? lines.get(2) : "       Buscar      ";
        String line3 = lines.size() > 3 ? lines.get(3) : "";

        open(player, line0, line1, line2, line3, (p, result) -> {
            String input = result.getLineWithoutColor(0).trim();
            onComplete.accept(p, input);
        });
    }

    /**
     * Open a sign input for setting a numeric value (bid amount, price, etc.)
     * 
     * @param player     The player
     * @param title      Description of what they're entering
     * @param onComplete Callback with input text
     */
    public static void openNumericInput(Player player, String title, BiConsumer<Player, String> onComplete) {
        open(player, "", "↑↑↑↑↑↑↑↑↑↑↑↑↑", title, "", (p, result) -> {
            String input = result.getLineWithoutColor(0).trim();
            onComplete.accept(p, input);
        });
    }

    /**
     * Open a sign input with custom lines
     * 
     * @param player     The player
     * @param line0      First line (editable by player)
     * @param line1      Second line
     * @param line2      Third line
     * @param line3      Fourth line
     * @param onComplete Callback with SignGUIResult
     */
    public static void open(Player player, String line0, String line1, String line2, String line3,
            BiConsumer<Player, SignGUIResult> onComplete) {
        try {
            SignGUI gui = SignGUI.builder()
                    .setLines(line0, line1, line2, line3)
                    .setType(Material.OAK_SIGN)
                    .setColor(DyeColor.BLACK)
                    .callHandlerSynchronously(AuctionHouse.getPlugin())
                    .setHandler((p, result) -> {
                        onComplete.accept(p, result);
                        return Collections.emptyList();
                    })
                    .build();

            gui.open(player);
        } catch (Throwable e) {
            // SignGUI not supported on this version, fallback message
            AuctionHouse.getPlugin().getLogger().severe("Error opening SignGUI: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage(me.elaineqheart.auctionHouse.util.StringUtils
                    .colorize("<red>Error al abrir el editor de carteles. Usa el chat o contacta a un administrador."));
        }
    }

    /**
     * Open a sign for admin reason input (expire/delete auction)
     * 
     * @param player     The player
     * @param action     "Expirar" or "Eliminar"
     * @param onComplete Callback with reason text
     */
    public static void openAdminReason(Player player, String action, BiConsumer<Player, String> onComplete) {
        open(player, "", "↑↑↑↑↑↑↑↑↑↑↑↑↑", "Razón para " + action, "", (p, result) -> {
            String reason = result.getLineWithoutColor(0).trim();
            if (reason.isEmpty()) {
                reason = "Sin razón especificada";
            }
            onComplete.accept(p, reason);
        });
    }
}
