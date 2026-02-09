package me.elaineqheart.auctionHouse.listeners;

import me.elaineqheart.auctionHouse.AuctionHouse;

import me.elaineqheart.auctionHouse.configuration.SettingManager;
import me.elaineqheart.auctionHouse.configuration.M;
import me.elaineqheart.auctionHouse.manager.AuctionManager;
import me.elaineqheart.auctionHouse.model.AuctionItem;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinCollectListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!SettingManager.autoCollect)
            return;
        Bukkit.getScheduler().runTaskLater(AuctionHouse.getPlugin(), () -> {
            Player p = event.getPlayer();
            for (AuctionItem note : AuctionManager.getInstance().getMySortedDateCreated(p.getUniqueId()))
                sell(note, p);
        }, 1);

    }

    public static void sell(AuctionItem note, Player p) {
        if (!note.isSold() && !(note.isBIDAuction() && note.hasBidHistory() && note.isExpired()))
            return;
        int amount = note.getItem().getAmount() - note.getPartiallySoldAmountLeft();
        if (AuctionManager.getInstance().claimSoldItemMoney(p, note)
                && SettingManager.soldMessageEnabled)
            p.sendMessage(M.getFormatted("chat.sold-message.auto-collect", note.getSoldPrice(),
                    "%player%", note.getBuyerName(),
                    "%item%", note.getItemName(),
                    "%amount%", String.valueOf(amount)));
    }

}
