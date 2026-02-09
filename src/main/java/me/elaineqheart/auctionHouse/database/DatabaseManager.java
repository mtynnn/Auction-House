package me.elaineqheart.auctionHouse.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.elaineqheart.auctionHouse.AuctionHouse;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private HikariDataSource dataSource;
    private final AuctionHouse plugin;

    public DatabaseManager(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (!setupDataSource()) {
            plugin.getLogger().severe("Could not connect to SQLite database! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(plugin);
            return;
        }

        // Configure SQLite PRAGMAs for containerized environments (Pterodactyl/Docker)
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA temp_store=MEMORY");
            stmt.execute("PRAGMA busy_timeout=5000");
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not set SQLite PRAGMAs: " + e.getMessage());
        }

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Auctions Table
            stmt.execute("CREATE TABLE IF NOT EXISTS auctions (" +
                    "id TEXT PRIMARY KEY, " +
                    "player_uuid TEXT, " +
                    "player_name TEXT, " +
                    "item_data TEXT, " +
                    "price REAL, " +
                    "creation_date INTEGER, " +
                    "auction_duration INTEGER, " +
                    "is_bin BOOLEAN, " +
                    "is_sold BOOLEAN, " +
                    "partially_sold_amount INTEGER, " +
                    "admin_message TEXT, " +
                    "buyer_name TEXT" +
                    ");");

            // Bids Table
            stmt.execute("CREATE TABLE IF NOT EXISTS bids (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "auction_id TEXT, " +
                    "player_uuid TEXT, " +
                    "player_name TEXT, " +
                    "amount REAL, " +
                    "timestamp INTEGER, " +
                    "FOREIGN KEY(auction_id) REFERENCES auctions(id) ON DELETE CASCADE" +
                    ");");

            // Transactions Table
            stmt.execute("CREATE TABLE IF NOT EXISTS transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "seller_uuid TEXT, " +
                    "buyer_uuid TEXT, " +
                    "item_name TEXT, " +
                    "price REAL, " +
                    "date INTEGER, " +
                    "type TEXT" +
                    ");");

            // Server Data (Banned Players, Blacklist, etc.)
            stmt.execute("CREATE TABLE IF NOT EXISTS server_data (" +
                    "type TEXT PRIMARY KEY, " +
                    "params TEXT" +
                    ");");

            // Player Preferences
            stmt.execute("CREATE TABLE IF NOT EXISTS player_preferences (" +
                    "player_uuid TEXT PRIMARY KEY, " +
                    "data TEXT" +
                    ");");

        } catch (SQLException e) {
            e.printStackTrace();
            plugin.getLogger().severe("Could not initialize database tables!");
            Bukkit.getPluginManager().disablePlugin(plugin);
        }

        validateSchema();
    }

    private void validateSchema() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Check player_preferences for legacy column name
            try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "player_preferences",
                    "preferences_json")) {
                if (rs.next()) {
                    plugin.getLogger().info("Migrating player_preferences column 'preferences_json' to 'data'...");
                    stmt.execute("ALTER TABLE player_preferences RENAME COLUMN preferences_json TO data;");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to validate schema or migrate columns: " + e.getMessage());
        }
    }

    private boolean setupDataSource() {
        try {
            File dataFolder = new File(plugin.getDataFolder(), "data");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            // Verify directory is writable (Pterodactyl/Docker may restrict permissions)
            if (!dataFolder.canWrite()) {
                plugin.getLogger().warning("Database directory is not writable: " + dataFolder.getAbsolutePath());
                plugin.getLogger().warning("Attempting to set writable...");
                dataFolder.setWritable(true);
            }

            File dbFile = new File(dataFolder, "auctionhouse.db");
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(10);
            config.setConnectionTimeout(30000); // 30 seconds

            // Use WAL journal mode on each new connection to avoid
            // SQLITE_READONLY_DIRECTORY errors in containerized environments
            config.setConnectionInitSql("PRAGMA journal_mode=WAL");

            // SQLite optimizations
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
