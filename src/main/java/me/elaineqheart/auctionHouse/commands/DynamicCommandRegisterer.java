package me.elaineqheart.auctionHouse.commands;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.configuration.M;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

import java.lang.reflect.Field;
import java.util.List;

public class DynamicCommandRegisterer {

    public static void init() {
        String name = M.getFormatted("command-names.ah").toLowerCase();
        String alias = M.getFormatted("command-names.alias").toLowerCase();
        List<String> aliases = alias.isEmpty() ? List.of() : List.of(alias);
        CommandExecutor executor = new AuctionHouseCommand();
        TabCompleter tabCompleter = new AuctionHouseCommand();

        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            CommandMap commandMap = (CommandMap) f.get(Bukkit.getServer());

            PluginCommand existing = AuctionHouse.getPlugin().getServer().getPluginCommand(name);
            if (existing != null)
                existing.unregister(commandMap);

            commandMap.register("auctionhouse", new DynamicCommand(name,
                    "opens the auction house GUI", aliases, executor, tabCompleter));
        } catch (Exception e) {
            throw new RuntimeException("Failed to register dynamic command: " + e.getMessage());
        }
    }

}
