package dev.plex.futura.bot.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public abstract class SlashCommand {

    private final String name;
    private final String description;

    public SlashCommand(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public SlashCommandData asCommandData() {
        return Commands.slash(this.name, this.description);
    }

    public abstract void execute(SlashCommandInteractionEvent event);
}
