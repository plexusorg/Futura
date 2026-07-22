package dev.plex.discordbridge.standalone;

import dev.plex.discordbridge.BridgeSettings;
import dev.plex.discordbridge.link.JdbiLinkRepository;
import dev.plex.discordbridge.link.DisabledLinkRepository;
import dev.plex.discordbridge.link.LinkRepository;
import dev.plex.discordbridge.link.LinkService;
import dev.plex.discordbridge.link.LegacyLinkImporter;
import dev.plex.discordbridge.platform.StandaloneBridgePlatform;
import dev.plex.discordbridge.service.DiscordBridgeService;
import java.io.IOException;
import java.sql.SQLException;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class StandaloneDiscordBridgePlugin extends JavaPlugin
{
    private StandaloneDatabase database;
    private DiscordBridgeService bridge;

    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        reloadConfig();
        BridgeSettings settings = BridgeSettings.from(getConfig());

        LinkRepository repository;
        try
        {
            if (settings.linking().enabled())
            {
                database = new StandaloneDatabase(this, getConfig());
                repository = new JdbiLinkRepository(database.jdbi(), StandaloneDatabase.LINKS_TABLE);
            }
            else
            {
                repository = new DisabledLinkRepository();
            }
        }
        catch (SQLException | IOException | RuntimeException exception)
        {
            getLogger().severe("Could not initialize the standalone database: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        StandaloneBridgePlatform platform = new StandaloneBridgePlatform(this);
        if (settings.linking().enabled())
        {
            LegacyLinkImporter.importIfPresent(platform, repository);
        }
        LinkService links = new LinkService(settings.linking(), repository);
        bridge = new DiscordBridgeService(platform, settings, links);
        getServer().getPluginManager().registerEvents(new StandaloneChatListener(bridge), this);

        StandaloneLinkCommands commands = new StandaloneLinkCommands(links, settings.linking());
        configureCommand("discordlink", commands);
        configureCommand("discordlinkadmin", commands);

        if (!settings.enabled())
        {
            getLogger().info("Discord bridge is disabled in config.yml");
        }
        else if (settings.token().isBlank())
        {
            getLogger().warning("Discord bridge was not started because bot.token is blank");
        }
        else
        {
            bridge.start();
        }
    }

    @Override
    public void onDisable()
    {
        if (bridge != null)
        {
            bridge.stop();
        }
        if (database != null)
        {
            database.close();
        }
    }

    private void configureCommand(String name, StandaloneLinkCommands commands)
    {
        PluginCommand command = getCommand(name);
        if (command == null)
        {
            throw new IllegalStateException("Missing command metadata for " + name);
        }
        command.setExecutor(commands);
        command.setTabCompleter(commands);
    }
}
