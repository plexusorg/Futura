package dev.plex.discordbridge.common.platform;

import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public interface BridgePlatform
{
    File dataFolder();

    void info(String message, Object... arguments);

    void warn(String message, Object... arguments);

    void error(String message, Object... arguments);

    void error(String message, Throwable throwable, Object... arguments);

    void executeGlobal(Runnable task);

    void executeAsync(Runnable task);

    void executeEntity(Player player, Runnable task);

    Optional<String> consoleCommandPermission(String command);

    boolean dispatchConsole(UUID identityId, String identityName, String command, Consumer<? super Component> feedback);

    void broadcast(Component message);

    Component miniMessage(String input);

    Collection<? extends Player> onlinePlayers();

    Optional<Player> onlinePlayer(UUID minecraftId);
}
