package dev.plex.futura.integration;

import dev.plex.futura.FuturaPlugin;
import dev.plex.futura.integration.plex.PlexIntegration;

import java.util.ArrayList;
import java.util.List;

public class IntegrationManager {

    private final FuturaPlugin plugin;
    private final List<Integration> integrations = new ArrayList<>();

    public IntegrationManager(FuturaPlugin plugin) {
        this.plugin = plugin;
    }

    private void register(Integration integration) {
        if (!integration.isAvailable()) {
            plugin.getLogger().info(integration.name() + " not found, skipping...");
            return;
        }

        // if an integration throws an exception, catch it so the plugin doesn't shut down
        try {
            if (integration.enable()) {
                integrations.add(integration);
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("unable to enable integration: " + integration.name() + "... " + ex.getMessage());
        }
    }

    public void load() {
        register(new PlexIntegration(plugin));

        plugin.getLogger().info("Loaded " + integrations.size() + " integrations.");
    }

    public void injectBotListeners() {
        integrations.forEach(integration -> {
            plugin.getBot().addListener(integration.getBotListener());
        });
    }

    public void unload() {
        integrations.forEach(Integration::disable);
        integrations.clear();
    }
}
