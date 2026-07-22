package dev.plex.discordbridge.common.link;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DisabledLinkRepository implements LinkRepository
{
    @Override
    public Optional<AccountLink> byMinecraft(UUID minecraftId)
    {
        return Optional.empty();
    }

    @Override
    public Optional<AccountLink> byDiscord(String discordId)
    {
        return Optional.empty();
    }

    @Override
    public List<AccountLink> all()
    {
        return List.of();
    }

    @Override
    public AccountLink forceLink(UUID minecraftId, String minecraftName, String discordId)
    {
        throw new IllegalStateException("Discord account linking is disabled");
    }

    @Override
    public Optional<AccountLink> unlinkMinecraft(UUID minecraftId)
    {
        return Optional.empty();
    }

    @Override
    public Optional<AccountLink> unlinkDiscord(String discordId)
    {
        return Optional.empty();
    }
}
