package dev.plex.futura.bot.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public abstract class SlashCommand {

    private final String name;
    private final String description;

    private final HashMap<String, Method> subcommands = new HashMap<>();

    public SlashCommand(String name, String description) {
        this.name = name;
        this.description = description;

        discoverSubcommands();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public HashMap<String, Method> getSubcommands() {
        return subcommands;
    }

    private void discoverSubcommands() {
        for (Method method : getClass().getDeclaredMethods()) {
            Subcommand subcommand = method.getAnnotation(Subcommand.class);
            if (subcommand == null) {
                return;
            }

            validateSubcommand(method);

            Method existing = subcommands.putIfAbsent(subcommand.name(), method);
            if (existing != null) {
                throw new IllegalStateException("Duplicate subcommand name: " + subcommand.name());
            }

            method.setAccessible(true);
        }
    }

    private void validateSubcommand(Method method) {
        if (method.getReturnType() != void.class) {
            throw new IllegalStateException("Subcommand method '" + method.getName() + "' must return void");
        }

        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0 || parameters[0].getType() != CommandContext.class) {
            throw new IllegalStateException("Subcommand method '" + method.getName() + "' must have CommandContext as its first parameter");
        }

        // CommandContext is always 0, so we start from 1
        for (int i = 1; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Option option = parameter.getAnnotation(Option.class);
            if (option == null) {
                throw new IllegalStateException("Parameter '" + parameter.getName() + "' in subcommand method '" + method.getName() + "' must have a @Option annotation");
            }

            // Validate the option type
            CommandOptionResolver.getOptionType(parameter.getType());

            // Don't allow primitive parameters if it's optional - can't represent null if optional
            if (!option.required() && parameter.getType().isPrimitive()) {
                throw new IllegalStateException("Optional parameter '" + parameter.getName() + "' in subcommand method '" + method.getName() + "' must use a boxed type");
            }
        }
    }

    public SlashCommandData buildCommandData() {
        SlashCommandData data = Commands.slash(name, description);

        for (Method method : subcommands.values()) {
            Subcommand annotation = method.getAnnotation(Subcommand.class);
            SubcommandData subcommand = new SubcommandData(annotation.name(), annotation.description());

            Parameter[] parameters = method.getParameters();
            for (int i = 1; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                Option option = parameter.getAnnotation(Option.class);
                OptionType type = CommandOptionResolver.getOptionType(parameter.getType());
                subcommand.addOption(type, option.name(), option.description(), option.required());
            }

            data.addSubcommands(subcommand);
        }

        return data;
    }

    // Default as not all commands are able to execute without subcommands (see purge as an example)
    public void execute(CommandContext context) {
        context.replyEphemeral("This command requires a subcommand to be defined, please ensure they're filled out");
    }

    public void executeSubcommand(String name, CommandContext context) {
        Method method = subcommands.get(name);
        if (method == null) {
            throw new IllegalArgumentException("No subcommand named '" + name + "' found");
        }

        Object[] args = resolveArguments(method, context);
        try {
            method.invoke(this, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }

            throw new RuntimeException("Subcommand '" + name + "' failed", cause);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Unable to invoke subcommand '" + name + "'", ex);
        }
    }

    private Object[] resolveArguments(Method method, CommandContext context) {
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];

        arguments[0] = context;
        SlashCommandInteractionEvent event = context.event();

        for (int i = 1; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Option option = parameter.getAnnotation(Option.class);

            OptionMapping mapping = event.getOption(option.name());
            if (mapping == null) {
                if (option.required()) {
                    throw new IllegalArgumentException("Required option '" + option.name() + "' was not provided");
                }

                arguments[i] = null;
                continue;
            }

            arguments[i] = CommandOptionResolver.resolve(parameter.getType(), mapping);
        }

        return arguments;
    }
}
