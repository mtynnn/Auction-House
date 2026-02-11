package me.elaineqheart.auctionHouse.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.configuration.ConfigManager;
import me.elaineqheart.auctionHouse.configuration.SettingManager;
import me.elaineqheart.auctionHouse.manager.AuctionManager;
import me.elaineqheart.auctionHouse.model.AuctionItem;
import me.elaineqheart.auctionHouse.model.UserSession;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for AuctionHouse
 * 
 * Available placeholders:
 * - %auctionhouse_notifications% - Si el jugador tiene activadas las notificaciones de venta (sí/no)
 * - %auctionhouse_notifications_enabled% - Lo mismo pero en inglés (yes/no)
 * - %auctionhouse_active_auctions% - Número de subastas activas del jugador
 * - %auctionhouse_max_auctions% - Límite máximo de subastas permitidas para el jugador
 * - %auctionhouse_auctions_left% - Cuántas subastas más puede crear el jugador
 * - %auctionhouse_total_auctions% - Total de subastas activas en el sistema
 * - %auctionhouse_total_expired% - Total de subastas expiradas en el sistema
 * - %auctionhouse_total_sold% - Total de subastas vendidas en el sistema
 * - %auctionhouse_is_admin% - Si el jugador tiene permisos de administrador (sí/no)
 */
public class AuctionHousePlaceholders extends PlaceholderExpansion {

    private final AuctionHouse plugin;

    public AuctionHousePlaceholders(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "auctionhouse";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Required for PlaceholderAPI to not unregister on reload
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        // Placeholders que no requieren que el jugador esté online
        switch (params.toLowerCase()) {
            case "total_auctions":
                long totalActive = AuctionManager.getInstance().getAll().stream()
                        .filter(a -> a.isOnAuction() && !a.isExpired())
                        .count();
                return String.valueOf(totalActive);

            case "total_expired":
                long totalExpired = AuctionManager.getInstance().getAll().stream()
                        .filter(AuctionItem::isExpired)
                        .count();
                return String.valueOf(totalExpired);

            case "total_sold":
                long totalSold = AuctionManager.getInstance().getAll().stream()
                        .filter(AuctionItem::isSold)
                        .count();
                return String.valueOf(totalSold);

            case "total_all":
                return String.valueOf(AuctionManager.getInstance().getAll().size());
        }

        // Placeholders que requieren jugador online
        if (offlinePlayer == null || !offlinePlayer.isOnline()) {
            return null;
        }

        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return null;
        }

        switch (params.toLowerCase()) {
            case "notifications":
            case "announcements":
                UserSession session = UserSession.getInstance(player);
                return session.isAnnouncementsEnabled() ? "sí" : "no";

            case "notifications_enabled":
            case "announcements_enabled":
                UserSession sessionEn = UserSession.getInstance(player);
                return sessionEn.isAnnouncementsEnabled() ? "yes" : "no";

            case "notifications_bool":
            case "announcements_bool":
                UserSession sessionBool = UserSession.getInstance(player);
                return String.valueOf(sessionBool.isAnnouncementsEnabled());

            case "active_auctions":
                int activeAuctions = AuctionManager.getInstance().getNumberOfAuctions(player.getUniqueId());
                return String.valueOf(activeAuctions);

            case "max_auctions":
                int maxAuctions = ConfigManager.permissions.getAuctionSlots(player);
                return String.valueOf(maxAuctions);

            case "auctions_left":
                int active = AuctionManager.getInstance().getNumberOfAuctions(player.getUniqueId());
                int max = ConfigManager.permissions.getAuctionSlots(player);
                int left = Math.max(0, max - active);
                return String.valueOf(left);

            case "is_admin":
                boolean isAdmin = player.hasPermission(SettingManager.permissionModerate);
                return isAdmin ? "sí" : "no";

            case "is_admin_bool":
                boolean isAdminBool = player.hasPermission(SettingManager.permissionModerate);
                return String.valueOf(isAdminBool);

            case "my_bids_count":
                int bidsCount = AuctionManager.getInstance().getMyBids(player.getUniqueId()).size();
                return String.valueOf(bidsCount);

            case "my_sold_items":
                long soldItems = AuctionManager.getInstance().getMySortedDateCreated(player.getUniqueId())
                        .stream()
                        .filter(AuctionItem::isSold)
                        .count();
                return String.valueOf(soldItems);

            case "my_expired_items":
                long expiredItems = AuctionManager.getInstance().getMySortedDateCreated(player.getUniqueId())
                        .stream()
                        .filter(note -> note.isExpired() && !note.isSold())
                        .count();
                return String.valueOf(expiredItems);

            default:
                return null;
        }
    }
}
