package me.elaineqheart.auctionHouse.database.dao;

import com.google.gson.Gson;
import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.configuration.BannedPlayers;
import me.elaineqheart.auctionHouse.configuration.Blacklist;
import me.elaineqheart.auctionHouse.util.Debug;

import me.elaineqheart.auctionHouse.database.DatabaseManager;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ServerDataDAO {

    private static class OptionalAdapter<T> extends com.google.gson.TypeAdapter<java.util.Optional<T>> {
        private final Gson gson = new Gson();

        @Override
        public void write(com.google.gson.stream.JsonWriter out, java.util.Optional<T> value)
                throws java.io.IOException {
            if (value == null || !value.isPresent()) {
                out.nullValue();
            } else {
                gson.toJson(value.get(), value.get().getClass(), out);
            }
        }

        @Override
        public java.util.Optional<T> read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
            return java.util.Optional.ofNullable(gson.fromJson(in, Object.class));
        }
    }

    private final Gson gson = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(java.util.Optional.class, new OptionalAdapter<>())
            .create();

    /**
     * Resolves DatabaseManager lazily to avoid NPE when constructed before DB init.
     */
    private DatabaseManager getDbManager() {
        return AuctionHouse.getPlugin().getDatabaseManager();
    }

    public ServerDataDAO() {
    }

    /** @deprecated Use no-arg constructor. DB manager is resolved lazily. */
    @Deprecated
    public ServerDataDAO(DatabaseManager ignored) {
    }

    public void loadAll() {
        Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getPlugin(), () -> {
            DatabaseManager db = getDbManager();
            if (db == null) {
                AuctionHouse.getPlugin().getLogger().warning("ServerDataDAO: Database not ready, skipping loadAll().");
                return;
            }
            try (Connection conn = db.getConnection();
                    PreparedStatement stmt = conn.prepareStatement("SELECT * FROM server_data")) {

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String type = rs.getString("type");
                    String params = rs.getString("params");

                    if ("BANNED_PLAYERS".equals(type)) {
                        java.lang.reflect.Type mapType = new com.google.gson.reflect.TypeToken<java.util.Map<UUID, BannedPlayers.BanEntry>>() {
                        }.getType();
                        Map<UUID, BannedPlayers.BanEntry> bans = gson.fromJson(params, mapType);
                        BannedPlayers.setBans(bans);
                        Debug.log("Loaded banned players: " + (bans == null ? 0 : bans.size()));
                    } else if ("BLACKLIST".equals(type)) {
                        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>() {
                        }.getType();
                        List<Map<String, Object>> blacklist = gson.fromJson(params, listType);
                        Blacklist.setBlacklist(blacklist);
                        Debug.log("Loaded blacklist entries: " + (blacklist == null ? 0 : blacklist.size()));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void saveBannedPlayers(Map<UUID, BannedPlayers.BanEntry> bannedPlayers) {
        Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getPlugin(), () -> {
            String json = gson.toJson(bannedPlayers);
            saveData("BANNED_PLAYERS", json);
        });
    }

    public void saveBlacklist(List<Map<String, Object>> blacklist) {
        Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getPlugin(), () -> {
            String json = gson.toJson(blacklist);
            saveData("BLACKLIST", json);
        });
    }

    private void saveData(String type, String params) {
        DatabaseManager db = getDbManager();
        if (db == null) {
            AuctionHouse.getPlugin().getLogger()
                    .warning("ServerDataDAO: Database not ready, skipping save for " + type);
            return;
        }
        String sql = "INSERT OR REPLACE INTO server_data (type, params) VALUES (?, ?)";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, type);
            stmt.setString(2, params);
            stmt.executeUpdate();
            Debug.log("Saved server_data type=" + type + " bytes=" + (params == null ? 0 : params.length()));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
