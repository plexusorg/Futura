package dev.plex.discordbridge.module.platform;

import dev.plex.discordbridge.common.platform.BridgePlatform;
import dev.plex.discordbridge.module.DiscordBridgeModule;
import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PlexBridgePlatform implements BridgePlatform
{
    private final DiscordBridgeModule module;

    public PlexBridgePlatform(DiscordBridgeModule module)
    {
        this.module = module;
    }

    @Override
    public File dataFolder()
    {
        return module.getDataFolder();
    }

    @Override
    public void info(String message, Object... arguments)
    {
        module.api().logging().info(message, arguments);
    }

    @Override
    public void warn(String message, Object... arguments)
    {
        module.api().logging().warn(message, arguments);
    }

    @Override
    public void error(String message, Object... arguments)
    {
        module.api().logging().error(message, arguments);
    }

    @Override
    public void executeGlobal(Runnable task)
    {
        module.api().scheduler().executeGlobal(task);
    }

    @Override
    public void executeAsync(Runnable task)
    {
        module.api().scheduler().runAsync(task);
    }

    @Override
    public void executeEntity(Player player, Runnable task)
    {
        module.api().scheduler().executeEntity(player, task, null, 1L);
    }

    @Override
    public void broadcast(Component message)
    {
        module.api().messages().broadcast(message);
    }

    @Override
    public Component miniMessage(String input)
    {
        return module.api().messages().miniMessage(input);
    }

    @Override
    public Collection<? extends Player> onlinePlayers()
    {
        return Bukkit.getOnlinePlayers();
    }

    @Override
    public Optional<Player> onlinePlayer(UUID minecraftId)
    {
        return Optional.ofNullable(Bukkit.getPlayer(minecraftId));
    }
}
