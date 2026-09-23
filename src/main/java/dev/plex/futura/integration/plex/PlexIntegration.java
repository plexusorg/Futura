package dev.plex.futura.integration.plex;

import dev.plex.api.PlexApi;
import dev.plex.futura.FuturaPlugin;
import dev.plex.futura.integration.Integration;
import dev.plex.futura.integration.IntegrationBotListener;
import dev.plex.futura.integration.IntegrationConfig;
import org.bukkit.event.HandlerList;

public class PlexIntegration implements Integration {

    private final FuturaPlugin plugin;
    private final IntegrationConfig config;
    private PlexApi api;
    private PlexDiscordBridge discordBridge;
    private PlexListener listener;
    private PlexBotListener botListener;

    public PlexIntegration(FuturaPlugin plugin) {
        this.plugin = plugin;
        this.config = new IntegrationConfig(plugin, "plex.yml");
    }

    @Override
    public String name() {
        return "Plex";
    }

    @Override
    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled("Plex");
    }

    @Override
    public boolean enable() {
        if (!isAvailable()) {
            return false;
        }

        api = plugin.getServer().getServicesManager().load(PlexApi.class);
        if (api == null) {
            plugin.getLogger().severe("Unable to load Plex API... Are you using the correct version of Plex?");
            return false;
        }

        config.load();

        discordBridge = new PlexDiscordBridge(plugin, this);

        listener = new PlexListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        botListener = new PlexBotListener(this);

        plugin.getLogger().info("Enabled Plex integration.");
        return true;
    }

    @Override
    public void disable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }

        config.save();
    }

    @Override
    public PlexBotListener getBotListener() {
        return botListener;
    }

    public IntegrationConfig getConfig() {
        return config;
    }

    public PlexDiscordBridge getDiscordBridge() {
        return discordBridge;
    }

    public PlexApi getApi() {
        if (!isAvailable()) {
            return null;
        }

        return api;
    }
}
