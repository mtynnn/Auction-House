package me.elaineqheart.auctionHouse.listeners;

import me.elaineqheart.auctionHouse.configuration.ConfigManager;
import me.elaineqheart.auctionHouse.model.UserSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class UserSessionListener implements Listener {

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        ;
        ConfigManager.playerPreferences.saveInstance(p.getUniqueId(), UserSession.getInstance(p));
        UserSession.removeInstance(p);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        ConfigManager.playerPreferences.loadInstance(p);
    }

}
