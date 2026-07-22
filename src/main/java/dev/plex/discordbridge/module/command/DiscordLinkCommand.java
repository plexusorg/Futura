package dev.plex.discordbridge.module.command;

import dev.plex.command.SimplePlexCommand;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.discordbridge.common.config.BridgeSettings;
import dev.plex.discordbridge.common.dialog.LinkDialogController;
import dev.plex.discordbridge.common.link.LinkService;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DiscordLinkCommand extends SimplePlexCommand
{
    private final LinkService links;
    private final BridgeSettings.Linking settings;
    private final LinkDialogController dialogs;

    public DiscordLinkCommand(
            LinkService links,
            BridgeSettings.Linking settings,
            LinkDialogController dialogs)
    {
        super(command(settings.playerCommandName())
                .description("Link your Minecraft account to Discord")
                .usage("/<command> [gui | code | status | unlink]")
                .aliases(settings.playerCommandAliases())
                .source(RequiredCommandSource.IN_GAME)
                .build());
        this.links = links;
        this.settings = settings;
        this.dialogs = dialogs;
    }

    @Override
    protected Component execute(@NotNull CommandSender sender, @Nullable Player player, @NotNull String[] args)
    {
        if (player == null)
        {
            return Component.text("This command can only be used in game.", NamedTextColor.RED);
        }
        if (!settings.enabled())
        {
            return Component.text("Discord account linking is disabled.", NamedTextColor.RED);
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui"))
        {
            dialogs.show(player);
            return null;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        return switch (action)
        {
            case "code" -> issueCode(player);
            case "status" -> status(player);
            case "unlink" -> unlink(player);
            default -> Component.text("Usage: " + getUsage(), NamedTextColor.RED);
        };
    }

    @Override
    protected @NotNull List<String> suggestions(
            @NotNull CommandSender sender,
            @NotNull String alias,
            @NotNull String[] args)
    {
        if (args.length != 1)
        {
            return List.of();
        }
        return settings.playerCanUnlink()
                ? List.of("gui", "code", "status", "unlink")
                : List.of("gui", "code", "status");
    }

    private Component issueCode(Player player)
    {
        LinkService.CodeIssue result = links.issueCode(player.getUniqueId(), player.getName());
        return switch (result.status())
        {
            case CREATED -> Component.text()
                    .append(Component.text("Your Discord link code is ", NamedTextColor.GOLD))
                    .append(Component.text(result.code(), NamedTextColor.YELLOW))
                    .append(Component.text(". DM this code to the Discord bot within "
                            + result.expiresInSeconds() + " seconds.", NamedTextColor.GOLD))
                    .build();
            case ALREADY_LINKED -> Component.text(
                    "Your account is already linked to Discord user ID "
                            + result.existingLink().discordId() + ".",
                    NamedTextColor.RED);
            case DISABLED -> Component.text("Discord account linking is disabled.", NamedTextColor.RED);
        };
    }

    private Component status(Player player)
    {
        return links.byMinecraft(player.getUniqueId())
                .<Component>map(link -> Component.text(
                        "Linked to Discord user ID " + link.discordId() + ".",
                        NamedTextColor.GREEN))
                .orElseGet(() -> Component.text("Your account is not linked.", NamedTextColor.YELLOW));
    }

    private Component unlink(Player player)
    {
        if (!settings.playerCanUnlink())
        {
            return Component.text("Players cannot unlink accounts on this server.", NamedTextColor.RED);
        }
        links.removePending(player.getUniqueId());
        return links.repository().unlinkMinecraft(player.getUniqueId())
                .<Component>map(link -> Component.text("Your Discord account was unlinked.", NamedTextColor.GREEN))
                .orElseGet(() -> Component.text("Your account is not linked.", NamedTextColor.YELLOW));
    }
}
