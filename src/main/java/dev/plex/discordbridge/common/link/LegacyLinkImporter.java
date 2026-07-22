package dev.plex.discordbridge.common.link;

import dev.plex.discordbridge.common.platform.BridgePlatform;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LegacyLinkImporter
{
    private LegacyLinkImporter()
    {
    }

    public static void importIfPresent(BridgePlatform platform, LinkRepository repository)
    {
        File legacyFile = new File(platform.dataFolder(), "links.yml");
        if (!legacyFile.isFile())
        {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyFile);
        ConfigurationSection links = yaml.getConfigurationSection("links");
        int imported = 0;
        if (links != null)
        {
            for (String key : links.getKeys(false))
            {
                try
                {
                    UUID minecraftId = UUID.fromString(key);
                    String path = "links." + key;
                    String discordId = yaml.getString(path + ".discord-id", "").trim();
                    if (discordId.isBlank())
                    {
                        continue;
                    }
                    repository.forceLink(
                            minecraftId,
                            yaml.getString(path + ".minecraft-name", minecraftId.toString()),
                            discordId);
                    imported++;
                }
                catch (IllegalArgumentException exception)
                {
                    platform.warn("Skipping an invalid legacy account link for {0}", key);
                }
            }
        }

        File migratedFile = new File(platform.dataFolder(), "links.yml.migrated");
        try
        {
            Files.move(
                    legacyFile.toPath(),
                    migratedFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            platform.info("Imported {0} legacy account link(s) into SQL storage", imported);
        }
        catch (IOException exception)
        {
            platform.warn("Imported legacy links but could not rename links.yml: {0}", exception.getMessage());
        }
    }
}
