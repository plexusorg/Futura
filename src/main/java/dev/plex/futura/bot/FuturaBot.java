package dev.plex.futura.bot;

import dev.plex.futura.FuturaPlugin;
import dev.plex.futura.bot.chat.ChannelDiscordChat;
import dev.plex.futura.bot.chat.ChatType;
import dev.plex.futura.bot.chat.DiscordChat;
import dev.plex.futura.bot.chat.WebhookDiscordChat;
import dev.plex.futura.bot.listener.MessageListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.IncomingWebhookClient;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FuturaBot {

    private final JDA api;
    private final DiscordChat chat;
    private TextChannel chatChannel;
    private TextChannel consoleChannel;

    public FuturaBot(FuturaPlugin plugin) {
        String token = System.getenv("BOT_TOKEN");
        if (token == null) {
            throw new IllegalStateException("Missing environment variable BOT_TOKEN");
        }

        api = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(new MessageListener(plugin, this))
                .build();

        try {
            api.awaitReady();
        } catch (Exception e) {
            throw new IllegalStateException("Bot failed to start!", e);
        }

        EnumSet<Message.MentionType> disallowedMentions = EnumSet.of(Message.MentionType.EVERYONE, Message.MentionType.HERE, Message.MentionType.ROLE);
        MessageRequest.setDefaultMentions(EnumSet.complementOf(disallowedMentions));

        boolean consoleEnabled = plugin.getConfig().getBoolean("console.enabled");
        if (consoleEnabled) {
            String consoleChannelId = plugin.getConfig().getString("console.channel_id");
            String consoleFallbackName = plugin.getConfig().getString("console.fallback_name");

            consoleChannel = prepareTextChannel(consoleChannelId, consoleFallbackName);
        }

        String channelId = plugin.getConfig().getString("chat.channel_id");
        String fallbackChannelName = plugin.getConfig().getString("chat.fallback_name", "minecraft-chat");
        if ((channelId == null || channelId.isBlank()) && fallbackChannelName.isBlank()) {
            throw new IllegalStateException("Missing channel ID, this must be set for Discord -> Minecraft chat!");
        }

        if (channelId != null && !channelId.isBlank()) {
            chatChannel = api.getTextChannelById(channelId);
        }

        if (chatChannel == null) {
            List<TextChannel> channels = api.getTextChannelsByName(fallbackChannelName, true);
            if (channels.isEmpty()) {
                throw new IllegalStateException("Unable to find a valid channel");
            } else {
                chatChannel = channels.getFirst();
            }
        }

        ChatType chatType;
        try {
            chatType = ChatType.valueOf(plugin.getConfig().getString("chat.type", "CHANNEL").toUpperCase());
        } catch (Exception ignored) {
            chatType = ChatType.CHANNEL;
        }

        chat = switch (chatType) {
            case CHANNEL: {
                String chatMessageFormat = plugin.getConfig().getString("messages.discord.chat_message");
                yield new ChannelDiscordChat(chatChannel, chatMessageFormat);
            }
            case WEBHOOK: {
                String webhookUrl = System.getenv("WEBHOOK_URL");
                if (webhookUrl == null) {
                    throw new IllegalStateException("Missing environment variable WEBHOOK_URL");
                }

                yield new WebhookDiscordChat(prepareWebhook(webhookUrl));
            }
        };

        plugin.getLogger().info("The bot has been initialized.");
    }

    public boolean shutdown() throws InterruptedException {
        api.shutdown();
        if (!api.awaitShutdown(10, TimeUnit.SECONDS)) {
            api.shutdownNow();
            return false;
        }
        return true;
    }

    @Nullable
    public TextChannel prepareTextChannel(String channelId, String fallback) {
        TextChannel textChannel = api.getTextChannelById(channelId);

        if (chatChannel == null) {
            List<TextChannel> channels = api.getTextChannelsByName(fallback, true);
            if (channels.isEmpty()) {
                throw new IllegalStateException("Unable to find a valid channel");
            } else {
                textChannel = channels.getFirst();
            }
        }

        return textChannel;
    }

    public IncomingWebhookClient prepareWebhook(String url) {
        return WebhookClient.createClient(api, url);
    }

    public DiscordChat getDiscordChat() {
        return chat;
    }

    public TextChannel getChatChannel() {
        return chatChannel;
    }

    public TextChannel getConsoleChannel() {
        return consoleChannel;
    }

    public void addListener(Object listener) {
        api.addEventListener(listener);
    }
}
