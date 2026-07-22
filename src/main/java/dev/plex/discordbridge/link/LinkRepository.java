package dev.plex.discordbridge.link;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkRepository
{
    Optional<AccountLink> byMinecraft(UUID minecraftId);

    Optional<AccountLink> byDiscord(String discordId);

    List<AccountLink> all();

    AccountLink forceLink(UUID minecraftId, String minecraftName, String discordId);

    Optional<AccountLink> unlinkMinecraft(UUID minecraftId);

    Optional<AccountLink> unlinkDiscord(String discordId);
}
