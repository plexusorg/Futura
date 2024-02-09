package dev.plex.futura.bot.linking;

import com.google.common.collect.Maps;
import dev.plex.futura.Futura;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.commons.lang3.RandomStringUtils;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * @author Taah
 * @since 6:51 PM [02-08-2024]
 */
public class LinkingManager
{
    private static final Map<UUID, String> CODES = Maps.newHashMap();
    private final boolean enabled;

    public LinkingManager()
    {
        this.enabled = Futura.plugin().config.getBoolean("discord.linking.enabled", false);
    }

    public void requestCode(Player player)
    {
        if (!Futura.plugin().botHandler().ready())
        {
            player.sendMessage("<red>The bot is not connected / online. Please contact an administrator.");
            return;
        }
        if (!this.enabled)
        {
            player.sendMessage(MiniMessage.miniMessage().deserialize(Futura.plugin().messages.getString("linkingDisabled", "<red>Discord to Minecraft account linking is currently disabled.")));
            return;
        }

        //TODO: Check if user is already linked on cache loaded from database

        final UUID uuid = player.getUniqueId();

        if (CODES.containsKey(uuid))
        {
            player.sendMessage(MiniMessage.miniMessage().deserialize(Futura.plugin().messages.getString("accountLinkingInProgress", "<red>Can't request a new code currently, user has already requested a code. Please try again in a minute.")));
            return;
        }

        final String code = RandomStringUtils.randomAlphanumeric(8).toLowerCase();
        CODES.put(uuid, code);

        final int expirationTime = Futura.plugin().config.getInt("discord.linking.expirationTime", 60);
        final String codeRequested = Futura.plugin().messages.getString("codeRequested", "<green>The code you have requested is %code%. Please do not share this. In order to link your account, message the %bot-name% bot with your code. This code will expire in %expirationTime% seconds.")
                .replace("%code%", code)
                .replace("%bot-name%", Futura.plugin().botHandler().jda().getSelfUser().getName())
                .replace("%expiration-time%", String.valueOf(expirationTime));

        player.sendMessage(MiniMessage.miniMessage().deserialize(codeRequested));
        Futura.plugin().getServer().getScheduler().runTaskLater(Futura.plugin(), () ->
        {
            CODES.remove(uuid);
        }, 20L * expirationTime);
    }

    public UUID matchCode(String code)
    {
        final Map.Entry<UUID, String> uuidStringEntry = CODES.entrySet().stream().filter(entry -> entry.getValue().equals(code)).findFirst().orElse(null);
        if (uuidStringEntry == null)
        {
            return null;
        }
        return uuidStringEntry.getKey();
    }
}
