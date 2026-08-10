package dev.plex.discordbridge.module.platform;

import dev.plex.discordbridge.common.platform.BridgePlatform;
import dev.plex.discordbridge.module.DiscordBridgeModule;
import dev.plex.command.PlexCommand;
import java.io.File;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
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
    public void error(String message, Throwable throwable, Object... arguments)
    {
        module.getLogger().error(MessageFormat.format(message, arguments), throwable);
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
    public Optional<String> consoleCommandPermission(String command)
    {
        String commandLabel = commandLabel(command);
        int namespaceSeparator = commandLabel.indexOf(':');
        String namespace = namespaceSeparator < 0 ? "" : commandLabel.substring(0, namespaceSeparator);
        String plainLabel = namespaceSeparator < 0 ? commandLabel : commandLabel.substring(namespaceSeparator + 1);

        if (namespace.isBlank() || namespace.equalsIgnoreCase("plex"))
        {
            Optional<String> plexPermission = module.api().commands().registeredCommands().stream()
                    .filter(plexCommand -> matches(plexCommand, plainLabel))
                    .map(PlexCommand::getPermission)
                    .filter(permission -> !permission.isBlank())
                    .findFirst();
            if (plexPermission.isPresent())
            {
                return plexPermission;
            }
        }
        return bukkitCommandPermission(commandLabel);
    }

    @Override
    public boolean dispatchConsole(UUID identityId, String identityName, String command, Consumer<? super Component> feedback)
    {
        return module.api().commands().dispatchAsConsole(identityId, identityName, command, feedback);
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

    private static boolean matches(PlexCommand command, String label)
    {
        return command.getName().equalsIgnoreCase(label)
                || command.getAliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(label));
    }

    private static Optional<String> bukkitCommandPermission(String label)
    {
        Command command = Bukkit.getCommandMap().getCommand(label);
        if (command == null || command.getPermission() == null || command.getPermission().isBlank())
        {
            return Optional.empty();
        }
        return Optional.of(command.getPermission());
    }

    private static String commandLabel(String command)
    {
        return command.stripLeading().split("\\s+", 2)[0];
    }
}
