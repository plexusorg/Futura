package dev.plex.futura.bot.command;

import dev.plex.futura.FuturaPlugin;
import dev.plex.futura.image.PlayerListImage;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import java.io.IOException;
import java.util.List;

public class ListCommand extends SlashCommand {

    private final FuturaPlugin plugin;

    public ListCommand(FuturaPlugin plugin) {
        super("list", "Shows online players on the Minecraft server");
        this.plugin = plugin;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        List<? extends Player> players = plugin.getServer().getOnlinePlayers().stream().filter(player -> !isVanished(player)).toList();
        int maxSize = plugin.getServer().getMaxPlayers();

        PlayerListImage.generate(plugin.getPlayerHeadService(), players, maxSize)
                .thenAccept(image -> {
                    event.getHook().sendFiles(FileUpload.fromData(image, "players.png")).queue();
                })
                .exceptionally(error -> {
                    plugin.getLogger().severe("Unable to generate an image: " + error.getMessage());

                    // Send embed as a fallback
                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle(String.format("Online Players (%s/%s)", players.size(), maxSize));
                    embed.setFooter("Join now at play.totalfreedom.tf");

                    if (!players.isEmpty()) {
                        List<String> names = players.stream().map(Player::getName).toList();
                        embed.setDescription("`" + String.join("`, `", names) + "`");
                    }

                    event.getHook().sendMessageEmbeds(embed.build()).queue();
                    return null;
                });
    }

    @Deprecated
    private boolean isVanished(Player player) {
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) return true;
        }
        return false;
    }
}
