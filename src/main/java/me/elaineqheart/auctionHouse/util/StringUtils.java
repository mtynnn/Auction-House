package me.elaineqheart.auctionHouse.util;

import me.elaineqheart.auctionHouse.configuration.SettingManager;
import me.elaineqheart.auctionHouse.configuration.M;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.Objects;

public class StringUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    /**
     * Colorize a string using MiniMessage format.
     * Supports MiniMessage tags like <color:#FFD180>, <bold>, <gradient>, etc.
     * Also converts legacy & codes to § for backwards compatibility.
     * 
     * @param text The text to colorize (MiniMessage format)
     * @return The colorized text as a legacy string for Bukkit compatibility
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty())
            return text;

        try {
            // Parse MiniMessage and convert to legacy format for Bukkit
            Component component = MINI_MESSAGE.deserialize(text);
            return LEGACY_SERIALIZER.serialize(component);
        } catch (Exception e) {
            // Fallback: just translate & codes if MiniMessage fails
            return ChatColor.translateAlternateColorCodes('&', text);
        }
    }

    /**
     * Get a Component from MiniMessage text (for Adventure-native APIs)
     * 
     * @param text The MiniMessage format text
     * @return The Adventure Component
     */
    public static Component toComponent(String text) {
        if (text == null || text.isEmpty())
            return Component.empty();
        return MINI_MESSAGE.deserialize(text);
    }

    public static String getTime(Long seconds, boolean convertDays) {
        // Handle infinite duration (-1)
        if (seconds == null || seconds < 0) {
            return colorize("<color:#FFD180>" + SettingManager.timeUnitNever + "</color>");
        }

        long days = seconds / (24 * 3600);
        long hours = (seconds % (24 * 3600)) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder result = new StringBuilder();

        if (convertDays && days > 0) {
            String dayUnit = days == 1 ? SettingManager.timeUnitDay : SettingManager.timeUnitDays;
            String hourUnit = hours == 1 ? SettingManager.timeUnitHour : SettingManager.timeUnitHours;
            result.append(days).append(" ").append(dayUnit).append(" ")
                    .append(hours).append(" ").append(hourUnit); // 1d 1h
        } else if (hours > 0 || (convertDays && days > 0)) { // Fallback if convertDays is false but hours > 24
            // If convertDays is false, hours will contain total hours.
            // But here hours is calculated via modulo.
            // If convertDays is false, we should use total hours.
            long totalHours = seconds / 3600;
            if (!convertDays) {
                hours = totalHours;
                // If not converting days, we show Hours + Mins? Or just Hours?
                // Original code showed H M S.
                // Let's assume user wants H M for < 24h context, or H M if > 24h but
                // convertDays=false?
                // If convertDays=false, typically we show H:M:S?
                // But for adaptive, let's stick to H M.
                String hourUnit = hours == 1 ? SettingManager.timeUnitHour : SettingManager.timeUnitHours;
                String minUnit = minutes == 1 ? SettingManager.timeUnitMinute : SettingManager.timeUnitMinutes;
                result.append(hours).append(" ").append(hourUnit).append(" ")
                        .append(minutes).append(" ").append(minUnit);
            } else {
                // Days = 0, Hours > 0
                String hourUnit = hours == 1 ? SettingManager.timeUnitHour : SettingManager.timeUnitHours;
                String minUnit = minutes == 1 ? SettingManager.timeUnitMinute : SettingManager.timeUnitMinutes;
                result.append(hours).append(" ").append(hourUnit).append(" ")
                        .append(minutes).append(" ").append(minUnit);
            }
        } else {
            // Hours = 0, Days = 0
            String minUnit = minutes == 1 ? SettingManager.timeUnitMinute : SettingManager.timeUnitMinutes;
            String secUnit = secs == 1 ? SettingManager.timeUnitSecond : SettingManager.timeUnitSeconds;
            result.append(minutes).append(" ").append(minUnit).append(" ")
                    .append(secs).append(" ").append(secUnit);
        }

        return colorize("<yellow>" + result.toString().trim() + "</yellow>");
    }

    public static String getTimeTrimmed(long seconds) {
        // Handle infinite duration (-1)
        if (seconds < 0) {
            return SettingManager.timeUnitNever;
        }

        if (seconds < 60) {
            String unit = seconds == 1 ? SettingManager.timeUnitSecond : SettingManager.timeUnitSeconds;
            return seconds + " " + unit;
        } else if (seconds < 60 * 60) {
            int min = (int) (seconds / 60);
            String unit = min == 1 ? SettingManager.timeUnitMinute : SettingManager.timeUnitMinutes;
            return min + " " + unit;
        } else if (seconds < 60 * 60 * 24) {
            int hours = (int) (seconds / 60 / 60);
            String unit = hours == 1 ? SettingManager.timeUnitHour : SettingManager.timeUnitHours;
            return hours + " " + unit;
        } else {
            int days = (int) (seconds / 60 / 60 / 24);
            String unit = days == 1 ? SettingManager.timeUnitDay : SettingManager.timeUnitDays;
            return days + " " + unit;
        }
    }

    public static String formatNumber(double number) {
        return M.getFormatted("placeholders.number", "%input%", formatNumberPlain(number));
    }

    public static String formatNumberPlain(double number) {
        if (Double.isInfinite(number) || Double.isNaN(number)) {
            return "---";
        }
        // fallback for async threads
        DecimalFormat fmt = Objects.requireNonNullElseGet(SettingManager.formatter,
                () -> new DecimalFormat(M.getFormatted("placeholders.format-numbers")));
        return fmt.format(number);
    }

    public static String formatNumber(String number) {
        return M.getFormatted("placeholders.number", "%input%", number);
    }

    public static String formatPrice(double price) {
        return M.getFormatted("placeholders.price",
                "%number%", formatNumber(price),
                "%currency-symbol%", M.getFormatted("placeholders.currency-symbol"));
    }

    public static String formatPrice(String price) {
        return M.getFormatted("placeholders.price",
                "%number%", formatNumber(price),
                "%currency-symbol%", M.getFormatted("placeholders.currency-symbol"));
    }

    public static String getItemName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Air";
        }
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return ChatColor.RESET.toString() + ChatColor.ITALIC.toString() + item.getItemMeta().getDisplayName();
        }

        // Fallback to type name
        String name = item.getType().name().replace("_", " ").toLowerCase();
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return ChatColor.RESET + sb.toString().trim();
    }

    public static double parsePositiveNumber(String input) {
        try {
            double price = Double.parseDouble(input);
            if (Double.isInfinite(price) || Double.isNaN(price))
                return -1;

            price = Math.max(price, 0);
            if (price % 1 != 0)
                throw new RuntimeException();
            return price;
        } catch (Exception e) {
            try {
                double price = Double.parseDouble(input.substring(0, input.length() - 1));
                if (Double.isInfinite(price) || Double.isNaN(price))
                    return -1;

                String suffix = input.substring(input.length() - 1).toLowerCase();
                switch (suffix) {
                    case "k":
                        price *= 1000;
                        break;
                    case "m":
                        price *= 1000000;
                        break;
                    default:
                        return -1;
                }
                if (Double.isInfinite(price) || Double.isNaN(price))
                    return -1;

                if (price % 1 != 0)
                    throw new RuntimeException();
                return Math.max(price, 0);
            } catch (Exception f) {
                return -1;
            }
        }
    }

    public static String getPriceTrimmed(double price) {
        if (price < 1000) {
            return String.valueOf(price);
        } else if (price < 1000000) {
            return String.format("%.1fK", price / 1000.0);
        } else if (price < 1000000000) {
            return String.format("%.1fM", price / 1000000.0);
        } else {
            return String.format("%.1fB", price / 1000000000.0);
        }
    }

}
