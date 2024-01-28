package dev.plex.futura;

import dev.plex.futura.bot.BotHandler;
import dev.plex.futura.config.Config;
import dev.plex.futura.listener.ChatListener;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class Futura extends JavaPlugin
{
    private static Futura plugin;

    private BotHandler botHandler;

    public Config config;
    public Config messages;

    @Override
    public void onLoad()
    {
        super.onLoad();
        plugin = this;
        config = new Config(this, "config.yml");
        messages = new Config(this, "messages.yml");

        final File messagesFile = new File(this.getDataFolder(), "messages.yml");
        if (!messagesFile.exists())
        {
            this.saveResource("messages.yml", false);
        }
        botHandler = new BotHandler();
    }

    @Override
    public void onEnable()
    {
        config.load();
        messages.load();
        final ChatListener chatListener = new ChatListener();
        this.getServer().getPluginManager().registerEvents(chatListener, this);
        if (this.botHandler.ready())
        {
            this.botHandler.jda().addEventListener(chatListener);
        }

        // Metrics @ https://bstats.org/plugin/bukkit/Futura/20848
        Metrics metrics = new Metrics(this, 20848);
    }

    @Override
    public void onDisable()
    {
    }

    public static Futura plugin()
    {
        return plugin;
    }

    public BotHandler botHandler()
    {
        return this.botHandler;
    }
}
