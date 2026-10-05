package dev.plex.futura.bot.command;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;

public class CommandOptionResolver {

    public static OptionType getOptionType(Class<?> type) {
        if (type == String.class) {
            return OptionType.STRING;
        }

        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            return OptionType.INTEGER;
        }

        if (type == boolean.class || type == Boolean.class) {
            return OptionType.BOOLEAN;
        }

        if (type == float.class || type == Float.class || type == double.class || type == Double.class) {
            return OptionType.NUMBER;
        }

        if (type == User.class) {
            return OptionType.USER;
        }

        if (type == Role.class) {
            return OptionType.ROLE;
        }

        if (GuildChannel.class.isAssignableFrom(type)) {
            return OptionType.CHANNEL;
        }

        throw new IllegalArgumentException("Unsupported command option type: " + type.getName());
    }

    public static Object resolve(Class<?> type, OptionMapping option) {
        if (type == String.class) {
            return option.getAsString();
        }

        if (type == int.class || type == Integer.class) {
            return option.getAsInt();
        }

        if (type == long.class || type == Long.class) {
            return option.getAsLong();
        }

        if (type == float.class || type == Float.class) {
            return (float) option.getAsDouble();
        }

        if (type == double.class || type == Double.class) {
            return option.getAsDouble();
        }

        if (type == boolean.class || type == Boolean.class) {
            return option.getAsBoolean();
        }

        if (type == User.class) {
            return option.getAsUser();
        }

        if (type == Member.class) {
            return option.getAsMember();
        }

        if (type == Role.class) {
            return option.getAsRole();
        }

        if (GuildChannel.class.isAssignableFrom(type)) {
            GuildChannel channel = option.getAsChannel();
            if (!type.isInstance(channel)) {
                throw new IllegalArgumentException("Unexpected channel option... Got " + channel.getType() + " but expected " + type.getSimpleName());
            }
            return channel;
        }

        throw new IllegalArgumentException("Unsupported command option type: " + type.getName());
    }
}
