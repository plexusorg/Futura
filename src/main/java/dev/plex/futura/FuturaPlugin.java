package dev.plex.futura;

import dev.plex.futura.bot.FuturaBot;
import dev.plex.futura.cache.PlayerHeadCache;
import dev.plex.futura.console.ConsoleBridge;
import dev.plex.futura.console.ConsoleLogManager;
import dev.plex.futura.integration.IntegrationManager;
import dev.plex.futura.listener.ChatListener;
import dev.plex.futura.listener.LifetimeListener;
import dev.plex.futura.service.PlayerHeadService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.logging.Logger;

public class FuturaPlugin extends JavaPlugin {

    private static FuturaPlugin plugin;
    private FuturaBot bot;
    private ConsoleBridge consoleBridge;
    private ConsoleLogManager consoleLogManager;
    private IntegrationManager integrationManager;
    private PlayerHeadService playerHeadService;

    private boolean logServerStatus = false;
    private String shutdownServerMessage;

    @Override
    public void onLoad() {
        plugin = this;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            getLogger().info("Starting bot...");
            bot = new FuturaBot(this);
        } catch (Exception ex) {
            getLogger().severe("unable to initialize Futura bot: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        logServerStatus = getConfig().getBoolean("messages.discord.online_offline_messages");
        String startServerMessage = getConfig().getString("messages.discord.online_message");
        shutdownServerMessage = getConfig().getString("messages.discord.offline_message");

        if (logServerStatus && startServerMessage != null && !startServerMessage.isBlank()) {
            bot.getChatChannel().sendMessage(startServerMessage).queue();
        }

        consoleBridge = new ConsoleBridge(this);
        consoleBridge.start();

        consoleLogManager = new ConsoleLogManager(consoleBridge);
        consoleLogManager.start();

        new ChatListener(this);
        new LifetimeListener(this);

        integrationManager = new IntegrationManager(this);
        integrationManager.load();
        integrationManager.injectBotListeners();

        PlayerHeadCache playerHeadCache = new PlayerHeadCache(this);
        try {
            playerHeadCache.initialize();
        } catch (IOException ex) {
            getLogger().severe("unable to initialize player head cache: " + ex.getMessage());
        }

        playerHeadService = new PlayerHeadService(this, playerHeadCache);

        getLogger().info("Futura has been started.");
    }

    @Override
    public void onDisable() {
        if (logServerStatus && shutdownServerMessage != null && !shutdownServerMessage.isBlank()) {
            bot.getChatChannel().sendMessage(shutdownServerMessage).queue();
        }

        integrationManager.unload();

        if (consoleLogManager != null) {
            consoleLogManager.stop();
        }

        if (consoleBridge != null) {
            consoleBridge.shutdown();
        }

        if (bot != null) {
            try {
                if (bot.shutdown()) {
                    getLogger().info("Futura has been shutdown.");
                } else {
                    getLogger().warning("Unable to gracefully shutdown Futura bot - forcing shutdown.");
                }
            } catch (Exception ex) {
                getLogger().severe("unable to shut down Futura Bot properly: " + ex.getMessage());
            }
        }

        plugin = null;
    }

    public FuturaBot getBot() {
        return bot;
    }

    public PlayerHeadService getPlayerHeadService() {
        return playerHeadService;
    }
}
