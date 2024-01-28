package dev.plex.futura.listener;

import dev.plex.futura.Futura;
import dev.plex.futura.event.DiscordBridgeChatEvent;
import dev.plex.futura.util.FuturaLog;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class ChatListener extends ListenerAdapter implements Listener
{
    private final static MiniMessage MINI = MiniMessage.miniMessage();
    private final static PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event)
    {
        if (event.isCancelled())
        {
            return;
        }

    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event)
    {
        if (event.getAuthor().isBot())
        {
            return;
        }

        final String channelId = Futura.plugin().getConfig().getString("discord.chat", "");
        if (channelId.isEmpty())
        {
            return;
        }

        if (!event.getChannel().getId().equals(channelId))
        {
            return;
        }

        if (event.getMessage().toString().length() > 256)
        {
            // TODO: Add a reaction to the message in Discord to indicate it wasn't sent
            return;
        }

        final Member member = event.getMember();
        final Role role = member.getRoles().isEmpty() ? event.getGuild().getPublicRole() : member.getRoles().get(0);
        final String format = StringUtils.normalizeSpace(
                Futura.plugin().messages.getString("discordToMinecraft", "")
                        .replace("%role-color%", "<" + String.format("#%06x", role.getColor() != null ? role.getColor().getRGB() & 0xFFFFFF : 0xFFFFFF) + ">")
                        .replace("%role%", role.getName())
                        .replace("%username%", member.getUser().getName())
                        .replace("%message%", event.getMessage().getContentRaw())
        );

        Bukkit.getScheduler().runTask(Futura.plugin(), () ->
        {
            final DiscordBridgeChatEvent discordBridgeChatEvent = new DiscordBridgeChatEvent(event.getGuild(), member, event.getMessage(), DiscordBridgeChatEvent.BridgeType.DISCORD_TO_MINECRAFT);
            Bukkit.getPluginManager().callEvent(discordBridgeChatEvent);
            if (discordBridgeChatEvent.isCancelled())
            {
                return;
            }

            final Component component = MINI.deserialize(format);
            Bukkit.broadcast(component);
            try
            {
                event.getChannel().sendMessageEmbeds(
                        new EmbedBuilder().setColor(Color.GREEN).setDescription(PLAIN.serialize(component)).build()
                ).queue();
                event.getMessage().delete().queue(null, throwable ->
                {
                    FuturaLog.error("Couldn't delete user's message sent in Discord due to: " + throwable.getMessage());
                });
            }
            catch (InsufficientPermissionException e)
            {
                FuturaLog.error("Bot could not send embed in channel! Missing permission: " + e.getPermission().getName());
            }
        });

    }
}
