package me.elaineqheart.auctionHouse.configuration;

import me.elaineqheart.auctionHouse.configuration.SettingManager;
import me.elaineqheart.auctionHouse.configuration.Config;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class Permissions extends Config {

    public int getAuctionSlots(Player player) {
        int slots = SettingManager.defaultMaxAuctions;
        ConfigurationSection section = getCustomFile().getConfigurationSection("auction-slots");
        if (section == null) return slots;
        for (String key : section.getKeys(true)) {
            if (player.hasPermission(key)) {
                int newSlots = section.getInt(key, slots);
                if(newSlots > slots) slots = newSlots;
            }
        }
        return slots;
    }

    public long getAuctionDuration(Player player, boolean BID) {
        long duration = BID ? SettingManager.BIDAuctionDuration : SettingManager.BINAuctionDuration;
        ConfigurationSection section = getCustomFile().getConfigurationSection(BID ? "bid-auction-duration" : "bin-auction-duration");
        if (section == null) return duration;
        for (String key : section.getKeys(true)) {
            if (player.hasPermission(key)) {
                long newDuration = section.getLong(key, duration);
                if(newDuration > duration) duration = newDuration;
            }
        }
        return duration;
    }

}
