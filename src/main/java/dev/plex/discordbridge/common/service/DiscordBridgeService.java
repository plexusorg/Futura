package dev.plex.discordbridge.common.service;

import dev.plex.discordbridge.common.config.BridgeSettings;
import dev.plex.discordbridge.common.link.LinkService;
import dev.plex.discordbridge.common.link.AccountLink;
import dev.plex.discordbridge.common.platform.BridgePlatform;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class DiscordBridgeService extends ListenerAdapter
{
    private static final Pattern IPV4_ADDRESS = Pattern.compile(
            "(?<![\\d.])(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?::\\d{1,5})?(?![\\d.])");
    private static final Pattern IPV6_ADDRESS = Pattern.compile(
            "(?i)(?<![0-9a-f:])(?:"
                    + "(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}|"
                    + "(?:[0-9a-f]{1,4}:){1,7}:|"
                    + "(?:[0-9a-f]{1,4}:){1,6}:[0-9a-f]{1,4}|"
                    + "(?:[0-9a-f]{1,4}:){1,5}(?::[0-9a-f]{1,4}){1,2}|"
                    + "(?:[0-9a-f]{1,4}:){1,4}(?::[0-9a-f]{1,4}){1,3}|"
                    + "(?:[0-9a-f]{1,4}:){1,3}(?::[0-9a-f]{1,4}){1,4}|"
                    + "(?:[0-9a-f]{1,4}:){1,2}(?::[0-9a-f]{1,4}){1,5}|"
                    + "[0-9a-f]{1,4}:(?:(?::[0-9a-f]{1,4}){1,6})|"
                    + ":(?:(?::[0-9a-f]{1,4}){1,7}|:)"
                    + ")(?:%[0-9a-z._~-]+)?(?![0-9a-f:])");

    private final BridgePlatform platform;
    private final BridgeSettings settings;
    private final LinkService links;
    private final AtomicBoolean stopping = new AtomicBoolean();

    private volatile JDA jda;
    private volatile TextChannel chatChannel;
    private volatile TextChannel staffChannel;
    private volatile TextChannel consoleChannel;
    private volatile ConsoleLogRelay consoleRelay;

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
        ConsoleLogRelay relay = consoleRelay;
        consoleRelay = null;
        if (relay != null)
        {
            relay.close();
        }
        sendStoppedMessage();
        stopping.set(true);
        chatChannel = null;
        staffChannel = null;
        consoleChannel = null;
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
        if (settings.console().enabled())
        {
            consoleChannel = resolveChannel(
                    event.getJDA(),
                    "console",
                    settings.console().channelId(),
                    settings.console().fallbackName(),
                    true);
            if (consoleChannel != null && settings.console().serverOutputToDiscord())
            {
                consoleRelay = ConsoleLogRelay.attach(settings.console(), this::sendConsoleOutput);
            }
        }
        platform.info("Discord bot connected as {0}", event.getJDA().getSelfUser().getName());
        registerSlashCommands(event.getJDA());
        sendLifecycleMessage(settings.lifecycle().started());
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event)
    {
        if (stopping.get() || !settings.slashCommands().enabled() || !isConfiguredGuild(event.getGuild()))
        {
            return;
        }

        switch (event.getName())
        {
            case "list" -> handleListCommand(event);
            case "info" -> handleInfoCommand(event);
            default -> { }
        }
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
        if (sameChannel(event.getChannel().getIdLong(), consoleChannel))
        {
            if (settings.console().discordToServerCommands())
            {
                handleConsoleCommand(event);
            }
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

    public void sendPlayerJoined(Player player)
    {
        platform.executeEntity(player, () -> sendPresence(player, settings.presence().joined()));
    }

    public void sendPlayerLeft(Player player)
    {
        sendPresence(player, settings.presence().left());
    }

    public CompletableFuture<DiscordIdentity> resolveDiscordIdentity(String discordId)
    {
        JDA current = jda;
        if (current == null || stopping.get())
        {
            return CompletableFuture.completedFuture(DiscordIdentity.unavailable(discordId));
        }

        Guild guild = identityGuild(current);
        if (guild == null)
        {
            return resolveUserIdentity(current, discordId);
        }

        return guild.retrieveMemberById(discordId)
                .submit()
                .handle((member, failure) -> member == null ? null : DiscordIdentity.from(member))
                .thenCompose(identity -> identity == null
                        ? resolveUserIdentity(current, discordId)
                        : CompletableFuture.completedFuture(identity));
    }

    private Guild identityGuild(JDA current)
    {
        if (!settings.guildId().isBlank())
        {
            try
            {
                Guild configured = current.getGuildById(settings.guildId());
                if (configured != null)
                {
                    return configured;
                }
            }
            catch (IllegalArgumentException ignored)
            {
                // Fall through to a resolved bridge channel's guild.
            }
        }
        if (chatChannel != null)
        {
            return chatChannel.getGuild();
        }
        return staffChannel == null ? null : staffChannel.getGuild();
    }

    private void registerSlashCommands(JDA current)
    {
        if (!settings.slashCommands().enabled())
        {
            return;
        }

        List<Guild> guilds;
        if (settings.guildId().isBlank())
        {
            guilds = current.getGuilds();
        }
        else
        {
            Guild guild;
            try
            {
                guild = current.getGuildById(settings.guildId());
            }
            catch (IllegalArgumentException exception)
            {
                guild = null;
            }
            if (guild == null)
            {
                platform.warn("Discord slash commands were not registered because guild-id was not found");
                return;
            }
            guilds = List.of(guild);
        }

        List<CommandData> commands = List.of(
                Commands.slash("list", "See who is currently playing on the server"),
                Commands.slash("info", "View the server address and connection details"));
        for (Guild guild : guilds)
        {
            guild.updateCommands()
                    .addCommands(commands)
                    .queue(
                            ignored -> platform.info("Registered Discord slash commands in {0}", guild.getName()),
                            failure -> platform.warn(
                                    "Could not register Discord slash commands in {0}: {1}",
                                    guild.getName(),
                                    safeError(failure)));
        }
    }

    private void handleListCommand(SlashCommandInteractionEvent event)
    {
        event.deferReply(true).queue(hook -> platform.executeGlobal(() ->
        {
            List<String> players = platform.onlinePlayers().stream()
                    .filter(player -> !isVanished(player))
                    .map(Player::getName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .map(MarkdownSanitizer::escape)
                    .toList();
            MessageEmbed embed = listEmbed(players, org.bukkit.Bukkit.getMaxPlayers());
            hook.editOriginalEmbeds(embed)
                    .queue(null, failure -> platform.warn(
                            "Could not answer Discord /list: {0}", safeError(failure)));
        }), failure -> platform.warn("Could not acknowledge Discord /list: {0}", safeError(failure)));
    }

    private void handleInfoCommand(SlashCommandInteractionEvent event)
    {
        event.replyEmbeds(infoEmbed())
                .setEphemeral(true)
                .queue(null, failure -> platform.warn("Could not answer Discord /info: {0}", safeError(failure)));
    }

    private MessageEmbed listEmbed(List<String> players, int maximumPlayers)
    {
        BridgeSettings.SlashCommands commands = settings.slashCommands();
        EmbedBuilder embed = baseCommandEmbed(commands.listTitle())
                .setDescription(truncate(
                        players.isEmpty() ? commands.listEmptyDescription() : commands.listDescription(),
                        MessageEmbed.DESCRIPTION_MAX_LENGTH))
                .addField(
                        truncate(commands.listCountField(), MessageEmbed.TITLE_MAX_LENGTH),
                        "**" + players.size() + " / " + maximumPlayers + "**",
                        true);
        if (!players.isEmpty())
        {
            String playerList = players.stream()
                    .map(player -> "🟢 " + player)
                    .collect(Collectors.joining("\n"));
            embed.addField(
                    truncate(commands.listPlayersField(), MessageEmbed.TITLE_MAX_LENGTH),
                    truncate(playerList, MessageEmbed.VALUE_MAX_LENGTH),
                    false);
        }
        return embed.build();
    }

    private MessageEmbed infoEmbed()
    {
        BridgeSettings.SlashCommands commands = settings.slashCommands();
        String address = commands.serverAddress().isBlank()
                ? "Not configured"
                : MarkdownSanitizer.escape(commands.serverAddress());
        return baseCommandEmbed(commands.infoTitle())
                .setDescription(truncate(commands.infoDescription(), MessageEmbed.DESCRIPTION_MAX_LENGTH))
                .addField("Status", "🟢 **Online**", true)
                .addField(
                        truncate(commands.infoAddressField(), MessageEmbed.TITLE_MAX_LENGTH),
                        truncate("`" + address + "`", MessageEmbed.VALUE_MAX_LENGTH),
                        false)
                .build();
    }

    private EmbedBuilder baseCommandEmbed(String title)
    {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(embedColor())
                .setTitle(truncate(title, MessageEmbed.TITLE_MAX_LENGTH))
                .setFooter(truncate(settings.slashCommands().footer(), MessageEmbed.TEXT_MAX_LENGTH))
                .setTimestamp(Instant.now());
        JDA current = jda;
        if (current != null)
        {
            embed.setThumbnail(current.getSelfUser().getEffectiveAvatarUrl());
        }
        return embed;
    }

    private int embedColor()
    {
        String configured = settings.slashCommands().embedColor().replace("#", "");
        try
        {
            return Integer.parseInt(configured, 16) & 0xFFFFFF;
        }
        catch (NumberFormatException ignored)
        {
            return 0xF4C542;
        }
    }

    private boolean isConfiguredGuild(Guild guild)
    {
        return guild != null && (settings.guildId().isBlank() || guild.getId().equals(settings.guildId()));
    }

    private CompletableFuture<DiscordIdentity> resolveUserIdentity(JDA current, String discordId)
    {
        try
        {
            return current.retrieveUserById(discordId)
                    .submit()
                    .handle((user, failure) -> user == null
                            ? DiscordIdentity.unavailable(discordId)
                            : DiscordIdentity.from(user));
        }
        catch (RuntimeException exception)
        {
            return CompletableFuture.completedFuture(DiscordIdentity.unavailable(discordId));
        }
    }

    private TextChannel resolveChannel(JDA jda, String routeName, BridgeSettings.Route route)
    {
        return resolveChannel(
                jda,
                routeName,
                route.channelId(),
                route.fallbackName(),
                route.minecraftToDiscord());
    }

    private TextChannel resolveChannel(
            JDA jda,
            String routeName,
            String channelId,
            String fallbackName,
            boolean needsSendPermission)
    {
        TextChannel byId = null;
        if (!channelId.isBlank())
        {
            try
            {
                byId = jda.getTextChannelById(channelId);
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
            logResolved(routeName, byId, needsSendPermission);
            return byId;
        }

        if (fallbackName.isBlank())
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
            matches = guild.getTextChannelsByName(fallbackName, false);
        }
        else
        {
            matches = jda.getTextChannelsByName(fallbackName, false);
        }

        if (matches.size() != 1)
        {
            platform.warn(
                    "Discord {0} fallback name ''{1}'' matched {2} channels; the route is disabled",
                    routeName,
                    fallbackName,
                    matches.size());
            return null;
        }

        TextChannel resolved = matches.getFirst();
        logResolved(routeName, resolved, needsSendPermission);
        return resolved;
    }

    private void logResolved(String routeName, TextChannel channel, boolean needsSendPermission)
    {
        platform.info(
                "Discord {0} route resolved to #{1} ({2})",
                routeName,
                channel.getName(),
                channel.getId());
        if (needsSendPermission && !channel.canTalk())
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

    private void handleConsoleCommand(MessageReceivedEvent event)
    {
        Message commandMessage = event.getMessage();
        String command = commandMessage.getContentRaw().trim();
        if (command.startsWith("/"))
        {
            command = command.substring(1).trim();
        }
        if (command.isBlank())
        {
            markConsoleCommand(commandMessage, false);
            return;
        }
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0)
        {
            markConsoleCommand(commandMessage, false);
            sendConsoleControl("⛔ Command denied: commands must contain exactly one line.");
            return;
        }

        AccountLink link = links.byDiscord(event.getAuthor().getId()).orElse(null);
        if (link == null)
        {
            markConsoleCommand(commandMessage, false);
            sendConsoleControl("⛔ Command denied: your Discord account is not linked to Minecraft.");
            return;
        }

        String finalCommand = command;
        platform.executeGlobal(() ->
        {
            String commandPermission = platform.consoleCommandPermission(finalCommand).orElse("");
            platform.executeAsync(() ->
            {
                OfflinePermissionResolver.Result consolePermissionResult = OfflinePermissionResolver.check(
                        link.minecraftId(),
                        settings.console().permission());

                if (!allowConsolePermission(commandMessage, link, settings.console().permission(), consolePermissionResult))
                {
                    return;
                }

                if (!commandPermission.isBlank())
                {
                    OfflinePermissionResolver.Result commandPermissionResult = OfflinePermissionResolver.check(
                            link.minecraftId(),
                            commandPermission);
                    if (!allowConsolePermission(commandMessage, link, commandPermission, commandPermissionResult))
                    {
                        return;
                    }
                }

                platform.executeGlobal(() -> executeConsoleCommand(link, finalCommand, commandMessage));
            });
        });
    }

    private boolean allowConsolePermission(
            Message commandMessage,
            AccountLink link,
            String permission,
            OfflinePermissionResolver.Result result)
    {
        if (result == OfflinePermissionResolver.Result.GRANTED)
        {
            return true;
        }

        markConsoleCommand(commandMessage, false);
        if (result == OfflinePermissionResolver.Result.PROVIDER_UNAVAILABLE)
        {
            sendConsoleControl("⛔ Command denied: no Vault-compatible offline permission provider is available.");
            return false;
        }

        sendConsoleControl("⛔ Command denied: **" + MarkdownSanitizer.escape(link.minecraftName())
                + "** does not have `" + MarkdownSanitizer.escape(permission) + "`.");
        return false;
    }

    private void executeConsoleCommand(AccountLink link, String command, Message commandMessage)
    {
        platform.info("[Discord Console] {0} issued /{1}", link.minecraftName(), command);
        if (!settings.console().serverOutputToDiscord())
        {
            sendConsoleControl("▶ **" + MarkdownSanitizer.escape(link.minecraftName())
                    + "** issued `" + MarkdownSanitizer.escape(command) + "`");
        }
        try
        {
            boolean accepted = platform.dispatchConsole(
                    link.minecraftId(),
                    link.minecraftName(),
                    command,
                    this::sendConsoleFeedback);
            if (!accepted)
            {
                markConsoleCommand(commandMessage, false);
                sendConsoleControl("⚠️ **" + MarkdownSanitizer.escape(link.minecraftName())
                        + "** attempted an unknown or rejected command.");
                return;
            }
            markConsoleCommand(commandMessage, true);
        }
        catch (RuntimeException exception)
        {
            markConsoleCommand(commandMessage, false);
            platform.error(
                    "A Discord console command from {0} failed",
                    exception,
                    link.minecraftName());
            sendConsoleControl("⛔ Command execution failed; check the server console.");
        }
    }

    private void markConsoleCommand(Message message, boolean successful)
    {
        message.addReaction(Emoji.fromUnicode(successful ? "✅" : "❌"))
                .queue(null, failure -> platform.warn(
                        "Could not mark a Discord console command as {0}: {1}",
                        successful ? "successful" : "failed",
                        safeError(failure)));
    }

    private void sendConsoleOutput(String output)
    {
        TextChannel channel = consoleChannel;
        if (channel == null || stopping.get() || !channel.canTalk())
        {
            return;
        }
        String safeOutput = redactConsole(output).replace("```", "`\u200B``");
        String content = "```ansi\n" + truncate(safeOutput, Message.MAX_CONTENT_LENGTH - 13) + "\n```";
        channel.sendMessage(content)
                .setAllowedMentions(Collections.emptySet())
                .queue(null, failure -> { });
    }

    private void sendConsoleFeedback(Component feedback)
    {
        String output = redactConsole(PlainTextComponentSerializer.plainText().serialize(feedback)).trim();
        if (output.isBlank())
        {
            return;
        }
        String safeOutput = output.replace("```", "`\u200B``");
        sendConsoleControl("```\n" + truncate(safeOutput, Message.MAX_CONTENT_LENGTH - 9) + "\n```");
    }

    private void sendConsoleControl(String message)
    {
        TextChannel channel = consoleChannel;
        if (channel == null || stopping.get() || !channel.canTalk())
        {
            return;
        }
        channel.sendMessage(truncate(redactConsole(message), Message.MAX_CONTENT_LENGTH))
                .setAllowedMentions(Collections.emptySet())
                .queue(null, failure -> { });
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

    public record DiscordIdentity(
            String discordId,
            String displayName,
            String username,
            String primaryRoleName,
            int primaryRoleColor,
            boolean guildMember,
            boolean available)
    {
        private static final int DISCORD_BLURPLE = 0x5865F2;

        private static DiscordIdentity from(Member member)
        {
            Role primaryColoredRole = member.getRoles().stream()
                    .filter(role -> role.getColors().getPrimaryRaw() != Role.DEFAULT_COLOR_RAW)
                    .findFirst()
                    .orElse(null);
            int color = member.getColors().getPrimaryRaw();
            if (color == Role.DEFAULT_COLOR_RAW)
            {
                color = DISCORD_BLURPLE;
            }
            return new DiscordIdentity(
                    member.getId(),
                    member.getEffectiveName(),
                    member.getUser().getName(),
                    primaryColoredRole == null ? "No colored role" : primaryColoredRole.getName(),
                    color,
                    true,
                    true);
        }

        private static DiscordIdentity from(User user)
        {
            return new DiscordIdentity(
                    user.getId(),
                    user.getEffectiveName(),
                    user.getName(),
                    "Not available outside the configured server",
                    DISCORD_BLURPLE,
                    false,
                    true);
        }

        private static DiscordIdentity unavailable(String discordId)
        {
            return new DiscordIdentity(
                    discordId,
                    "Discord member unavailable",
                    "unknown",
                    "Bot is offline or the member could not be found",
                    DISCORD_BLURPLE,
                    false,
                    false);
        }

        public String colorHex()
        {
            return String.format("#%06X", primaryRoleColor & 0xFFFFFF);
        }
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

    private void sendPresence(Player player, String template)
    {
        if (!settings.presence().enabled() || template.isBlank() || isVanished(player))
        {
            return;
        }
        TextChannel channel = settings.presence().route().equalsIgnoreCase("staff-chat")
                ? staffChannel
                : chatChannel;
        String message = template.replace("{player}", MarkdownSanitizer.escape(player.getName()));
        send(channel, message, "player presence");
    }

    private String redactConsole(String input)
    {
        if (!settings.console().redactIpAddresses() || input.isBlank())
        {
            return input;
        }
        String replacement = Matcher.quoteReplacement(settings.console().ipRedactionText());
        String withoutIpv6 = IPV6_ADDRESS.matcher(input).replaceAll(replacement);
        return IPV4_ADDRESS.matcher(withoutIpv6).replaceAll(replacement);
    }

    private TextChannel lifecycleChannel()
    {
        return settings.lifecycle().route().equalsIgnoreCase("staff-chat") ? staffChannel : chatChannel;
    }

    @SuppressWarnings("deprecation") // Vanish plugins expose their temporary state through Bukkit's "vanished" metadata key.
    private static boolean isVanished(Player player)
    {
        return player.getMetadata("vanished").stream().anyMatch(metadata -> metadata.asBoolean());
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
