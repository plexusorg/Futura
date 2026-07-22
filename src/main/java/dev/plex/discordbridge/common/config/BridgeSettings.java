package dev.plex.discordbridge.common.config;

import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

public record BridgeSettings(
        boolean enabled,
        String token,
        String activity,
        String guildId,
        Route chat,
        Route staffChat,
        ConsoleRoute console,
        boolean ignoreBots,
        boolean ignoreWebhooks,
        boolean includeAttachments,
        Lifecycle lifecycle,
        Linking linking,
        String chatToDiscordFormat,
        String staffToDiscordFormat,
        String discordToChatFormat,
        String discordToStaffFormat)
{
    public static BridgeSettings from(ConfigurationSection config)
    {
        return new BridgeSettings(
                config.getBoolean("bot.enabled", true),
                config.getString("bot.token", "").trim(),
                config.getString("bot.activity", "Minecraft chat").trim(),
                config.getString("guild-id", "").trim(),
                route(config, "channels.chat"),
                route(config, "channels.staff-chat"),
                consoleRoute(config),
                config.getBoolean("discord.ignore-bots", true),
                config.getBoolean("discord.ignore-webhooks", true),
                config.getBoolean("discord.include-attachments", true),
                lifecycle(config),
                linking(config),
                config.getString("formats.chat-to-discord", "**{player}**: {message}"),
                config.getString("formats.staff-to-discord", "**[Staff] {player}**: {message}"),
                config.getString("formats.discord-to-chat", "<dark_gray>[<blue>Discord</blue>]</dark_gray> <aqua>{user}</aqua><gray>: </gray><white>{message}</white>"),
                config.getString("formats.discord-to-staff", "<dark_gray>[<dark_aqua>Discord Staff</dark_aqua>]</dark_gray> <aqua>{user}</aqua><gray>: </gray><white>{message}</white>"));
    }

    private static Lifecycle lifecycle(ConfigurationSection config)
    {
        return new Lifecycle(
                config.getBoolean("server-messages.enabled", true),
                config.getString("server-messages.route", "chat").trim(),
                config.getString("server-messages.started", "🟢 **Server started.**"),
                config.getString("server-messages.stopped", "🔴 **Server stopped.**"),
                Math.max(1, config.getInt("server-messages.shutdown-timeout-seconds", 3)));
    }

    private static Linking linking(ConfigurationSection config)
    {
        int codeLength = Math.clamp(config.getInt("linking.code-length", 6), 4, 12);
        long expirySeconds = Math.max(30L, config.getLong("linking.code-expiry-seconds", 300L));
        List<String> playerAliases = config.getStringList("linking.player-command.aliases").stream()
                .map(alias -> alias.trim().toLowerCase(Locale.ROOT))
                .filter(alias -> alias.matches("[a-z0-9_-]+"))
                .toList();
        return new Linking(
                config.getBoolean("linking.enabled", true),
                codeLength,
                expirySeconds,
                config.getBoolean("linking.allow-minecraft-relink", false),
                config.getBoolean("linking.allow-discord-relink", false),
                config.getBoolean("linking.player-command.allow-unlink", true),
                commandName(config.getString("linking.player-command.name", "discordlink"), "discordlink"),
                playerAliases.isEmpty() ? List.of("linkdiscord") : List.copyOf(playerAliases),
                commandName(config.getString("linking.admin-command.name", "discordlinkadmin"), "discordlinkadmin"),
                config.getBoolean("linking.use-linked-name-in-chat", true),
                config.getString("linking.linked-name-format", "{minecraft} ({discord})"),
                config.getString("linking.dm-success", "Your Discord account is now linked to **{minecraft}**."),
                config.getString("linking.dm-invalid-code", "That link code is invalid or expired. Generate a new code in Minecraft."));
    }

    private static String commandName(String configured, String fallback)
    {
        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_-]+") ? normalized : fallback;
    }

    private static Route route(ConfigurationSection config, String path)
    {
        return new Route(
                config.getString(path + ".id", "").trim(),
                config.getString(path + ".fallback-name", "").trim(),
                config.getBoolean(path + ".minecraft-to-discord", true),
                config.getBoolean(path + ".discord-to-minecraft", true),
                config.getString(path + ".receive-permission", "plex.adminchat").trim(),
                config.getBoolean(path + ".send-to-console", true));
    }

    private static ConsoleRoute consoleRoute(ConfigurationSection config)
    {
        String path = "channels.console";
        String permission = config.getString(path + ".permission", "plex.discord.console").trim();
        if (permission.isBlank())
        {
            permission = "plex.discord.console";
        }
        return new ConsoleRoute(
                config.getString(path + ".id", "").trim(),
                config.getString(path + ".fallback-name", "console").trim(),
                config.getBoolean(path + ".server-output-to-discord", false),
                config.getBoolean(path + ".discord-to-server-commands", false),
                permission,
                Math.clamp(config.getLong(path + ".output.batch-interval-ms", 1_000L), 250L, 10_000L),
                Math.clamp(config.getInt(path + ".output.max-lines-per-batch", 50), 1, 100),
                Math.clamp(config.getInt(path + ".output.max-queued-lines", 500), 50, 5_000));
    }

    public record Route(
            String channelId,
            String fallbackName,
            boolean minecraftToDiscord,
            boolean discordToMinecraft,
            String receivePermission,
            boolean sendToConsole)
    {
    }

    public record ConsoleRoute(
            String channelId,
            String fallbackName,
            boolean serverOutputToDiscord,
            boolean discordToServerCommands,
            String permission,
            long batchIntervalMillis,
            int maxLinesPerBatch,
            int maxQueuedLines)
    {
        public boolean enabled()
        {
            return serverOutputToDiscord || discordToServerCommands;
        }
    }

    public record Lifecycle(
            boolean enabled,
            String route,
            String started,
            String stopped,
            int shutdownTimeoutSeconds)
    {
    }

    public record Linking(
            boolean enabled,
            int codeLength,
            long codeExpirySeconds,
            boolean allowMinecraftRelink,
            boolean allowDiscordRelink,
            boolean playerCanUnlink,
            String playerCommandName,
            List<String> playerCommandAliases,
            String adminCommandName,
            boolean useLinkedNameInChat,
            String linkedNameFormat,
            String dmSuccess,
            String dmInvalidCode)
    {
    }
}
