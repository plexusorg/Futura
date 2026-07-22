package dev.plex.discordbridge.standalone.command;

import dev.plex.discordbridge.common.config.BridgeSettings;
import dev.plex.discordbridge.common.link.AccountLink;
import dev.plex.discordbridge.common.dialog.LinkDialogController;
import dev.plex.discordbridge.common.link.LinkService;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StandaloneLinkCommands implements CommandExecutor, TabCompleter
{
    private final LinkService links;
    private final BridgeSettings.Linking settings;
    private final LinkDialogController dialogs;

    public StandaloneLinkCommands(
            LinkService links,
            BridgeSettings.Linking settings,
            LinkDialogController dialogs)
    {
        this.links = links;
        this.settings = settings;
        this.dialogs = dialogs;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args)
    {
        @Nullable Component response = command.getName().equalsIgnoreCase("discordlinkadmin")
                ? admin(sender, args)
                : player(sender, args);
        if (response != null)
        {
            sender.sendMessage(response);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args)
    {
        if (command.getName().equalsIgnoreCase("discordlinkadmin"))
        {
            if (!(sender instanceof ConsoleCommandSender))
            {
                return List.of();
            }
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
        if (args.length == 1)
        {
            return settings.playerCanUnlink()
                    ? List.of("gui", "code", "status", "unlink")
                    : List.of("gui", "code", "status");
        }
        return List.of();
    }

    private @Nullable Component player(CommandSender sender, String[] args)
    {
        if (!(sender instanceof Player player))
        {
            return error("This command can only be used in game.");
        }
        if (!settings.enabled())
        {
            return error("Discord account linking is disabled.");
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui"))
        {
            dialogs.show(player);
            return null;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        return switch (action)
        {
            case "code" -> issueCode(player.getUniqueId(), player.getName());
            case "status" -> links.byMinecraft(player.getUniqueId())
                    .<Component>map(link -> success("Linked to Discord user ID " + link.discordId() + "."))
                    .orElseGet(() -> info("Your account is not linked."));
            case "unlink" -> unlinkSelf(player);
            default -> error("Usage: /discordlink [gui | code | status | unlink]");
        };
    }

    private Component admin(CommandSender sender, String[] args)
    {
        if (!(sender instanceof ConsoleCommandSender))
        {
            return error("This command is only available through the server console.");
        }
        if (!settings.enabled())
        {
            return error("Discord account linking is disabled.");
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

    private Component issueCode(UUID minecraftId, String minecraftName)
    {
        LinkService.CodeIssue result = links.issueCode(minecraftId, minecraftName);
        return switch (result.status())
        {
            case CREATED -> info("Discord link code for " + minecraftName + ": " + result.code()
                    + " (expires in " + result.expiresInSeconds() + " seconds)");
            case ALREADY_LINKED -> error("That account is already linked to Discord user ID "
                    + result.existingLink().discordId() + ".");
            case DISABLED -> error("Discord account linking is disabled.");
        };
    }

    private Component unlinkSelf(Player player)
    {
        if (!settings.playerCanUnlink())
        {
            return error("Players cannot unlink accounts on this server.");
        }
        links.removePending(player.getUniqueId());
        return links.repository().unlinkMinecraft(player.getUniqueId())
                .<Component>map(link -> success("Your Discord account was unlinked."))
                .orElseGet(() -> info("Your account is not linked."));
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
        all.forEach(link -> output.append("\n").append(describeText(link)));
        return info(output.toString());
    }

    private Component lookup(String[] args)
    {
        if (args.length != 3)
        {
            return error("Usage: /discordlinkadmin lookup <minecraft | discord> <player | id>");
        }
        Optional<AccountLink> result = switch (args[1].toLowerCase(Locale.ROOT))
        {
            case "minecraft" -> offlinePlayer(args[2]).flatMap(player -> links.byMinecraft(player.getUniqueId()));
            case "discord" -> links.byDiscord(args[2]);
            default -> Optional.empty();
        };
        return result.<Component>map(link -> info(describeText(link)))
                .orElseGet(() -> error("No matching link was found."));
    }

    private Component code(String[] args)
    {
        if (args.length != 2)
        {
            return error("Usage: /discordlinkadmin code <minecraft-player>");
        }
        Optional<OfflinePlayer> player = offlinePlayer(args[1]);
        return player.<Component>map(value -> issueCode(value.getUniqueId(), value.getName() == null ? args[1] : value.getName()))
                .orElseGet(() -> error("Unknown cached player: " + args[1]));
    }

    private Component link(String[] args)
    {
        if (args.length != 3 || !args[2].matches("[0-9]{15,22}"))
        {
            return error("Usage: /discordlinkadmin link <minecraft-player> <discord-id>");
        }
        Optional<OfflinePlayer> player = offlinePlayer(args[1]);
        if (player.isEmpty())
        {
            return error("Unknown cached player: " + args[1]);
        }
        String name = player.get().getName() == null ? args[1] : player.get().getName();
        AccountLink link = links.repository().forceLink(player.get().getUniqueId(), name, args[2]);
        links.removePending(player.get().getUniqueId());
        return success("Linked " + name + " to Discord user ID " + link.discordId() + ".");
    }

    private Component unlink(String[] args)
    {
        if (args.length != 3)
        {
            return error("Usage: /discordlinkadmin unlink <minecraft | discord> <player | id>");
        }
        Optional<AccountLink> removed = switch (args[1].toLowerCase(Locale.ROOT))
        {
            case "minecraft" -> offlinePlayer(args[2]).flatMap(player ->
            {
                links.removePending(player.getUniqueId());
                return links.repository().unlinkMinecraft(player.getUniqueId());
            });
            case "discord" -> links.repository().unlinkDiscord(args[2]);
            default -> Optional.empty();
        };
        return removed.<Component>map(link -> success("Unlinked " + describeText(link) + "."))
                .orElseGet(() -> error("No matching link was found."));
    }

    private Optional<OfflinePlayer> offlinePlayer(String nameOrUuid)
    {
        try
        {
            return Optional.of(Bukkit.getOfflinePlayer(UUID.fromString(nameOrUuid)));
        }
        catch (IllegalArgumentException ignored)
        {
            return Optional.ofNullable(Bukkit.getOfflinePlayerIfCached(nameOrUuid));
        }
    }

    private String describeText(AccountLink link)
    {
        return link.minecraftName() + " (" + link.minecraftId() + ") -> " + link.discordId();
    }

    private Component adminUsage()
    {
        return error("Usage: /discordlinkadmin <list | lookup | code | link | unlink> ...");
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
