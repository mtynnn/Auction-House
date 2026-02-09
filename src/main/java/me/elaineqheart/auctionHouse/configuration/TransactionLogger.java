package me.elaineqheart.auctionHouse.configuration;

import me.elaineqheart.auctionHouse.database.dao.TransactionDAO;

import java.util.UUID;

public class TransactionLogger {

    private final TransactionDAO dao;

    public TransactionLogger() {
        this.dao = new TransactionDAO();
    }

    public void loginTransaction(UUID buyer, UUID seller, String item, double price, int amount, boolean isBID) {
        logTransaction(buyer, seller, item, price, amount, isBID);
    }

    public void logTransaction(UUID buyer, UUID seller, String item, double price, int amount, boolean isBID) {
        // Store material type name for reliable price protection queries
        // Format: "MATERIAL_NAME x<amount>" (e.g. "DIAMOND x64")
        String itemName = item + " x" + amount;
        String type = isBID ? "BID" : "BIN";
        // Map Logger (Buyer, Seller) to DAO (Seller, Buyer)
        dao.logTransaction(seller, buyer, itemName, price, type);
    }

    /**
     * Get the average price-per-unit for a given material from transaction history.
     * 
     * @return double[]{avgPricePerUnit, saleCount}, or {-1, count} if not enough
     *         data
     */
    public TransactionDAO getDao() {
        return dao;
    }

    public String getName() {
        return "transactions";
    }

    public void setup(String name, boolean b, String path) {
        // No-op for compatibility
    }
}
