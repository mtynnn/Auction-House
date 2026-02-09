package me.elaineqheart.auctionHouse.database.dao;

import com.google.gson.Gson;
import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.configuration.BannedPlayers;
import me.elaineqheart.auctionHouse.configuration.Blacklist;

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

    private final Gson gson = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(java.util.Optional.class, new com.google.gson.TypeAdapter<java.util.Optional<?>>() {
                @Override
                public void write(com.google.gson.stream.JsonWriter out, java.util.Optional<?> value)
                        throws java.io.IOException {
                    if (value == null || !value.isPresent()) {
                        out.nullValue();
                    } else {
                        new Gson().toJson(value.get(), value.get().getClass(), out);
                    }
                }

                @Override
                public java.util.Optional<?> read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                    return java.util.Optional.ofNullable(new Gson().fromJson(in, Object.class));
                }
            })
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
                    } else if ("BLACKLIST".equals(type)) {
                        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>() {
                        }.getType();
                        List<Map<String, Object>> blacklist = gson.fromJson(params, listType);
                        Blacklist.setBlacklist(blacklist);
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
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
