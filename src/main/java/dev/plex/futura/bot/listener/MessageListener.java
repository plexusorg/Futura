package dev.plex.futura.bot.listener;

import dev.plex.futura.FuturaPlugin;
import dev.plex.futura.bot.FuturaBot;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.List;

public class MessageListener extends AbstractDiscordListener {

    private final String chatMessageFormat;
    private final boolean showReply;
    private final String replyMessageFormat;

    public MessageListener(FuturaPlugin plugin, FuturaBot bot) {
        super(plugin, bot);

        chatMessageFormat = plugin.getConfig().getString("messages.minecraft.chat_message");
        showReply = plugin.getConfig().getBoolean("messages.minecraft.reply");
        replyMessageFormat = plugin.getConfig().getString("messages.minecraft.reply_message");
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getChannel().asTextChannel() != bot.getChatChannel() || event.getAuthor().isBot()) return;

        Message message = event.getMessage();

        Component replyMessage = null;
        if (showReply && message.getType() == MessageType.INLINE_REPLY) {
            Message referencedMessage = message.getReferencedMessage();
            if (referencedMessage != null) {
                replyMessage = MiniMessage.miniMessage().deserialize(replyMessageFormat,
                        Placeholder.unparsed("user", referencedMessage.getAuthor().getEffectiveName()));
            }
        }

        String role = "";
        if (event.getMember() != null) {
            List<Role> roles = event.getMember().getRoles();
            role = roles.getFirst().getName();
        }

        Component mcMessage = MiniMessage.miniMessage().deserialize(chatMessageFormat,
                Placeholder.unparsed("user", event.getAuthor().getEffectiveName()),
                Placeholder.unparsed("role", role),
                Placeholder.unparsed("message", message.getContentRaw()),
                replyMessage != null ? Placeholder.component("reply", replyMessage) : Placeholder.unparsed("reply", ""));

        plugin.getServer().broadcast(mcMessage);
    }
}
