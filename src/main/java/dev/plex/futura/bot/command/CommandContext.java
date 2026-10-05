package dev.plex.futura.bot.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public record CommandContext(SlashCommandInteractionEvent event) {

    public User user() {
        return event.getUser();
    }

    public Member member() {
        return event.getMember();
    }

    public Guild guild() {
        return event.getGuild();
    }

    public MessageChannel channel() {
        return event.getChannel();
    }

    public void reply(String message) {
        event.reply(message).queue();
    }

    public void replyEphemeral(String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    public void defer() {
        event.deferReply().queue();
    }

    public void deferEphemeral() {
        event.deferReply(true).queue();
    }
}
