package dev.plex.discordbridge.common.service;

import java.util.UUID;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Resolves a permission for an online or offline Minecraft UUID through Bukkit/Vault. */
public final class OfflinePermissionResolver
{
    private OfflinePermissionResolver()
    {
    }

    public static Result check(UUID minecraftId, String permission)
    {
        Player online = Bukkit.getPlayer(minecraftId);
        if (online != null)
        {
            return online.hasPermission(permission) ? Result.GRANTED : Result.DENIED;
        }

        try
        {
            RegisteredServiceProvider<Permission> registration =
                    Bukkit.getServicesManager().getRegistration(Permission.class);
            if (registration == null)
            {
                return Result.PROVIDER_UNAVAILABLE;
            }
            OfflinePlayer player = Bukkit.getOfflinePlayer(minecraftId);
            return registration.getProvider().playerHas(null, player, permission)
                    ? Result.GRANTED
                    : Result.DENIED;
        }
        catch (LinkageError exception)
        {
            return Result.PROVIDER_UNAVAILABLE;
        }
    }

    public enum Result
    {
        GRANTED,
        DENIED,
        PROVIDER_UNAVAILABLE
    }
}
