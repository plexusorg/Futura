package dev.plex.futura.bot.listener;

import dev.plex.futura.FuturaPlugin;
import dev.plex.futura.bot.FuturaBot;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jspecify.annotations.NonNull;

public class SlashCommandListener extends AbstractDiscordListener {

    private final FuturaBot bot;

    public SlashCommandListener(FuturaPlugin plugin, FuturaBot bot) {
        super(plugin, bot);
        this.bot = bot;
    }

    @Override
    public void onSlashCommandInteraction(@NonNull SlashCommandInteractionEvent event) {
        bot.getCommands().stream().filter(command -> command.getName().equalsIgnoreCase(event.getName())).findFirst().ifPresent(command -> {
            command.execute(event);
        });
    }
}
