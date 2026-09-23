package dev.plex.futura.bot.chat;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.EnumSet;


public class ChannelDiscordChat implements DiscordChat {

    private final TextChannel channel;
    private final String chatMessageFormat;

    public ChannelDiscordChat(TextChannel channel, String chatMessageFormat) {
        this.channel = channel;
        this.chatMessageFormat = chatMessageFormat;
    }

    @Override
    public void sendMessage(String username, String message) {
        Component messageFormat = MiniMessage.miniMessage().deserialize(chatMessageFormat, Placeholder.unparsed("name", username), Placeholder.unparsed("message", message));
        String stringFormat = PlainTextComponentSerializer.plainText().serialize(messageFormat);

        channel.sendMessage(stringFormat)
                .setAllowedMentions(EnumSet.of(Message.MentionType.USER))
                .queue();
    }
}
