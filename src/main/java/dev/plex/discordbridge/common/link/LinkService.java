package dev.plex.discordbridge.common.link;

import dev.plex.discordbridge.common.config.BridgeSettings;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LinkService
{
    private static final char[] CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final BridgeSettings.Linking settings;
    private final LinkRepository repository;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingLink> pendingByCode = new ConcurrentHashMap<>();
    private final Map<UUID, String> codeByMinecraft = new ConcurrentHashMap<>();

    public LinkService(BridgeSettings.Linking settings, LinkRepository repository)
    {
        this.settings = settings;
        this.repository = repository;
    }

    public CodeIssue issueCode(UUID minecraftId, String minecraftName)
    {
        if (!settings.enabled())
        {
            return new CodeIssue(CodeStatus.DISABLED, "", 0, repository.byMinecraft(minecraftId).orElse(null));
        }

        Optional<AccountLink> existing = repository.byMinecraft(minecraftId);
        if (existing.isPresent() && !settings.allowMinecraftRelink())
        {
            return new CodeIssue(CodeStatus.ALREADY_LINKED, "", 0, existing.get());
        }

        removePending(minecraftId);
        purgeExpired();
        String code;
        do
        {
            code = randomCode();
        }
        while (pendingByCode.containsKey(code));

        long expiresAt = Instant.now().getEpochSecond() + settings.codeExpirySeconds();
        pendingByCode.put(code, new PendingLink(minecraftId, minecraftName, expiresAt));
        codeByMinecraft.put(minecraftId, code);
        return new CodeIssue(CodeStatus.CREATED, code, settings.codeExpirySeconds(), existing.orElse(null));
    }

    public ClaimResult claim(String suppliedCode, String discordId)
    {
        if (!settings.enabled())
        {
            return new ClaimResult(ClaimStatus.DISABLED, null);
        }

        String code = suppliedCode.trim().toUpperCase(Locale.ROOT);
        PendingLink pending = pendingByCode.remove(code);
        if (pending == null)
        {
            purgeExpired();
            return new ClaimResult(ClaimStatus.INVALID_OR_EXPIRED, null);
        }
        codeByMinecraft.remove(pending.minecraftId(), code);
        if (pending.expiresAtEpochSecond() < Instant.now().getEpochSecond())
        {
            return new ClaimResult(ClaimStatus.INVALID_OR_EXPIRED, null);
        }

        Optional<AccountLink> minecraftLink = repository.byMinecraft(pending.minecraftId());
        if (minecraftLink.isPresent() && !settings.allowMinecraftRelink())
        {
            return new ClaimResult(ClaimStatus.MINECRAFT_ALREADY_LINKED, minecraftLink.get());
        }
        Optional<AccountLink> discordLink = repository.byDiscord(discordId);
        if (discordLink.isPresent()
                && !discordLink.get().minecraftId().equals(pending.minecraftId())
                && !settings.allowDiscordRelink())
        {
            return new ClaimResult(ClaimStatus.DISCORD_ALREADY_LINKED, discordLink.get());
        }

        AccountLink link = repository.forceLink(pending.minecraftId(), pending.minecraftName(), discordId);
        return new ClaimResult(ClaimStatus.LINKED, link);
    }

    public Optional<AccountLink> byMinecraft(UUID minecraftId)
    {
        return repository.byMinecraft(minecraftId);
    }

    public Optional<AccountLink> byDiscord(String discordId)
    {
        return repository.byDiscord(discordId);
    }

    public LinkRepository repository()
    {
        return repository;
    }

    public void removePending(UUID minecraftId)
    {
        String code = codeByMinecraft.remove(minecraftId);
        if (code != null)
        {
            pendingByCode.remove(code);
        }
    }

    private void purgeExpired()
    {
        long now = Instant.now().getEpochSecond();
        pendingByCode.entrySet().removeIf(entry ->
        {
            if (entry.getValue().expiresAtEpochSecond() >= now)
            {
                return false;
            }
            codeByMinecraft.remove(entry.getValue().minecraftId(), entry.getKey());
            return true;
        });
    }

    private String randomCode()
    {
        StringBuilder code = new StringBuilder(settings.codeLength());
        for (int i = 0; i < settings.codeLength(); i++)
        {
            code.append(CODE_CHARACTERS[random.nextInt(CODE_CHARACTERS.length)]);
        }
        return code.toString();
    }

    private record PendingLink(UUID minecraftId, String minecraftName, long expiresAtEpochSecond)
    {
    }

    public record CodeIssue(CodeStatus status, String code, long expiresInSeconds, AccountLink existingLink)
    {
    }

    public enum CodeStatus
    {
        CREATED,
        DISABLED,
        ALREADY_LINKED
    }

    public record ClaimResult(ClaimStatus status, AccountLink link)
    {
    }

    public enum ClaimStatus
    {
        LINKED,
        DISABLED,
        INVALID_OR_EXPIRED,
        MINECRAFT_ALREADY_LINKED,
        DISCORD_ALREADY_LINKED
    }
}
