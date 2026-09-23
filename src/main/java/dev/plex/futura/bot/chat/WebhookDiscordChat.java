package dev.plex.futura.bot.chat;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.IncomingWebhookClient;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.WebhookClient;

import java.util.EnumSet;

public class WebhookDiscordChat implements DiscordChat {

    private final IncomingWebhookClient chatWebhook;

    public WebhookDiscordChat(IncomingWebhookClient client) throws IllegalArgumentException {
        this.chatWebhook = client;
    }

    @Override
    public void sendMessage(String username, String message) {
        chatWebhook.sendMessage(message)
                .setAllowedMentions(EnumSet.of(Message.MentionType.USER))
                .setUsername(username)
                .setAvatarUrl(String.format("https://mc-heads.net/avatar/%s.png", username))
                .queue();
    }
}
