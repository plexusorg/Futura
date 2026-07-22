package dev.plex.discordbridge.command;

import dev.plex.api.player.PlexPlayerView;
import dev.plex.command.SimplePlexCommand;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.discordbridge.BridgeSettings;
import dev.plex.discordbridge.link.AccountLink;
import dev.plex.discordbridge.link.LinkService;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DiscordLinkAdminCommand extends SimplePlexCommand
{
    private final LinkService links;

    public DiscordLinkAdminCommand(LinkService links, BridgeSettings.Linking settings)
    {
        super(command(settings.adminCommandName())
                .description("Manage Discord account links")
                .usage("/<command> <list | lookup | code | link | unlink> ...")
                .source(RequiredCommandSource.CONSOLE)
                .build());
        this.links = links;
    }

    @Override
    protected Component execute(@NotNull CommandSender sender, @Nullable Player player, @NotNull String[] args)
    {
        if (!(sender instanceof ConsoleCommandSender))
        {
            return error("This command is only available through the server console.");
        }
        if (args.length == 0)
        {
            return adminUsage();
        }
        return switch (args[0].toLowerCase(Locale.ROOT))
        {
            case "list" -> list();
            case "lookup" -> lookup(args);
            case "code" -> code(args);
            case "link" -> link(args);
            case "unlink" -> unlink(args);
            default -> adminUsage();
        };
    }

    @Override
    protected @NotNull List<String> suggestions(
            @NotNull CommandSender sender,
            @NotNull String alias,
            @NotNull String[] args)
    {
        if (args.length == 1)
        {
            return List.of("list", "lookup", "code", "link", "unlink");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("lookup") || args[0].equalsIgnoreCase("unlink")))
        {
            return List.of("minecraft", "discord");
        }
        return List.of();
    }

    private Component list()
    {
        List<AccountLink> all = links.repository().all().stream()
                .sorted(Comparator.comparing(AccountLink::minecraftName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (all.isEmpty())
        {
            return info("No linked accounts.");
        }
        StringBuilder output = new StringBuilder("Linked accounts (" + all.size() + "):");
        all.forEach(link -> output.append("\n")
                .append(link.minecraftName())
                .append(" (")
                .append(link.minecraftId())
                .append(") -> ")
                .append(link.discordId()));
        return info(output.toString());
    }

    private Component lookup(String[] args)
    {
        if (args.length != 3)
        {
            return error("Usage: /" + getName() + " lookup <minecraft | discord> <player | id>");
        }
        Optional<AccountLink> result = switch (args[1].toLowerCase(Locale.ROOT))
        {
            case "minecraft" -> player(args[2]).flatMap(view -> links.byMinecraft(view.uuid()));
            case "discord" -> links.byDiscord(args[2]);
            default -> Optional.empty();
        };
        return result.<Component>map(this::describe).orElseGet(() -> error("No matching link was found."));
    }

    private Component code(String[] args)
    {
        if (args.length != 2)
        {
            return error("Usage: /" + getName() + " code <minecraft-player>");
        }
        Optional<? extends PlexPlayerView> view = player(args[1]);
        if (view.isEmpty())
        {
            return error("Unknown Plex player: " + args[1]);
        }
        LinkService.CodeIssue result = links.issueCode(view.get().uuid(), view.get().name());
        return switch (result.status())
        {
            case CREATED -> success("Link code for " + view.get().name() + ": " + result.code()
                    + " (expires in " + result.expiresInSeconds() + " seconds)");
            case ALREADY_LINKED -> error("That Minecraft account is already linked to "
                    + result.existingLink().discordId() + ".");
            case DISABLED -> error("Discord account linking is disabled.");
        };
    }

    private Component link(String[] args)
    {
        if (args.length != 3 || !validDiscordId(args[2]))
        {
            return error("Usage: /" + getName() + " link <minecraft-player> <discord-id>");
        }
        Optional<? extends PlexPlayerView> view = player(args[1]);
        if (view.isEmpty())
        {
            return error("Unknown Plex player: " + args[1]);
        }
        AccountLink link = links.repository().forceLink(view.get().uuid(), view.get().name(), args[2]);
        links.removePending(view.get().uuid());
        return success("Linked " + link.minecraftName() + " to Discord user ID " + link.discordId() + ".");
    }

    private Component unlink(String[] args)
    {
        if (args.length != 3)
        {
            return error("Usage: /" + getName() + " unlink <minecraft | discord> <player | id>");
        }
        Optional<AccountLink> removed = switch (args[1].toLowerCase(Locale.ROOT))
        {
            case "minecraft" -> player(args[2]).flatMap(view ->
            {
                links.removePending(view.uuid());
                return links.repository().unlinkMinecraft(view.uuid());
            });
            case "discord" -> links.repository().unlinkDiscord(args[2]);
            default -> Optional.empty();
        };
        return removed.<Component>map(link -> success("Unlinked " + link.minecraftName()
                        + " from Discord user ID " + link.discordId() + "."))
                .orElseGet(() -> error("No matching link was found."));
    }

    private Optional<? extends PlexPlayerView> player(String name)
    {
        return api().players().byName(name);
    }

    private Component describe(AccountLink link)
    {
        return info(link.minecraftName() + " (" + link.minecraftId() + ") -> " + link.discordId());
    }

    private Component adminUsage()
    {
        return error("Usage: " + getUsage());
    }

    private static boolean validDiscordId(String value)
    {
        return value.matches("[0-9]{15,22}");
    }

    private static Component success(String message)
    {
        return Component.text(message, NamedTextColor.GREEN);
    }

    private static Component info(String message)
    {
        return Component.text(message, NamedTextColor.YELLOW);
    }

    private static Component error(String message)
    {
        return Component.text(message, NamedTextColor.RED);
    }
}
