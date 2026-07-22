package dev.plex.discordbridge.link;

import java.util.UUID;

public record AccountLink(
        UUID minecraftId,
        String minecraftName,
        String discordId,
        long linkedAtEpochSecond)
{
}
