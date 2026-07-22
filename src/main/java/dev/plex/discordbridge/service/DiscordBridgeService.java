package dev.plex.discordbridge.service;

import dev.plex.discordbridge.BridgeSettings;
import dev.plex.discordbridge.link.LinkService;
import dev.plex.discordbridge.link.AccountLink;
import dev.plex.discordbridge.platform.BridgePlatform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class DiscordBridgeService extends ListenerAdapter
{
    private final BridgePlatform platform;
    private final BridgeSettings settings;
    private final LinkService links;
    private final AtomicBoolean stopping = new AtomicBoolean();

    private volatile JDA jda;
    private volatile TextChannel chatChannel;
    private volatile TextChannel staffChannel;

    public DiscordBridgeService(BridgePlatform platform, BridgeSettings settings, LinkService links)
    {
        this.platform = platform;
        this.settings = settings;
        this.links = links;
    }

    public void start()
    {
        try
        {
            JDABuilder builder = JDABuilder.createLight(
                            settings.token(),
                            EnumSet.of(
                                    GatewayIntent.GUILD_MESSAGES,
                                    GatewayIntent.DIRECT_MESSAGES,
                                    GatewayIntent.MESSAGE_CONTENT))
                    .addEventListeners(this);
            if (!settings.activity().isBlank())
            {
                builder.setActivity(Activity.playing(settings.activity()));
            }
            jda = builder.build();
            platform.info("Discord bot login started");
        }
        catch (RuntimeException exception)
        {
            platform.error("Could not start the Discord bot: {0}", safeError(exception));
        }
    }

    public void stop()
    {
        sendStoppedMessage();
        stopping.set(true);
        chatChannel = null;
        staffChannel = null;
        JDA current = jda;
        jda = null;
        if (current != null)
        {
            current.removeEventListener(this);
            current.shutdownNow();
        }
    }

    @Override
    public void onReady(@NotNull ReadyEvent event)
    {
        if (stopping.get())
        {
            return;
        }
        chatChannel = resolveChannel(event.getJDA(), "public chat", settings.chat());
        staffChannel = resolveChannel(event.getJDA(), "staff chat", settings.staffChat());
        platform.info("Discord bot connected as {0}", event.getJDA().getSelfUser().getName());
        sendLifecycleMessage(settings.lifecycle().started());
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event)
    {
        if (stopping.get())
        {
            return;
        }
        if (settings.ignoreBots() && event.getAuthor().isBot())
        {
            return;
        }
        if (!event.isFromGuild())
        {
            handleLinkDirectMessage(event);
            return;
        }
        if (settings.ignoreWebhooks() && event.getMessage().isWebhookMessage())
        {
            return;
        }

        String discordName = event.getMember() == null
                ? event.getAuthor().getName()
                : event.getMember().getEffectiveName();
        String user = linkedDisplayName(event.getAuthor().getId(), discordName);
        String message = discordMessage(event.getMessage());
        if (message.isBlank())
        {
            return;
        }

        if (sameChannel(event.getChannel().getIdLong(), chatChannel) && settings.chat().discordToMinecraft())
        {
            broadcastPublic(component(settings.discordToChatFormat(), user, message));
        }
        else if (sameChannel(event.getChannel().getIdLong(), staffChannel) && settings.staffChat().discordToMinecraft())
        {
            broadcastStaff(component(settings.discordToStaffFormat(), user, message));
        }
    }

    public void sendPublicChat(String player, String message)
    {
        if (settings.chat().minecraftToDiscord())
        {
            send(chatChannel, formatDiscord(settings.chatToDiscordFormat(), player, message), "public chat");
        }
    }

    public void sendStaffChat(String player, String message)
    {
        if (settings.staffChat().minecraftToDiscord())
        {
            send(staffChannel, formatDiscord(settings.staffToDiscordFormat(), player, message), "staff chat");
        }
    }

    private TextChannel resolveChannel(JDA jda, String routeName, BridgeSettings.Route route)
    {
        TextChannel byId = null;
        if (!route.channelId().isBlank())
        {
            try
            {
                byId = jda.getTextChannelById(route.channelId());
            }
            catch (IllegalArgumentException exception)
            {
                platform.warn("The configured {0} channel ID is invalid; trying its fallback name", routeName);
            }
            if (byId == null)
            {
                platform.warn("The configured {0} channel ID was not found; trying its fallback name", routeName);
            }
        }
        if (byId != null)
        {
            logResolved(routeName, byId, route);
            return byId;
        }

        if (route.fallbackName().isBlank())
        {
            platform.warn("Discord {0} route is unavailable: no usable channel ID or fallback name", routeName);
            return null;
        }

        List<TextChannel> matches;
        if (!settings.guildId().isBlank())
        {
            Guild guild;
            try
            {
                guild = jda.getGuildById(settings.guildId());
            }
            catch (IllegalArgumentException exception)
            {
                guild = null;
            }
            if (guild == null)
            {
                platform.warn("Discord {0} fallback failed because guild-id was not found", routeName);
                return null;
            }
            matches = guild.getTextChannelsByName(route.fallbackName(), false);
        }
        else
        {
            matches = jda.getTextChannelsByName(route.fallbackName(), false);
        }

        if (matches.size() != 1)
        {
            platform.warn(
                    "Discord {0} fallback name ''{1}'' matched {2} channels; the route is disabled",
                    routeName,
                    route.fallbackName(),
                    matches.size());
            return null;
        }

        TextChannel resolved = matches.getFirst();
        logResolved(routeName, resolved, route);
        return resolved;
    }

    private void logResolved(String routeName, TextChannel channel, BridgeSettings.Route route)
    {
        platform.info(
                "Discord {0} route resolved to #{1} ({2})",
                routeName,
                channel.getName(),
                channel.getId());
        if (route.minecraftToDiscord() && !channel.canTalk())
        {
            platform.warn("The bot cannot send messages in the Discord {0} channel", routeName);
        }
    }

    private void send(TextChannel channel, String content, String routeName)
    {
        if (channel == null || stopping.get() || !channel.canTalk())
        {
            return;
        }
        channel.sendMessage(truncate(content, Message.MAX_CONTENT_LENGTH))
                .setAllowedMentions(Collections.emptySet())
                .queue(null, failure -> platform.warn(
                        "Could not relay a message to Discord {0}: {1}",
                        routeName,
                        safeError(failure)));
    }

    private void handleLinkDirectMessage(MessageReceivedEvent event)
    {
        if (!settings.linking().enabled())
        {
            return;
        }
        String code = event.getMessage().getContentRaw().trim();
        if (code.isBlank())
        {
            return;
        }

        LinkService.ClaimResult result = links.claim(code, event.getAuthor().getId());
        String response = switch (result.status())
        {
            case LINKED -> settings.linking().dmSuccess().replace(
                    "{minecraft}",
                    MarkdownSanitizer.escape(result.link().minecraftName()));
            case DISABLED -> "Discord account linking is disabled.";
            case MINECRAFT_ALREADY_LINKED -> "That Minecraft account is already linked.";
            case DISCORD_ALREADY_LINKED -> "Your Discord account is already linked to **"
                    + MarkdownSanitizer.escape(result.link().minecraftName()) + "**.";
            case INVALID_OR_EXPIRED -> settings.linking().dmInvalidCode();
        };
        event.getChannel().sendMessage(response)
                .setAllowedMentions(Collections.emptySet())
                .queue(null, failure -> platform.warn(
                        "Could not answer a Discord account-link DM: {0}", safeError(failure)));
        if (result.status() == LinkService.ClaimStatus.LINKED)
        {
            notifyLinkedPlayer(result.link());
        }
    }

    private void notifyLinkedPlayer(AccountLink link)
    {
        platform.executeGlobal(() -> platform.onlinePlayer(link.minecraftId())
                .ifPresent(player -> platform.executeEntity(
                        player,
                        () -> player.sendMessage(Component.text(
                                "Your Minecraft account is now linked to Discord.",
                                net.kyori.adventure.text.format.NamedTextColor.GREEN)))));
    }

    private String linkedDisplayName(String discordId, String discordName)
    {
        if (!settings.linking().enabled() || !settings.linking().useLinkedNameInChat())
        {
            return discordName;
        }
        return links.byDiscord(discordId)
                .map(link -> settings.linking().linkedNameFormat()
                        .replace("{minecraft}", link.minecraftName())
                        .replace("{discord}", discordName))
                .orElse(discordName);
    }

    private void sendStoppedMessage()
    {
        if (!settings.lifecycle().enabled() || settings.lifecycle().stopped().isBlank())
        {
            return;
        }
        TextChannel channel = lifecycleChannel();
        if (channel == null || !channel.canTalk())
        {
            return;
        }
        try
        {
            channel.sendMessage(truncate(settings.lifecycle().stopped(), Message.MAX_CONTENT_LENGTH))
                    .setAllowedMentions(Collections.emptySet())
                    .submit()
                    .get(settings.lifecycle().shutdownTimeoutSeconds(), TimeUnit.SECONDS);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            platform.warn("Interrupted while sending the Discord server-stopped message");
        }
        catch (Exception exception)
        {
            platform.warn("Could not send the Discord server-stopped message: {0}", safeError(exception));
        }
    }

    private void sendLifecycleMessage(String message)
    {
        if (!settings.lifecycle().enabled() || message.isBlank())
        {
            return;
        }
        TextChannel channel = lifecycleChannel();
        if (channel == null || !channel.canTalk())
        {
            return;
        }
        channel.sendMessage(truncate(message, Message.MAX_CONTENT_LENGTH))
                .setAllowedMentions(Collections.emptySet())
                .queue(null, failure -> platform.warn(
                        "Could not send a Discord lifecycle message: {0}", safeError(failure)));
    }

    private TextChannel lifecycleChannel()
    {
        return settings.lifecycle().route().equalsIgnoreCase("staff-chat") ? staffChannel : chatChannel;
    }

    private String discordMessage(Message message)
    {
        List<String> parts = new ArrayList<>();
        if (!message.getContentDisplay().isBlank())
        {
            parts.add(message.getContentDisplay());
        }
        if (settings.includeAttachments())
        {
            message.getAttachments().stream()
                    .map(Message.Attachment::getUrl)
                    .forEach(parts::add);
        }
        return String.join(" ", parts);
    }

    private Component component(String template, String user, String message)
    {
        Component result = platform.miniMessage(template);
        return result.replaceText(TextReplacementConfig.builder()
                .match("\\{(user|message)}")
                .replacement((match, builder) -> Component.text(
                        match.group(1).equals("user") ? user : message))
                .build());
    }

    private void broadcastPublic(Component message)
    {
        platform.executeGlobal(() -> platform.broadcast(message));
    }

    private void broadcastStaff(Component message)
    {
        platform.executeGlobal(() ->
        {
            if (settings.staffChat().sendToConsole())
            {
                org.bukkit.Bukkit.getConsoleSender().sendMessage(message);
            }
            String permission = settings.staffChat().receivePermission();
            for (Player player : platform.onlinePlayers())
            {
                if (player.hasPermission(permission))
                {
                    sendToPlayer(player, message);
                }
            }
        });
    }

    private void sendToPlayer(Player player, Component message)
    {
        platform.executeEntity(player, () -> player.sendMessage(message));
    }

    private static boolean sameChannel(long sourceId, TextChannel target)
    {
        return target != null && sourceId == target.getIdLong();
    }

    private static String formatDiscord(String template, String player, String message)
    {
        return template
                .replace("{player}", MarkdownSanitizer.escape(player))
                .replace("{message}", MarkdownSanitizer.escape(message));
    }

    private static String truncate(String input, int maxLength)
    {
        if (input.length() <= maxLength)
        {
            return input;
        }
        return input.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private static String safeError(Throwable throwable)
    {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
