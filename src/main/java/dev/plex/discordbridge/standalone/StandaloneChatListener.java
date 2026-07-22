package dev.plex.discordbridge.standalone;

import dev.plex.discordbridge.service.DiscordBridgeService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

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
}
