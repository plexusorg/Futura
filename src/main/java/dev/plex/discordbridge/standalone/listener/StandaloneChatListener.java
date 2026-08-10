package dev.plex.discordbridge.standalone.listener;

import dev.plex.discordbridge.common.service.DiscordBridgeService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class StandaloneChatListener implements Listener
{
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final DiscordBridgeService bridge;

    public StandaloneChatListener(DiscordBridgeService bridge)
    {
        this.bridge = bridge;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event)
    {
        bridge.sendPublicChat(event.getPlayer().getName(), PLAIN.serialize(event.message()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event)
    {
        bridge.sendPlayerJoined(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event)
    {
        bridge.sendPlayerLeft(event.getPlayer());
    }
}
