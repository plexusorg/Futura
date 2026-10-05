package dev.plex.futura.bot.command.impl;

import dev.plex.futura.bot.FuturaBot;
import dev.plex.futura.bot.chat.ChatType;
import dev.plex.futura.bot.command.CommandContext;
import dev.plex.futura.bot.command.Option;
import dev.plex.futura.bot.command.SlashCommand;
import dev.plex.futura.bot.command.Subcommand;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class PurgeCommand extends SlashCommand {

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)?[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z]{2,})+(?:/[^\\s<>]*)?"
    );

    private final FuturaBot bot;

    public PurgeCommand(FuturaBot bot) {
        super("purge", "Bulk deletes messages based on the condition provided (default search limit is 100)");
        this.bot = bot;
    }

    @Subcommand(name = "contains", description = "deletes messages that contains a text string")
    public void contains(CommandContext context, @Option(name = "text", description = "string to match") String text, @Option(name = "search", description = "Number of recent messages to search", required = false) Integer search) {
        int limit = search != null ? search : 100;
        context.deferEphemeral();

        purge(context.channel(), limit, message -> message.getContentRaw().toLowerCase().contains(text.toLowerCase())).thenAccept(result -> context.event().getHook().editOriginal("Deleted " + result + " messages.").queue());
    }

    @Subcommand(name = "user", description = "deletes messages from a specified Discord user")
    public void user(CommandContext context, @Option(name = "user", description = "Specifies an user to delete") User user, @Option(name = "search", description = "Number of recent messages to delete", required = false) Integer search) {
        int limit = search != null ? search : 100;
        context.deferEphemeral();

        purge(context.channel(), limit, message -> message.getAuthor().equals(user)).thenAccept(result -> context.event().getHook().editOriginal("Deleted " + result + " messages.").queue());
    }

    @Subcommand(name = "links", description = "deletes message that contains a link")
    public void links(CommandContext context, @Option(name = "search", description = "Number of recent messages to delete", required = false) Integer search) {
        int limit = search != null ? search : 100;
        context.deferEphemeral();

        purge(context.channel(), limit, message -> LINK_PATTERN.matcher(message.getContentRaw()).find()).thenAccept(result -> context.event().getHook().editOriginal("Deleted " + result + " messages.").queue());
    }

    @Subcommand(name = "webhook", description = "deletes messages from a specified webhook by username")
    public void webhook(CommandContext context, @Option(name = "username", description = "Specifies an user to delete") String username, @Option(name = "search", description = "Number of recent messages to delete", required = false) Integer search) {
        int limit = search != null ? search : 100;
        context.deferEphemeral();

        if (bot.getChatType() != ChatType.WEBHOOK) {
            context.event().getHook().editOriginal("We're not using webhook to send Minecraft messages to Discord, so we'll not proceed any further.").queue();
            return;
        }

        purge(context.channel(), limit, message -> message.isWebhookMessage() && message.getAuthor().getName().equalsIgnoreCase(username)).thenAccept(result -> context.event().getHook().editOriginal("Deleted " + result + " messages.").queue());
    }

    public CompletableFuture<Integer> purge(MessageChannel channel, int searchLimit, Predicate<Message> condition) {
        return channel.getHistory().retrievePast(searchLimit).submit().thenComposeAsync(messages -> {
            List<Message> matches = messages.stream().filter(condition).toList();
            if (matches.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }

            List<CompletableFuture<Void>> deletions = channel.purgeMessages(matches);
            return CompletableFuture.allOf(deletions.toArray(CompletableFuture[]::new)).thenApplyAsync(ignored -> matches.size());
        });
    }
}
