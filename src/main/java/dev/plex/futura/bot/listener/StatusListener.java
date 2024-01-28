package dev.plex.futura.bot.listener;

import dev.plex.futura.Futura;
import net.dv8tion.jda.api.entities.Activity;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class StatusListener implements Listener
{
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        final String status = Futura.plugin().getConfig().getString("bot.status", "").replace("%players%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        Futura.plugin().botHandler().jda().getPresence().setActivity(Activity.customStatus(status));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        final String status = Futura.plugin().getConfig().getString("bot.status", "").replace("%players%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        Futura.plugin().botHandler().jda().getPresence().setActivity(Activity.customStatus(status));
    }
}
