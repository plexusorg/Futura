package dev.plex.futura.integration;

import dev.plex.futura.FuturaPlugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class IntegrationConfig extends YamlConfiguration {

    private final File file;

    public IntegrationConfig(FuturaPlugin plugin, String fileName) {
        this.file = new File(plugin.getDataFolder(), "integrations" + File.separator + fileName);

        if (!file.exists()) {
            plugin.saveResource("integrations" + File.separator + fileName, false);
        }
    }

    public void load() {
        options().parseComments(true);
        try {
            super.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("Error loading config file", e);
        }
    }

    public void save() {
        try {
            super.save(file);
        } catch (IOException e) {
            throw new IllegalStateException("Error saving config file", e);
        }
    }
}
