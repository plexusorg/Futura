package dev.plex.discordbridge.platform;

import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public interface BridgePlatform
{
    File dataFolder();

    void info(String message, Object... arguments);

    void warn(String message, Object... arguments);

    void error(String message, Object... arguments);

    void executeGlobal(Runnable task);

    void executeEntity(Player player, Runnable task);

    void broadcast(Component message);

    Component miniMessage(String input);

    Collection<? extends Player> onlinePlayers();

    Optional<Player> onlinePlayer(UUID minecraftId);
}
