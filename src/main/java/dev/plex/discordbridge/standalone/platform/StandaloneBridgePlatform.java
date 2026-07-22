package dev.plex.discordbridge.standalone.platform;

import dev.plex.discordbridge.common.platform.BridgePlatform;
import java.io.File;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class StandaloneBridgePlatform implements BridgePlatform
{
    private final JavaPlugin plugin;

    public StandaloneBridgePlatform(JavaPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public File dataFolder()
    {
        return plugin.getDataFolder();
    }

    @Override
    public void info(String message, Object... arguments)
    {
        plugin.getLogger().info(format(message, arguments));
    }

    @Override
    public void warn(String message, Object... arguments)
    {
        plugin.getLogger().warning(format(message, arguments));
    }

    @Override
    public void error(String message, Object... arguments)
    {
        plugin.getLogger().severe(format(message, arguments));
    }

    @Override
    public void executeGlobal(Runnable task)
    {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void executeEntity(Player player, Runnable task)
    {
        player.getScheduler().execute(plugin, task, null, 1L);
    }

    @Override
    public void broadcast(Component message)
    {
        Bukkit.broadcast(message);
    }

    @Override
    public Component miniMessage(String input)
    {
        return MiniMessage.miniMessage().deserialize(input);
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

    private static String format(String message, Object... arguments)
    {
        return MessageFormat.format(message, arguments);
    }
}
