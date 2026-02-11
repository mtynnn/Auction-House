package me.elaineqheart.auctionHouse.configuration;

import me.elaineqheart.auctionHouse.util.StringUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class M extends Config {

    private static final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static LegacyComponentSerializer getLegacySerializer() {
        return legacy;
    }

    public static MiniMessage getMiniMessage() {
        return mm;
    }

    // AuctionHouse.getPlugin().saveResource("messages.yml", false);

    public static FileConfiguration get() {
        return ConfigManager.messages.getCustomFile();
    }

    public static String getValue(String key, boolean convertNewLine) {
        String message = get().getString(key);
        if (message == null) {
            return ChatColor.RED + "Missing message key: " + key;
        }
        // Auto-replace %prefix% with the configured prefix
        String prefix = get().getString("general.prefix", "");
        message = message.replace("%prefix%", prefix);
        return convertNewLine ? message.replace("&n", "\n") : message;
    }

    // this is to replace placeholders like %player%
    public static String getFormatted(String key, String... replacements) {
        return adventureApi(getMM(key, replacements));
    }

    public static String getFormatted(String key, double price, String... replacements) {
        return adventureApi(getMM(key, price, replacements));
    }

    /**
     * Get the raw MiniMessage string with placeholders replaced.
     * This avoids early conversion to legacy color codes.
     */
    public static String getMM(String key, String... replacements) {
        String message = getValue(key, true);
        return replacePlaceholders(key, message, replacements);
    }

    /**
     * Get the raw MiniMessage string with placeholders and price replaced.
     */
    public static String getMM(String key, double price, String... replacements) {
        String message = getValue(key, true);
        message = replacePlaceholders(key, message, replacements);
        message = replace(message, price);
        return message;
    }

    public static List<String> getLoreList(String key, String... replacements) {
        if (get().isList(key)) {
            List<String> list = get().getStringList(key);
            for (int i = 0; i < list.size(); i++) {
                String line = list.get(i);
                String prefix = get().getString("general.prefix", "");
                line = line.replace("%prefix%", prefix);
                line = replacePlaceholders(key, line, replacements);
                list.set(i, adventureApi("<!italic>" + line));
            }
            return list;
        }
        String message = getValue(key, false);
        message = replacePlaceholders(key, message, replacements);
        List<String> list = new java.util.ArrayList<>(Arrays.asList(message.split("&n")));
        list.replaceAll(s -> adventureApi("<!italic>" + s));
        return list;
    }

    public static List<String> getLoreList(String key, double price, String... replacements) {
        if (get().isList(key)) {
            List<String> list = get().getStringList(key);
            for (int i = 0; i < list.size(); i++) {
                String line = list.get(i);
                String prefix = get().getString("general.prefix", "");
                line = line.replace("%prefix%", prefix);
                line = replacePlaceholders(key, line, replacements);
                line = replace(line, price);
                list.set(i, adventureApi("<!italic>" + line));
            }
            return list;
        }
        String message = getValue(key, false);
        message = replacePlaceholders(key, message, replacements);
        message = replace(message, price);
        List<String> list = new java.util.ArrayList<>(Arrays.asList(message.split("&n")));
        list.replaceAll(s -> adventureApi("<!italic>" + s));
        return list;
    }

    private static String replacePlaceholders(String key, String message, String... replacements) {
        if (replacements.length % 2 != 0) {
            return ChatColor.RED + "Invalid placeholder replacements for key: " + key;
        }
        for (int i = 0; i < replacements.length; i += 2) {
            String replacement = replacements[i + 1];
            if (replacement == null)
                replacement = "null";

            // If the replacement contains legacy codes, convert it to MiniMessage tags
            // to avoid "Legacy formatting codes have been detected in a MiniMessage string"
            // errors
            if (replacement.contains("§") || replacement.contains("&")) {
                try {
                    // First translate & to § if present
                    if (replacement.contains("&")) {
                        replacement = ChatColor.translateAlternateColorCodes('&', replacement);
                    }
                    // Then convert § to MiniMessage
                    replacement = mm.serialize(legacy.deserialize(replacement));
                } catch (Exception ignored) {
                    // If conversion fails, just escape it to be safe
                    replacement = StringUtils.escapeMiniMessage(replacement);
                }
            }
            message = message.replace(replacements[i], replacement);
        }
        return message;
    }

    public static String replace(String message, double... prices) {
        message = message.replace("%price%", StringUtils.formatPriceMM(prices[0]));
        message = message.replace("%price-trim%", StringUtils.formatPriceMM(StringUtils.getPriceTrimmed(prices[0])));
        message = message.replace("%number%", StringUtils.formatNumberMM(prices[0]));
        for (int i = 2; i - 1 < prices.length; i++) {
            message = message.replace("%price" + i + "%", StringUtils.formatPriceMM(prices[i - 1]));
            message = message.replace("%price-trim" + i + "%",
                    StringUtils.formatPriceMM(StringUtils.getPriceTrimmed(prices[i - 1])));
            message = message.replace("%number" + i + "%", StringUtils.formatNumberMM(prices[i - 1]));
        }
        return message;
    }

    public static String adventureApi(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Fix for &#HEX format - convert to MiniMessage format
        if (input.contains("&#")) {
            input = input.replaceAll("&#([0-9a-fA-F]{6})", "<color:#$1>");
        }

        try {
            // Parse as MiniMessage and convert directly to legacy (§) format
            Component comp = mm.deserialize(input);
            return legacy.serialize(comp);
        } catch (Exception e) {
            // If MiniMessage parsing fails, try to parse as legacy
            try {
                Component comp = legacy.deserialize(input);
                return legacy.serialize(comp);
            } catch (Exception ex) {
                // Last resort: just translate & codes
                return ChatColor.translateAlternateColorCodes('&', input);
            }
        }
    }

    /**
     * Get a Component from a message key (for direct Adventure sending)
     */
    public static Component getComponent(String key, String... replacements) {
        String message = getMM(key, replacements);

        // Fix for &#HEX format - convert to MiniMessage format
        if (message.contains("&#")) {
            message = message.replaceAll("&#([0-9a-fA-F]{6})", "<color:#$1>");
        }

        try {
            return mm.deserialize(message);
        } catch (Exception e) {
            // Fallback to legacy parsing
            try {
                return legacy.deserialize(message);
            } catch (Exception ex) {
                return Component.text(message);
            }
        }
    }

    /**
     * Get a Component from a message key with price
     */
    public static Component getComponent(String key, double price, String... replacements) {
        String message = getMM(key, price, replacements);

        // Fix for &#HEX format - convert to MiniMessage format
        if (message.contains("&#")) {
            message = message.replaceAll("&#([0-9a-fA-F]{6})", "<color:#$1>");
        }

        try {
            return mm.deserialize(message);
        } catch (Exception e) {
            // Fallback to legacy parsing
            try {
                return legacy.deserialize(message);
            } catch (Exception ex) {
                return Component.text(message);
            }
        }
    }

    /**
     * Send a formatted message to a player using the best available method
     * (Adventure on Paper, legacy on Spigot/Bukkit)
     */
    public static void sendMessage(Player player, String key, String... replacements) {
        player.sendMessage(getFormatted(key, replacements));
    }

    /**
     * Send a formatted message with price to a player
     */
    public static void sendMessage(Player player, String key, double price, String... replacements) {
        player.sendMessage(getFormatted(key, price, replacements));
    }
}
