package dev.plex.futura.bot.listener;

import dev.plex.futura.FuturaPlugin;
import dev.plex.futura.bot.FuturaBot;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public abstract class AbstractDiscordListener extends ListenerAdapter {

    protected final FuturaPlugin plugin;
    protected final FuturaBot bot;

    protected AbstractDiscordListener(FuturaPlugin plugin, FuturaBot bot) {
        this.plugin = plugin;
        this.bot = bot;
    }
}
