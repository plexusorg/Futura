package dev.plex.discordbridge.common.link;

import java.util.UUID;

public record AccountLink(
        UUID minecraftId,
        String minecraftName,
        String discordId,
        long linkedAtEpochSecond)
{
}
