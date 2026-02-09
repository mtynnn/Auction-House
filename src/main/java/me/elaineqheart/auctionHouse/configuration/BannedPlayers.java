package me.elaineqheart.auctionHouse.configuration;

import me.elaineqheart.auctionHouse.util.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BannedPlayers {

    public static class BanEntry {
        public long endTimestamp;
        public String playerName;
        public String reason;

        public BanEntry(long endTimestamp, String playerName, String reason) {
            this.endTimestamp = endTimestamp;
            this.playerName = playerName;
            this.reason = reason;
        }
    }

    private static final Map<UUID, BanEntry> bannedPlayers = new ConcurrentHashMap<>();

    public static void setBans(Map<UUID, BanEntry> bans) {
        bannedPlayers.clear();
        if (bans != null)
            bannedPlayers.putAll(bans);
    }

    public static Map<UUID, BanEntry> getBans() {
        return bannedPlayers;
    }

    public void saveBannedPlayer(Player p, int durationInDays, String reason) {
        int timeInMillis = durationInDays * 24 * 60 * 60 * 1000;
        long banEndDate = new Date().getTime() + timeInMillis;

        BanEntry entry = new BanEntry(banEndDate, p.getName(), reason);
        bannedPlayers.put(p.getUniqueId(), entry);

        save();
    }

    // if the player is banned, send them a message
    public boolean checkIsBannedSendMessage(Player p) {
        if (!bannedPlayers.containsKey(p.getUniqueId()))
            return false;

        BanEntry entry = bannedPlayers.get(p.getUniqueId());
        long currentTime = new Date().getTime();

        if (currentTime > entry.endTimestamp) {
            bannedPlayers.remove(p.getUniqueId());
            save();
            return false;
        }

        long banDuration = entry.endTimestamp - currentTime;
        p.sendMessage(ChatColor.WHITE + "You are temporarily banned for " + ChatColor.YELLOW
                + StringUtils.getTime(banDuration / 1000, true)
                + ChatColor.WHITE + " from the auction house.");
        p.sendMessage(ChatColor.GRAY + "Reason: " + entry.reason);
        return true;
    }

    private void save() {
        new me.elaineqheart.auctionHouse.database.dao.ServerDataDAO()
                .saveBannedPlayers(bannedPlayers);
    }
}
