package dev.plex.discordbridge.common.dialog;

import dev.plex.discordbridge.common.config.BridgeSettings;
import dev.plex.discordbridge.common.link.AccountLink;
import dev.plex.discordbridge.common.link.LinkService;
import dev.plex.discordbridge.common.platform.BridgePlatform;
import dev.plex.discordbridge.common.service.DiscordBridgeService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Player-facing Discord account dashboard built with Paper's Dialog API. */
public final class LinkDialogController
{
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final DateTimeFormatter LINK_DATE = DateTimeFormatter
            .ofPattern("MMM d, uuuu")
            .withZone(ZoneId.systemDefault());
    private static final int BODY_WIDTH = 360;
    private static final int BUTTON_WIDTH = 150;

    private final LinkService links;
    private final BridgeSettings.Linking settings;
    private final DiscordBridgeService bridge;
    private final BridgePlatform platform;

    public LinkDialogController(
            LinkService links,
            BridgeSettings.Linking settings,
            DiscordBridgeService bridge,
            BridgePlatform platform)
    {
        this.links = links;
        this.settings = settings;
        this.bridge = bridge;
        this.platform = platform;
    }

    public void show(Player player)
    {
        if (!settings.enabled())
        {
            player.sendMessage(error("Discord account linking is disabled."));
            return;
        }

        links.byMinecraft(player.getUniqueId()).ifPresentOrElse(
                link -> loadLinkedDashboard(player, link),
                () -> player.showDialog(unlinkedDialog(player)));
    }

    private void loadLinkedDashboard(Player player, AccountLink link)
    {
        player.showDialog(loadingDialog());
        bridge.resolveDiscordIdentity(link.discordId()).thenAccept(identity -> platform.executeEntity(player, () ->
        {
            if (!player.isOnline())
            {
                return;
            }
            links.byMinecraft(player.getUniqueId())
                    .filter(current -> current.discordId().equals(link.discordId()))
                    .ifPresentOrElse(
                            current -> player.showDialog(linkedDialog(player, current, identity)),
                            () -> player.showDialog(unlinkedDialog(player)));
        }));
    }

    private Dialog unlinkedDialog(Player player)
    {
        Component description = mini(
                "<#E3E5E8>Connect your Minecraft profile to Discord for a seamless identity across both communities.</#E3E5E8>");
        Component status = mini(
                "<#ED4245><bold>● NOT LINKED</bold></#ED4245><newline>"
                        + "<#FEE75C>No Discord account is connected to <white>"
                        + escape(player.getName()) + "</white>.</#FEE75C>");

        ActionButton link = button(
                "<#FFFFFF><bold>✦ LINK DISCORD</bold></#FFFFFF>",
                "<#E3E5E8>Generate a private one-time link code</#E3E5E8>",
                action(player, this::showCode));
        ActionButton refresh = button(
                "<#E3E5E8>↻ REFRESH</#E3E5E8>",
                "<#E3E5E8>Check whether a recent Discord DM completed</#E3E5E8>",
                action(player, this::show));

        return dialog(
                "<#5865F2><bold>DISCORD CONNECTION</bold></#5865F2>",
                Material.TRIPWIRE_HOOK,
                description,
                status,
                DialogType.multiAction(List.of(link, refresh), closeButton("CLOSE"), 2));
    }

    private Dialog loadingDialog()
    {
        return dialog(
                "<#5865F2><bold>DISCORD CONNECTION</bold></#5865F2>",
                Material.ENDER_EYE,
                mini("<#E3E5E8>Fetching your live Discord member profile…</#E3E5E8>"),
                mini("<#5865F2><bold>◌ SYNCING MEMBER DETAILS</bold></#5865F2>"),
                DialogType.notice(closeButton("CLOSE")));
    }

    private Dialog linkedDialog(
            Player player,
            AccountLink link,
            DiscordBridgeService.DiscordIdentity identity)
    {
        String color = identity.colorHex();
        Component member = mini(
                "<#57F287><bold>● CONNECTED</bold></#57F287><newline>"
                        + "<#FEE75C>Discord member</#FEE75C><newline>"
                        + "<" + color + "><bold>" + escape(identity.displayName()) + "</bold></" + color + ">"
                        + " <#FEE75C>(@" + escape(identity.username()) + ")</#FEE75C>");
        Component role = mini(
                "<#FEE75C>Primary role color</#FEE75C><newline>"
                        + "<" + color + "><bold>◆ " + escape(identity.primaryRoleName()) + "</bold></" + color + "><newline>"
                        + "<#DCDDDE>" + color + " • Discord ID " + escape(link.discordId()) + "</#DCDDDE>");
        Component linkedAt = mini(
                "<#FEE75C>Linked to Minecraft as <white>" + escape(link.minecraftName())
                        + "</white> on <white>" + LINK_DATE.format(Instant.ofEpochSecond(link.linkedAtEpochSecond()))
                        + "</white>.</#FEE75C>"
                        + (identity.guildMember()
                        ? ""
                        : "<newline><#FEE75C>Member role details are unavailable outside the configured Discord server.</#FEE75C>"));

        ActionButton refresh = button(
                "<#FFFFFF><bold>↻ REFRESH PROFILE</bold></#FFFFFF>",
                "<#E3E5E8>Fetch the latest display name and role color</#E3E5E8>",
                action(player, this::show));
        DialogAction unlinkAction = settings.playerCanUnlink()
                ? action(player, this::showUnlinkConfirmation)
                : null;
        ActionButton unlink = button(
                settings.playerCanUnlink()
                        ? "<#ED4245><bold>UNLINK ACCOUNT</bold></#ED4245>"
                        : "<#DCDDDE>UNLINK DISABLED</#DCDDDE>",
                settings.playerCanUnlink()
                        ? "<#E3E5E8>Disconnect this Discord account</#E3E5E8>"
                        : "<#E3E5E8>Only an administrator can unlink accounts</#E3E5E8>",
                unlinkAction);

        return dialog(
                "<#5865F2><bold>YOUR DISCORD ACCOUNT</bold></#5865F2>",
                null,
                member,
                role,
                linkedAt,
                DialogType.multiAction(List.of(refresh, unlink), closeButton("DONE"), 2));
    }

    private void showCode(Player player)
    {
        LinkService.CodeIssue result = links.issueCode(player.getUniqueId(), player.getName());
        switch (result.status())
        {
            case CREATED -> player.showDialog(codeDialog(player, result));
            case ALREADY_LINKED -> show(player);
            case DISABLED -> player.sendMessage(error("Discord account linking is disabled."));
        }
    }

    private Dialog codeDialog(Player player, LinkService.CodeIssue code)
    {
        Component instructions = mini(
                "<#E3E5E8>Direct-message this one-time code to the Discord bot. "
                        + "It expires in <white>" + code.expiresInSeconds() + " seconds</white>.</#E3E5E8>");
        Component codeDisplay = mini(
                "<#FEE75C>YOUR PRIVATE CODE</#FEE75C><newline>"
                        + "<#5865F2><bold>「 " + escape(code.code()) + " 」</bold></#5865F2><newline>"
                        + "<#FEE75C>Never share this code with another player.</#FEE75C>");

        ActionButton copy = button(
                "<#FFFFFF><bold>⧉ COPY CODE</bold></#FFFFFF>",
                "<#E3E5E8>Copy " + escape(code.code()) + " to your clipboard</#E3E5E8>",
                DialogAction.staticAction(ClickEvent.copyToClipboard(code.code())));
        ActionButton regenerate = button(
                "<#E3E5E8>↻ NEW CODE</#E3E5E8>",
                "<#E3E5E8>Invalidate this code and generate another</#E3E5E8>",
                action(player, this::showCode));
        ActionButton back = button(
                "<#E3E5E8>← BACK</#E3E5E8>",
                "<#E3E5E8>Return to your connection dashboard</#E3E5E8>",
                action(player, this::show));

        return dialog(
                "<#5865F2><bold>LINK YOUR DISCORD</bold></#5865F2>",
                Material.NAME_TAG,
                instructions,
                codeDisplay,
                DialogType.multiAction(List.of(copy, regenerate, back), closeButton("DONE"), 3));
    }

    private void showUnlinkConfirmation(Player player)
    {
        AccountLink link = links.byMinecraft(player.getUniqueId()).orElse(null);
        if (link == null)
        {
            show(player);
            return;
        }

        ActionButton confirm = button(
                "<#FFFFFF><bold>UNLINK</bold></#FFFFFF>",
                "<#ED4245>This disconnects Discord ID " + escape(link.discordId()) + "</#ED4245>",
                action(player, this::unlink));
        ActionButton cancel = button(
                "<#57F287><bold>KEEP LINKED</bold></#57F287>",
                "<#E3E5E8>Return without changing your account</#E3E5E8>",
                action(player, this::show));

        player.showDialog(dialog(
                "<#ED4245><bold>UNLINK DISCORD?</bold></#ED4245>",
                Material.BARRIER,
                mini("<#E3E5E8>You will lose your linked Discord identity in Minecraft chat. "
                        + "You can link again later with a new code.</#E3E5E8>"),
                mini("<#FEE75C><bold>This only removes the account connection; it does not affect either account.</bold></#FEE75C>"),
                DialogType.confirmation(confirm, cancel)));
    }

    private void unlink(Player player)
    {
        if (!settings.playerCanUnlink())
        {
            player.sendMessage(error("Players cannot unlink accounts on this server."));
            show(player);
            return;
        }
        links.removePending(player.getUniqueId());
        boolean removed = links.repository().unlinkMinecraft(player.getUniqueId()).isPresent();
        player.sendMessage(removed
                ? mini("<#57F287><bold>✓</bold> Your Discord account was unlinked.</#57F287>")
                : mini("<#FEE75C>Your account was already unlinked.</#FEE75C>"));
        show(player);
    }

    private Dialog dialog(
            String title,
            Material icon,
            Component first,
            Component second,
            io.papermc.paper.registry.data.dialog.type.DialogType type)
    {
        return dialog(title, icon, List.of(first, second), type);
    }

    private Dialog dialog(
            String title,
            Material icon,
            Component first,
            Component second,
            Component third,
            io.papermc.paper.registry.data.dialog.type.DialogType type)
    {
        return dialog(title, icon, List.of(first, second, third), type);
    }

    private Dialog dialog(
            String title,
            Material icon,
            List<Component> content,
            io.papermc.paper.registry.data.dialog.type.DialogType type)
    {
        List<DialogBody> body = new java.util.ArrayList<>();
        if (icon != null)
        {
            body.add(DialogBody.item(
                    new ItemStack(icon),
                    null,
                    false,
                    false,
                    42,
                    42));
        }
        content.forEach(component -> body.add(DialogBody.plainMessage(component, BODY_WIDTH)));
        DialogBase base = DialogBase.builder(mini(title))
                .externalTitle(mini(title))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body)
                .build();
        return Dialog.create(builder -> builder.empty().base(base).type(type));
    }

    private ActionButton button(String label, String tooltip, DialogAction action)
    {
        return ActionButton.create(mini(label), mini(tooltip), BUTTON_WIDTH, action);
    }

    private ActionButton closeButton(String label)
    {
        return ActionButton.create(
                mini("<#FEE75C>" + escape(label) + "</#FEE75C>"),
                mini("<#DCDDDE>Close this screen</#DCDDDE>"),
                100,
                DialogAction.customClick(
                        (response, audience) -> audience.closeDialog(),
                        ClickCallback.Options.builder()
                                .uses(1)
                                .lifetime(Duration.ofMinutes(10))
                                .build()));
    }

    private DialogAction action(Player owner, Consumer<Player> callback)
    {
        UUID ownerId = owner.getUniqueId();
        return DialogAction.customClick(
                (response, audience) ->
                {
                    if (audience instanceof Player player && player.getUniqueId().equals(ownerId))
                    {
                        platform.executeEntity(player, () -> callback.accept(player));
                    }
                },
                ClickCallback.Options.builder()
                        .uses(1)
                        .lifetime(Duration.ofMinutes(10))
                        .build());
    }

    private static Component mini(String input)
    {
        return MINI_MESSAGE.deserialize(input);
    }

    private static String escape(String input)
    {
        return MINI_MESSAGE.escapeTags(input == null ? "unknown" : input);
    }

    private static Component error(String message)
    {
        return Component.text(message, NamedTextColor.RED);
    }
}
