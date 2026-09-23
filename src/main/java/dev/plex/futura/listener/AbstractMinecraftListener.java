package dev.plex.futura.listener;

import dev.plex.futura.FuturaPlugin;
import org.bukkit.event.Listener;

public abstract class AbstractMinecraftListener implements Listener {

    protected final FuturaPlugin plugin;

    protected AbstractMinecraftListener(FuturaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
}
