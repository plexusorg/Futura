package dev.plex.futura.bot.listener;

import dev.plex.futura.FuturaPlugin;
import dev.plex.futura.bot.FuturaBot;
import dev.plex.futura.bot.command.CommandContext;
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
            CommandContext context = new CommandContext(event);
            String subcommand = event.getSubcommandName();

            try {
                if (subcommand != null) {
                    command.executeSubcommand(subcommand, context);
                } else {
                    command.execute(context);
                }
            } catch (Exception e) {
                e.printStackTrace();

                if (event.isAcknowledged()) {
                    event.getHook().sendMessage("Something went wrong while executing this command.").setEphemeral(true).queue();
                } else {
                    context.replyEphemeral("Something went wrong while executing this command.");
                }
            }
        });
    }
}
