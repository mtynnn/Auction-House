package me.elaineqheart.auctionHouse.configuration;

import com.google.common.base.Charsets;
import me.elaineqheart.auctionHouse.AuctionHouse;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class Config {

    private File file;
    private FileConfiguration customFile;
    private String fileName;
    private boolean copyDefaults;
    private String parent;

    public void setup(String fileName, boolean copyDefaults, String parent) {
        this.fileName = fileName;
        this.copyDefaults = copyDefaults;
        this.parent = parent;
        file = new File(AuctionHouse.getPlugin().getDataFolder() + parent, fileName);

        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                // uwu
            }
        }
        if (ConfigManager.backwardsCompatibility() && !parent.isEmpty())
            backwardsCompatibility(fileName, parent);
        try {
            customFile = YamlConfiguration.loadConfiguration(file);
        } catch (IllegalArgumentException | IllegalStateException e) {
            AuctionHouse.getPlugin().getLogger().severe("Error loading " + fileName + ": " + e.getMessage());
            AuctionHouse.getPlugin().getLogger().severe("The file appears to be corrupted or contains invalid data.");

            try {
                File broken = new File(file.getParentFile(), fileName + ".broken." + System.currentTimeMillis());
                Files.move(file.toPath(), broken.toPath(), StandardCopyOption.REPLACE_EXISTING);
                AuctionHouse.getPlugin().getLogger()
                        .severe("Renamed broken file to " + broken.getName() + " and creating a new one.");
            } catch (IOException ex) {
                AuctionHouse.getPlugin().getLogger().severe("Failed to rename broken file! Using empty config.");
                ex.printStackTrace();
            }

            customFile = new YamlConfiguration();
        } catch (Exception e) {
            AuctionHouse.getPlugin().getLogger().severe("Unexpected error loading " + fileName + ": " + e.getMessage());
            e.printStackTrace();
            customFile = new YamlConfiguration();
        }

        if (copyDefaults) {
            final InputStream defConfigStream = AuctionHouse.getPlugin().getResource(parent + fileName);
            if (defConfigStream != null) {
                customFile.setDefaults(
                        YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, Charsets.UTF_8)));
                customFile.options().copyDefaults(true);
            }
        }

        save();
        setup();
    }

    public void setup() {
    }

    public FileConfiguration getCustomFile() {
        return customFile;
    }

    public File getFile() {
        return file;
    }

    public void save() {
        try {
            customFile.save(file);
        } catch (IOException e) {
            AuctionHouse.getPlugin().getLogger().severe("Couldn't save " + fileName + " file");
        }
    }

    public void reload() {
        customFile = YamlConfiguration.loadConfiguration(file);

        if (copyDefaults) {
            final InputStream defConfigStream = AuctionHouse.getPlugin().getResource(parent + fileName);
            if (defConfigStream != null) {
                customFile.setDefaults(
                        YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, Charsets.UTF_8)));
                customFile.options().copyDefaults(true);
                save(); // ensure new keys are written during /ah reload or PlugMan reload
            }
        }
        reloadChild();
    }

    public void reloadChild() {
    }

    private void backwardsCompatibility(String fileName, String parent) {
        File file = new File(AuctionHouse.getPlugin().getDataFolder().getAbsolutePath() + parent + "/" + fileName);
        File old = new File(AuctionHouse.getPlugin().getDataFolder().getAbsolutePath() + "/" + fileName);
        if (old.exists()) {

            try {
                Files.copy(old.getAbsoluteFile().toPath(), file.getAbsoluteFile().toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                old.delete();
            } catch (IOException ignored) {
            }
        }
    }

}
