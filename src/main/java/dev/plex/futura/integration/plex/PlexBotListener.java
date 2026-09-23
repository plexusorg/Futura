package dev.plex.futura.integration.plex;

import dev.plex.futura.integration.IntegrationBotListener;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class PlexBotListener extends IntegrationBotListener {

    private final PlexIntegration integration;
    private final String messageFormat;
    private final boolean showReply;
    private final String replyMessageFormat;
    private final String prefix;

    public PlexBotListener(PlexIntegration integration) {
        super(integration);
        this.integration = integration;

        messageFormat = integration.getConfig().getString("messages.minecraft.message_format");
        showReply = integration.getConfig().getBoolean("messages.minecraft.reply");
        replyMessageFormat = integration.getConfig().getString("messages.minecraft.reply_message");
        prefix = integration.getConfig().getString("messages.minecraft.prefix");
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getChannel().asTextChannel() != integration.getDiscordBridge().getChatChannel() || event.getAuthor().isBot()) return;

        Message message = event.getMessage();

        Component replyMessage = null;
        if (showReply && message.getType() == MessageType.INLINE_REPLY) {
            Message referencedMessage = message.getReferencedMessage();
            if (referencedMessage != null) {
                replyMessage = MiniMessage.miniMessage().deserialize(replyMessageFormat,
                        Placeholder.unparsed("user", referencedMessage.getAuthor().getEffectiveName()));
            }
        }

        Component parsedPrefix = MiniMessage.miniMessage().deserialize(prefix);
        Component mcMessage = MiniMessage.miniMessage().deserialize(messageFormat,
                Placeholder.unparsed("message", message.getContentRaw()),
                replyMessage != null ? Placeholder.component("reply", replyMessage) : Placeholder.unparsed("reply", ""));

        integration.getApi().messages().sendAdminChat(event.getAuthor().getEffectiveName(), parsedPrefix, mcMessage);
    }
}
