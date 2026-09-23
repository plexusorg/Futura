package dev.plex.futura.integration.plex;

import dev.plex.api.event.StaffChatMessageEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Objects;

public class PlexListener implements Listener {

    private final PlexIntegration integration;

    public PlexListener(PlexIntegration integration) {
        this.integration = integration;
    }

    @EventHandler
    public void onStaffChatMessage(StaffChatMessageEvent event) {
        if (event.isCancelled() || event.getSource() == StaffChatMessageEvent.Source.API) return;

        final CommandSender sender = Objects.requireNonNull(event.getSender());
        Component message = event.getMessage();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(message);

        integration.getDiscordBridge().getDiscordChat().sendMessage(sender.getName(), rawMessage);
    }
}
