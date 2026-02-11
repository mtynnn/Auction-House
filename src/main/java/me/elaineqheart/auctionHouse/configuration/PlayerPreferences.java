package me.elaineqheart.auctionHouse.configuration;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.model.UserSession;
import me.elaineqheart.auctionHouse.database.dao.PlayerPreferencesDAO;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.UUID;

public class PlayerPreferences {

    private final PlayerPreferencesDAO dao;
    private final boolean defaultAnnounce = true;
    private final Gson gson;

    public PlayerPreferences() {
        this.dao = new PlayerPreferencesDAO();
        this.gson = getGson();
    }

    public boolean hasAnnouncementsEnabled(UUID player) {
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            return UserSession.getInstance(p).isAnnouncementsEnabled();
        }
        return defaultAnnounce;
    }

    public void setAnnouncementsEnabled(UUID player, boolean enabled) {
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            UserSession.getInstance(p).setAnnouncementsEnabled(enabled);
            // Changes are saved via saveInstance when needed, or we can trigger it here:
            saveInstance(player, UserSession.getInstance(p));
        }
    }

    public boolean toggleAnnouncements(Player player) {
        boolean current = hasAnnouncementsEnabled(player.getUniqueId());
        setAnnouncementsEnabled(player.getUniqueId(), !current);
        return !current;
    }

    public void saveInstance(UUID player, UserSession c) {
        if (c == null)
            return;
        String json = gson.toJson(c);
        dao.save(player, json);
    }

    /**
     * Synchronous save - use during plugin disable
     */
    public void saveInstanceSync(UUID player, UserSession c) {
        if (c == null)
            return;
        String json = gson.toJson(c);
        dao.saveSync(player, json);
    }

    public void loadInstance(Player p) {
        dao.load(p.getUniqueId()).thenAccept(json -> {
            if (json != null) {
                UserSession config = gson.fromJson(json, UserSession.class);
                // Ensure runs on main thread if UserSession.loadInstance touches Bukkit
                // API?
                // UserSession.loadInstance updates a HashMap.
                // HashMap is likely not thread-safe if accessed concurrently.
                // However, UserSessionListener uses it on join/quit (sync).
                // Here we are in async callback.
                // We should sync back to main thread to be safe.
                Bukkit.getScheduler().runTask(AuctionHouse.getPlugin(), () -> {
                    if (p.isOnline()) { // Check if player is still online
                        UserSession.loadInstance(p, config);
                    }
                });
            }
        });
    }

    // Renamed from setup to loadAll to match intention and avoid override if it
    // were extending something
    public void loadAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            loadInstance(p);
        }
    }

    public void disable() {
        // Use synchronous save during shutdown to avoid task registration errors
        for (Player p : Bukkit.getOnlinePlayers()) {
            saveInstanceSync(p.getUniqueId(), UserSession.getInstance(p));
        }
    }

    private Gson getGson() {
        return new GsonBuilder()
                .registerTypeAdapterFactory(new TypeAdapterFactory() {
                    @Override
                    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
                        TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);

                        return new TypeAdapter<>() {
                            @Override
                            public void write(JsonWriter out, T value) throws IOException {
                                JsonElement tree = delegate.toJsonTree(value);

                                if (tree.isJsonObject()) {
                                    JsonObject obj = tree.getAsJsonObject();
                                    obj.entrySet().removeIf(entry -> {
                                        JsonElement e = entry.getValue();
                                        if (!e.isJsonPrimitive())
                                            return false;
                                        JsonPrimitive p = e.getAsJsonPrimitive();
                                        if (p.isBoolean()) {
                                            return !p.getAsBoolean();
                                        }
                                        if (p.isNumber()) {
                                            return p.getAsNumber().doubleValue() == 0;
                                        }
                                        if (p.isString()) {
                                            return p.getAsString().isEmpty();
                                        }
                                        return false;
                                    });
                                }

                                elementAdapter.write(out, tree);
                            }

                            @Override
                            public T read(JsonReader in) throws IOException {
                                return delegate.read(in);
                            }
                        };
                    }
                })
                .create();
    }

}
