package dev.plex.futura.listener;

import dev.plex.futura.FuturaPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class ChatListener extends AbstractMinecraftListener {

    public ChatListener(FuturaPlugin plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        if (event.isCancelled()) return;

        final Player player = event.getPlayer();
        Component message = event.message();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(message);

        plugin.getBot().getDiscordChat().sendMessage(player.getName(), rawMessage);
    }
}
