package dev.plex.futura.integration;

import net.dv8tion.jda.api.hooks.ListenerAdapter;

public abstract class IntegrationBotListener extends ListenerAdapter {

    private final Integration integration;

    public IntegrationBotListener(Integration integration) {
        this.integration = integration;
    }
}
