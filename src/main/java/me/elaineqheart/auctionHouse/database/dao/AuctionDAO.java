package me.elaineqheart.auctionHouse.database.dao;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.configuration.ConfigManager;
import me.elaineqheart.auctionHouse.model.Bid;
import me.elaineqheart.auctionHouse.database.DatabaseManager;
import me.elaineqheart.auctionHouse.manager.AuctionManager;
import me.elaineqheart.auctionHouse.model.AuctionItem;
import me.elaineqheart.auctionHouse.util.ItemStackConverter;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class AuctionDAO {

    private final DatabaseManager dbManager;

    public AuctionDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void loadAll() {
        System.out.println("[AuctionHouse-DEBUG] AuctionDAO.loadAll() starting...");
        // Migration Check
        backwardsCompatibility();
        File jsonFile = new File(AuctionHouse.getPlugin().getDataFolder(), "data/notes.json");
        if (jsonFile.exists()) {
            AuctionHouse.getPlugin().getLogger().info("Found legacy data. Starting migration to SQLite...");
            migrateFromJSON(jsonFile);
        } else {
            loadFromDatabase();
        }
    }

    private void migrateFromJSON(File jsonFile) {
        try {
            // 1. Load into RAM using inlined logic
            if (ConfigManager.backwardsCompatibility())
                backwardsCompatibility();

            if (jsonFile.exists()) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                try (java.io.Reader reader = new java.io.FileReader(jsonFile)) {
                    AuctionItem[] items = gson.fromJson(reader, AuctionItem[].class);
                    if (items != null) {
                        AuctionManager.getInstance().set(items);
                    }
                }
            }

            // 2. Save all RAM notes to Database, skipping corrupt items
            int migrated = 0;
            int skipped = 0;
            try (Connection conn = dbManager.getConnection()) {
                conn.setAutoCommit(false);
                for (AuctionItem note : AuctionManager.getInstance().getAll()) {
                    try {
                        saveSync(note, conn);
                        migrated++;
                    } catch (Exception e) {
                        skipped++;
                        AuctionHouse.getPlugin().getLogger().warning(
                                "Skipping corrupt auction item " + note.getNoteID() + ": " + e.getMessage());
                    }
                }
                conn.commit();
                AuctionHouse.getPlugin().getLogger().info(
                        "Migration: " + migrated + " items saved, " + skipped + " corrupt items skipped.");
            } catch (SQLException e) {
                AuctionHouse.getPlugin().getLogger().log(Level.SEVERE, "Failed to save migrated items to database!", e);
            }

            // 3. Rename old file to .bak
            File backup = new File(AuctionHouse.getPlugin().getDataFolder(), "data/notes.json.bak");
            if (jsonFile.renameTo(backup)) {
                AuctionHouse.getPlugin().getLogger()
                        .info("Migration successful! Legacy data backed up to notes.json.bak");
            } else {
                AuctionHouse.getPlugin().getLogger().warning("Migration completed but failed to rename notes.json!");
            }

        } catch (Exception e) {
            AuctionHouse.getPlugin().getLogger().log(Level.SEVERE, "Migration failed!", e);
        }
    }

    private void backwardsCompatibility() {
        File file = new File(AuctionHouse.getPlugin().getDataFolder(), "data/notes.json");
        File old = new File(AuctionHouse.getPlugin().getDataFolder(), "notes.json");
        if (!old.exists()) {
            old = new File(AuctionHouse.getPlugin().getDataFolder(), "notes.js");
        }
        if (old.exists()) {
            try {
                if (!file.getParentFile().exists())
                    file.getParentFile().mkdirs();
                if (!file.exists())
                    file.createNewFile();
                java.nio.file.Files.copy(old.getAbsoluteFile().toPath(), file.getAbsoluteFile().toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                old.delete();
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadFromDatabase() {
        AuctionManager.getInstance().clear();
        String query = "SELECT * FROM auctions";
        int count = 0;
        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(query);
                ResultSet rs = stmt.executeQuery()) {

            int active = 0;
            int expired = 0;
            int sold = 0;
            int adminHidden = 0;

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                String playerName = rs.getString("player_name");
                String itemData = rs.getString("item_data");
                double price = rs.getDouble("price");
                long creationDate = rs.getLong("creation_date");
                long auctionDuration = rs.getLong("auction_duration");
                boolean isBin = rs.getBoolean("is_bin");
                boolean isSold = rs.getBoolean("is_sold");
                int partiallySold = rs.getInt("partially_sold_amount");
                String adminMessage = rs.getString("admin_message");
                String buyerName = rs.getString("buyer_name");

                try {
                    AuctionItem note = new AuctionItem(id, playerUUID, playerName, itemData, price, creationDate,
                            auctionDuration,
                            isBin, isSold, partiallySold, adminMessage, buyerName);

                    loadBidsForNote(note, conn);
                    AuctionManager.getInstance().addQuietly(note);

                    if (note.isSold())
                        sold++;
                    else if (note.getAdminMessage() != null && !note.getAdminMessage().isEmpty())
                        adminHidden++;
                    else if (note.isExpired())
                        expired++;
                    else
                        active++;
                } catch (Exception e) {
                    System.err.println(
                            "[AuctionHouse-DEBUG] Failed to load auction ID: " + id + " due to " + e.getMessage());
                    // Corrupted item or missing dependency (like nova_structures), skipping to
                    // allow others to load
                }
                count++;
            }
            AuctionManager.getInstance().finalizeLoad();
            System.out.println("[AuctionHouse-DEBUG] Loaded " + count + " auctions. Breakdown:");
            System.out.println("[AuctionHouse-DEBUG]  - Active: " + active);
            System.out.println("[AuctionHouse-DEBUG]  - Expired: " + expired);
            System.out.println("[AuctionHouse-DEBUG]  - Sold: " + sold);
            System.out.println("[AuctionHouse-DEBUG]  - Admin Hidden: " + adminHidden);

        } catch (SQLException e) {
            System.err.println("[AuctionHouse-DEBUG] Failed to load auctions from database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadBidsForNote(AuctionItem note, Connection conn) {
        String query = "SELECT * FROM bids WHERE auction_id = ? ORDER BY timestamp ASC";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, note.getNoteID().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                    String playerName = rs.getString("player_name");
                    double amount = rs.getDouble("amount");
                    long timestamp = rs.getLong("timestamp");

                    Bid bid = new Bid(playerUUID, playerName, amount, timestamp);
                    note.getBidHistoryList().add(bid);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void save(AuctionItem note) {
        Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getPlugin(), () -> {
            try (Connection conn = dbManager.getConnection()) {
                saveSync(note, conn);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void saveSync(AuctionItem note, Connection conn) throws SQLException {
        String sql = "INSERT OR REPLACE INTO auctions (id, player_uuid, player_name, item_data, price, creation_date, auction_duration, is_bin, is_sold, partially_sold_amount, admin_message, buyer_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, note.getNoteID().toString());
            stmt.setString(2, note.getPlayerUUID().toString());
            stmt.setString(3, note.getPlayerName());
            stmt.setString(4, ItemStackConverter.encode(note.getItem()));
            stmt.setDouble(5, note.getPrice());
            stmt.setLong(6, note.getDateCreated().getTime());
            stmt.setLong(7, 0); // Need to store actual duration or logic? AuctionItem calculates expiry
                                // dynamically.
            stmt.setBoolean(8, note.isBIDAuction());
            stmt.setBoolean(9, note.isSold());
            stmt.setInt(10, note.getPartiallySoldAmountLeft());
            stmt.setString(11, note.getAdminMessage());
            stmt.setString(12, note.getBuyerName());
            stmt.executeUpdate();

            saveBidsSync(note, conn);
        }
    }

    private void saveBidsSync(AuctionItem note, Connection conn) throws SQLException {
        String deleteSql = "DELETE FROM bids WHERE auction_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setString(1, note.getNoteID().toString());
            stmt.executeUpdate();
        }

        String insertSql = "INSERT INTO bids (auction_id, player_uuid, player_name, amount, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (Bid bid : note.getBidHistoryList()) {
                stmt.setString(1, note.getNoteID().toString());
                stmt.setString(2, bid.getPlayerID().toString());
                stmt.setString(3, bid.getPlayerName());
                stmt.setDouble(4, bid.getPrice());
                stmt.setLong(5, bid.getTimestamp().getTime());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public void delete(AuctionItem note) {
        Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getPlugin(), () -> {
            String sql = "DELETE FROM auctions WHERE id = ?";
            try (Connection conn = dbManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, note.getNoteID().toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

    }

    public void purge() {
        Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getPlugin(), () -> {
            String sql = "DELETE FROM auctions"; // Cascade delete bids?
            // Safer to delete from bids first.
            String sqlBids = "DELETE FROM bids";

            try (Connection conn = dbManager.getConnection()) {
                try (PreparedStatement stmt = conn.prepareStatement(sqlBids)) {
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}
