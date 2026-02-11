package me.elaineqheart.auctionHouse.database.dao;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.database.DatabaseManager;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TransactionDAO {

    public static class Transaction {
        public final UUID sellerUUID;
        public final UUID buyerUUID;
        public final String itemName;
        public final double price;
        public final long date;
        public final String type;
        public final boolean isSeller;

        public Transaction(UUID sellerUUID, UUID buyerUUID, String itemName, double price, long date, String type, boolean isSeller) {
            this.sellerUUID = sellerUUID;
            this.buyerUUID = buyerUUID;
            this.itemName = itemName;
            this.price = price;
            this.date = date;
            this.type = type;
            this.isSeller = isSeller;
        }
    }

    /**
     * Resolves DatabaseManager lazily to avoid NPE when constructed before DB init.
     */
    private DatabaseManager getDbManager() {
        return AuctionHouse.getPlugin().getDatabaseManager();
    }

    public TransactionDAO() {
    }

    /** @deprecated Use no-arg constructor. DB manager is resolved lazily. */
    @Deprecated
    public TransactionDAO(DatabaseManager ignored) {
    }

    public void logTransaction(UUID seller, UUID buyer, String itemName, double price, String type) {
        Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getPlugin(), () -> {
            DatabaseManager db = getDbManager();
            if (db == null)
                return;
            String sql = "INSERT INTO transactions (seller_uuid, buyer_uuid, item_name, price, date, type) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, seller != null ? seller.toString() : null);
                stmt.setString(2, buyer != null ? buyer.toString() : null);
                stmt.setString(3, itemName);
                stmt.setDouble(4, price);
                stmt.setLong(5, System.currentTimeMillis());
                stmt.setString(6, type);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Get the average price-per-unit for a given material from transaction history.
     * Returns -1 if there are fewer than minSales records.
     *
     * Item names are stored as "MATERIAL_NAME x<amount>" (e.g. "DIAMOND x64").
     * We extract the amount from the name to calculate price-per-unit.
     */
    public CompletableFuture<double[]> getAveragePricePerUnit(String materialName, int minSales) {
        CompletableFuture<double[]> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getPlugin(), () -> {
            DatabaseManager db = getDbManager();
            if (db == null) {
                future.complete(new double[] { -1, 0 });
                return;
            }
            // Query: get average price-per-unit for items matching this material
            // item_name format: "Material Name x<amount>"
            // We use SUBSTR + INSTR to extract the amount after " x"
            String sql = "SELECT " +
                    "AVG(price / MAX(CAST(SUBSTR(item_name, INSTR(item_name, ' x') + 2) AS REAL), 1)) as avg_ppu, " +
                    "COUNT(*) as sale_count " +
                    "FROM transactions " +
                    "WHERE item_name LIKE ? || ' x%'";
            try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, materialName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt("sale_count");
                        double avgPPU = rs.getDouble("avg_ppu");
                        if (count < minSales) {
                            future.complete(new double[] { -1, count });
                        } else {
                            future.complete(new double[] { avgPPU, count });
                        }
                    } else {
                        future.complete(new double[] { -1, 0 });
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                future.complete(new double[] { -1, 0 });
            }
        });
        return future;
    }

    /**
     * Get recent transactions for a player (as seller or buyer)
     * @param playerUUID UUID of the player
     * @param limit Maximum number of transactions to return
     * @return CompletableFuture with list of transactions
     */
    public CompletableFuture<List<Transaction>> getRecentTransactions(UUID playerUUID, int limit) {
        CompletableFuture<List<Transaction>> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(AuctionHouse.getPlugin(), () -> {
            DatabaseManager db = getDbManager();
            if (db == null) {
                future.complete(new ArrayList<>());
                return;
            }
            List<Transaction> transactions = new ArrayList<>();
            String sql = "SELECT seller_uuid, buyer_uuid, item_name, price, date, type " +
                         "FROM transactions " +
                         "WHERE seller_uuid = ? OR buyer_uuid = ? " +
                         "ORDER BY date DESC " +
                         "LIMIT ?";
            try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, playerUUID.toString());
                stmt.setInt(3, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String sellerStr = rs.getString("seller_uuid");
                        String buyerStr = rs.getString("buyer_uuid");
                        UUID seller = sellerStr != null ? UUID.fromString(sellerStr) : null;
                        UUID buyer = buyerStr != null ? UUID.fromString(buyerStr) : null;
                        String itemName = rs.getString("item_name");
                        double price = rs.getDouble("price");
                        long date = rs.getLong("date");
                        String type = rs.getString("type");
                        boolean isSeller = playerUUID.equals(seller);
                        transactions.add(new Transaction(seller, buyer, itemName, price, date, type, isSeller));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            future.complete(transactions);
        });
        return future;
    }
}
