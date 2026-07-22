package dev.plex.discordbridge;

import dev.plex.api.config.ModuleConfiguration;
import dev.plex.discordbridge.command.DiscordLinkAdminCommand;
import dev.plex.discordbridge.command.DiscordLinkCommand;
import dev.plex.discordbridge.link.JdbiLinkRepository;
import dev.plex.discordbridge.link.DisabledLinkRepository;
import dev.plex.discordbridge.link.LinkService;
import dev.plex.discordbridge.link.LegacyLinkImporter;
import dev.plex.discordbridge.listener.PlexChatListener;
import dev.plex.discordbridge.platform.PlexBridgePlatform;
import dev.plex.discordbridge.service.DiscordBridgeService;
import dev.plex.module.PlexModule;
import dev.plex.api.storage.ModuleStorage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

public final class DiscordBridgeModule extends PlexModule
{
    private ModuleConfiguration configuration;
    private BridgeSettings settings;
    private DiscordBridgeService bridge;
    private LinkService links;

    @Override
    public void load()
    {
        recoverPlexConfigCollision();
        configuration = api().moduleConfigs().create(this, "config.yml", "config.yml");
        configuration.load();
        settings = BridgeSettings.from(configuration);
    }

    private void recoverPlexConfigCollision()
    {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.isFile())
        {
            return;
        }

        YamlConfiguration existing = YamlConfiguration.loadConfiguration(configFile);
        boolean looksLikePlexConfig = !existing.contains("bot")
                && existing.contains("server")
                && existing.contains("banning")
                && existing.contains("data.db");
        if (!looksLikePlexConfig)
        {
            return;
        }

        Path backup = configFile.toPath().resolveSibling("config.yml.plex-backup");
        if (Files.exists(backup))
        {
            backup = configFile.toPath().resolveSibling("config.yml.plex-backup-" + System.currentTimeMillis());
        }

        try
        {
            Files.move(configFile.toPath(), backup);
            api().logging().warn(
                    "Backed up a Plex config copied by an older Plex resource collision to {0}",
                    backup.getFileName());
        }
        catch (IOException exception)
        {
            api().logging().error(
                    "Could not back up the incorrect module config; move or delete {0} and restart: {1}",
                    configFile.getAbsolutePath(),
                    exception.getMessage());
        }
    }

    @Override
    public void enable()
    {
        PlexBridgePlatform platform = new PlexBridgePlatform(this);
        try
        {
            if (settings.linking().enabled())
            {
                ModuleStorage storage = api().storage().forModule(this);
                storage.migrations().run(List.of("001_links"));
                JdbiLinkRepository repository = new JdbiLinkRepository(storage.jdbi(), storage.table("links"));
                LegacyLinkImporter.importIfPresent(platform, repository);
                links = new LinkService(settings.linking(), repository);
            }
            else
            {
                links = new LinkService(settings.linking(), new DisabledLinkRepository());
            }
            bridge = new DiscordBridgeService(platform, settings, links);
        }
        catch (SQLException | RuntimeException exception)
        {
            api().logging().error("Could not initialize Discord account-link storage: {0}", exception.getMessage());
            return;
        }

        registerListener(new PlexChatListener(bridge));
        if (settings.linking().enabled())
        {
            registerCommand(new DiscordLinkCommand(links, settings.linking()));
            registerCommand(new DiscordLinkAdminCommand(links, settings.linking()));
        }

        if (!settings.enabled())
        {
            api().logging().info("Discord bridge is disabled in config.yml");
            return;
        }
        if (settings.token().isBlank())
        {
            api().logging().warn("Discord bridge was not started because bot.token is blank");
            return;
        }

        bridge.start();
    }

    @Override
    public void disable()
    {
        if (bridge != null)
        {
            bridge.stop();
        }
    }
}
