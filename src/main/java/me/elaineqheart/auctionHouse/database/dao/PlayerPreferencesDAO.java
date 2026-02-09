package me.elaineqheart.auctionHouse.database.dao;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.database.DatabaseManager;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerPreferencesDAO {

    /**
     * Resolves DatabaseManager lazily to avoid NPE when constructed before DB init.
     */
    private DatabaseManager getDbManager() {
        return AuctionHouse.getPlugin().getDatabaseManager();
    }

    public PlayerPreferencesDAO() {
    }

    /** @deprecated Use no-arg constructor. DB manager is resolved lazily. */
    @Deprecated
    public PlayerPreferencesDAO(DatabaseManager ignored) {
    }

    public void save(UUID uuid, String json) {
        Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getPlugin(), () -> {
            DatabaseManager db = getDbManager();
            if (db == null)
                return; // DB not ready yet, skip silently
            String sql = "INSERT OR REPLACE INTO player_preferences (player_uuid, data) VALUES (?, ?)";
            try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, json);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public CompletableFuture<String> load(UUID uuid) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getPlugin(), () -> {
            DatabaseManager db = getDbManager();
            if (db == null) {
                future.complete(null); // DB not ready yet
                return;
            }
            String sql = "SELECT data FROM player_preferences WHERE player_uuid = ?";
            try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        future.complete(rs.getString("data"));
                    } else {
                        future.complete(null);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                future.complete(null);
            }
        });
        return future;
    }
}
