package dev.plex.futura.integration.plex;

import dev.plex.futura.FuturaPlugin;
import dev.plex.futura.bot.chat.ChannelDiscordChat;
import dev.plex.futura.bot.chat.ChatType;
import dev.plex.futura.bot.chat.DiscordChat;
import dev.plex.futura.bot.chat.WebhookDiscordChat;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class PlexDiscordBridge {

    private final PlexIntegration integration;
    private final DiscordChat chat;
    private final TextChannel chatChannel;

    public PlexDiscordBridge(FuturaPlugin plugin, PlexIntegration integration) {
        this.integration = integration;

        String channelId = integration.getConfig().getString("chat.channel_id");
        String fallbackChannelName = integration.getConfig().getString("chat.fallback_name", "minecraft-chat");
        if ((channelId == null || channelId.isBlank()) && fallbackChannelName.isBlank()) {
            throw new IllegalStateException("Missing channel ID, this must be set for Discord -> Minecraft chat!");
        }

        chatChannel = plugin.getBot().prepareTextChannel(channelId, fallbackChannelName);
        ChatType chatType;
        try {
            chatType = ChatType.valueOf(integration.getConfig().getString("chat.type", "CHANNEL").toUpperCase());
        } catch (Exception ignored) {
            chatType = ChatType.CHANNEL;
        }

        chat = switch (chatType) {
            case CHANNEL: {
                String chatMessageFormat = integration.getConfig().getString("messages.discord.chat_message");
                yield new ChannelDiscordChat(chatChannel, chatMessageFormat);
            }
            case WEBHOOK: {
                String webhookUrl = System.getenv("PLEX_STAFF_CHAT_WEBHOOK_URL");
                if (webhookUrl == null) {
                    throw new IllegalStateException("Missing environment variable PLEX_STAFF_CHAT_WEBHOOK_URL");
                }

                yield new WebhookDiscordChat(plugin.getBot().prepareWebhook(webhookUrl));
            }
        };
    }

    public DiscordChat getDiscordChat() {
        return chat;
    }

    public TextChannel getChatChannel() {
        return chatChannel;
    }
}
