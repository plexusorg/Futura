package dev.plex.futura.bot.listener;

import dev.plex.futura.Futura;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * @author Taah
 * @since 7:05 PM [02-08-2024]
 */
public class LinkingListener extends ListenerAdapter
{
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event)
    {
        if (event.getAuthor().isBot()) return;
        if (event.getChannelType() != ChannelType.PRIVATE) return;
        final String messageContent = event.getMessage().getContentRaw().toLowerCase();
        final UUID uuid = Futura.plugin().linkingManager().matchCode(messageContent);
        if (uuid == null)
        {
            // Invalid Code
            return;
        }

        //TODO: Code was valid, link user, store user in cache and future database
    }
}
