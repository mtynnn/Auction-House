package me.elaineqheart.auctionHouse.configuration;

import me.elaineqheart.auctionHouse.util.StringUtils;
import me.elaineqheart.auctionHouse.configuration.Config;
import me.elaineqheart.auctionHouse.configuration.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.List;

public class M extends Config {

    private static final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
    private static final MiniMessage mm = MiniMessage.miniMessage();

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
        String message = getValue(key, true);
        message = replacePlaceholders(key, message, replacements);
        return adventureApi(message);
    }

    public static String getFormatted(String key, double price, String... replacements) {
        String message = getValue(key, true);
        message = replacePlaceholders(key, message, replacements);
        message = replace(message, price);
        return adventureApi(message);
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
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }

    public static String replace(String message, double... prices) {
        message = message.replace("%price%", StringUtils.formatPrice(prices[0]));
        message = message.replace("%price-trim%", StringUtils.formatPrice(StringUtils.getPriceTrimmed(prices[0])));
        message = message.replace("%number%", StringUtils.formatNumber(prices[0]));
        for (int i = 2; i - 1 < prices.length; i++) {
            message = message.replace("%price" + i + "%", StringUtils.formatPrice(prices[i - 1]));
            message = message.replace("%price-trim" + i + "%",
                    StringUtils.formatPrice(StringUtils.getPriceTrimmed(prices[i - 1])));
            message = message.replace("%number" + i + "%", StringUtils.formatNumber(prices[i - 1]));
        }
        return message;
    }

    private static String adventureApi(String input) {
        // Fix for &#HEX format
        if (input.contains("&#")) {
            input = input.replaceAll("&#([0-9a-fA-F]{6})", "<color:#$1>");
        }
        Component comp;
        try {
            comp = mm.deserialize(input);
        } catch (Exception e) {
            comp = legacy.deserialize(input);
        }
        return legacy.serialize(comp);
    }

}
