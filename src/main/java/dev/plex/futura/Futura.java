package dev.plex.futura;

import dev.plex.futura.bot.BotHandler;
import dev.plex.futura.listener.ChatListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class Futura extends JavaPlugin {
    private static Futura plugin;

    private YamlConfiguration messages;

    private BotHandler botHandler;

    @Override
    public void onLoad() {
        plugin = this;

        if (!this.getDataFolder().exists()) this.getDataFolder().mkdir();

        this.getConfig().options().copyDefaults(true);
        saveConfig();

        final File messagesFile = new File(this.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            this.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        botHandler = new BotHandler();
    }

    @Override
    public void onEnable() {
        final ChatListener chatListener = new ChatListener();
        this.getServer().getPluginManager().registerEvents(chatListener, this);
        if (this.botHandler.ready()) {
            this.botHandler.jda().addEventListener(chatListener);
        }
    }

    @Override
    public void onDisable() {
    }

    public static Futura plugin() {
        return plugin;
    }

    public FileConfiguration messages()
    {
        return this.messages;
    }

    public BotHandler botHandler()
    {
        return this.botHandler;
    }
}
