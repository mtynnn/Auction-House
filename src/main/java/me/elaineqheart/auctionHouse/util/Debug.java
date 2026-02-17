package me.elaineqheart.auctionHouse.util;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.configuration.SettingManager;

public final class Debug {

    private Debug() {
    }

    public static boolean isEnabled() {
        return SettingManager.debugEnabled;
    }

    public static void log(String message) {
        if (!isEnabled() || message == null) {
            return;
        }
        AuctionHouse.getPlugin().getLogger().info("[Debug] " + message);
    }

    public static void warn(String message) {
        if (!isEnabled() || message == null) {
            return;
        }
        AuctionHouse.getPlugin().getLogger().warning("[Debug] " + message);
    }

    public static void error(String message, Throwable throwable) {
        if (!isEnabled()) {
            return;
        }
        if (message != null) {
            AuctionHouse.getPlugin().getLogger().severe("[Debug] " + message);
        }
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
}

