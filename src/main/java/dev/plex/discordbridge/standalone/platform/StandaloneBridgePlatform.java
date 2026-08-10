package dev.plex.discordbridge.standalone.platform;

import dev.plex.discordbridge.common.platform.BridgePlatform;
import java.io.File;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
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
    public void error(String message, Throwable throwable, Object... arguments)
    {
        plugin.getLogger().log(Level.SEVERE, format(message, arguments), throwable);
    }

    @Override
    public void executeGlobal(Runnable task)
    {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void executeAsync(Runnable task)
    {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    @Override
    public void executeEntity(Player player, Runnable task)
    {
        player.getScheduler().execute(plugin, task, null, 1L);
    }

    @Override
    public Optional<String> consoleCommandPermission(String commandLine)
    {
        String label = commandLine.stripLeading().split("\\s+", 2)[0];
        Command command = Bukkit.getCommandMap().getCommand(label);
        if (command == null || command.getPermission() == null || command.getPermission().isBlank())
        {
            return Optional.empty();
        }
        return Optional.of(command.getPermission());
    }

    @Override
    public boolean dispatchConsole(UUID identityId, String identityName, String command, Consumer<? super Component> feedback)
    {
        return Bukkit.dispatchCommand(Bukkit.createCommandSender(feedback), command);
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
