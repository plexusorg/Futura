package dev.plex.futura.integration;

public interface Integration {

    String name();

    boolean isAvailable();

    boolean enable();

    void disable();

    IntegrationBotListener getBotListener();
}
