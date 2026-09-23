package dev.plex.futura.listener;

import dev.plex.futura.FuturaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class LifetimeListener extends AbstractMinecraftListener {

    private final boolean showJoinQuitMessage;
    private final String joinMessage;
    private final String quitMessage;

    public LifetimeListener(FuturaPlugin plugin) {
        super(plugin);

        showJoinQuitMessage = plugin.getConfig().getBoolean("messages.discord.join_quit_messages");
        joinMessage = plugin.getConfig().getString("messages.discord.join_message");
        quitMessage = plugin.getConfig().getString("messages.discord.quit_message");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        if (showJoinQuitMessage) {
            Component parsedMessage = MiniMessage.miniMessage().deserialize(joinMessage, Placeholder.unparsed("name", player.getName()));
            String stringFormat = PlainTextComponentSerializer.plainText().serialize(parsedMessage);
            plugin.getBot().getChatChannel().sendMessage(stringFormat).queue();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();

        if (showJoinQuitMessage) {
            Component parsedMessage = MiniMessage.miniMessage().deserialize(quitMessage, Placeholder.unparsed("name", player.getName()));
            String stringFormat = PlainTextComponentSerializer.plainText().serialize(parsedMessage);
            plugin.getBot().getChatChannel().sendMessage(stringFormat).queue();
        }
    }
}
