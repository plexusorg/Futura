package dev.plex.discordbridge.module.listener;

import dev.plex.discordbridge.common.service.DiscordBridgeService;
import dev.plex.api.event.StaffChatMessageEvent;
import dev.plex.listener.PlexListener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlexChatListener extends PlexListener
{
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final DiscordBridgeService bridge;

    public PlexChatListener(DiscordBridgeService bridge)
    {
        this.bridge = bridge;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event)
    {
        String player = event.getPlayer().getName();
        String message = PLAIN.serialize(event.message());
        bridge.sendPublicChat(player, message);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStaffChat(StaffChatMessageEvent event)
    {
        bridge.sendStaffChat(event.getSender().getName(), PLAIN.serialize(event.getMessage()));
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
