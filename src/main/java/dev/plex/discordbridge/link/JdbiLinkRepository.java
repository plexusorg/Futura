package dev.plex.discordbridge.link;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;

public final class JdbiLinkRepository implements LinkRepository
{
    private final Jdbi jdbi;
    private final String table;

    public JdbiLinkRepository(Jdbi jdbi, String table)
    {
        if (!table.matches("[a-zA-Z0-9_]+"))
        {
            throw new IllegalArgumentException("Unsafe link table name: " + table);
        }
        this.jdbi = jdbi;
        this.table = table;
    }

    @Override
    public Optional<AccountLink> byMinecraft(UUID minecraftId)
    {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT minecraft_uuid, minecraft_name, discord_id, linked_at FROM " + table
                                + " WHERE minecraft_uuid = :minecraft")
                .bind("minecraft", minecraftId.toString())
                .map((resultSet, context) -> map(resultSet))
                .findFirst());
    }

    @Override
    public Optional<AccountLink> byDiscord(String discordId)
    {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT minecraft_uuid, minecraft_name, discord_id, linked_at FROM " + table
                                + " WHERE discord_id = :discord")
                .bind("discord", discordId)
                .map((resultSet, context) -> map(resultSet))
                .findFirst());
    }

    @Override
    public List<AccountLink> all()
    {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT minecraft_uuid, minecraft_name, discord_id, linked_at FROM " + table
                                + " ORDER BY minecraft_name")
                .map((resultSet, context) -> map(resultSet))
                .list());
    }

    @Override
    public AccountLink forceLink(UUID minecraftId, String minecraftName, String discordId)
    {
        AccountLink link = new AccountLink(
                minecraftId,
                minecraftName,
                discordId,
                Instant.now().getEpochSecond());
        jdbi.useTransaction(handle ->
        {
            handle.createUpdate("DELETE FROM " + table + " WHERE minecraft_uuid = :minecraft OR discord_id = :discord")
                    .bind("minecraft", minecraftId.toString())
                    .bind("discord", discordId)
                    .execute();
            handle.createUpdate("INSERT INTO " + table
                            + " (minecraft_uuid, minecraft_name, discord_id, linked_at)"
                            + " VALUES (:minecraft, :name, :discord, :linkedAt)")
                    .bind("minecraft", minecraftId.toString())
                    .bind("name", minecraftName)
                    .bind("discord", discordId)
                    .bind("linkedAt", link.linkedAtEpochSecond())
                    .execute();
        });
        return link;
    }

    @Override
    public Optional<AccountLink> unlinkMinecraft(UUID minecraftId)
    {
        return jdbi.inTransaction(handle ->
        {
            Optional<AccountLink> existing = handle.createQuery(
                            "SELECT minecraft_uuid, minecraft_name, discord_id, linked_at FROM " + table
                                    + " WHERE minecraft_uuid = :minecraft")
                    .bind("minecraft", minecraftId.toString())
                    .map((resultSet, context) -> map(resultSet))
                    .findFirst();
            existing.ifPresent(link -> handle.createUpdate(
                            "DELETE FROM " + table + " WHERE minecraft_uuid = :minecraft")
                    .bind("minecraft", minecraftId.toString())
                    .execute());
            return existing;
        });
    }

    @Override
    public Optional<AccountLink> unlinkDiscord(String discordId)
    {
        return jdbi.inTransaction(handle ->
        {
            Optional<AccountLink> existing = handle.createQuery(
                            "SELECT minecraft_uuid, minecraft_name, discord_id, linked_at FROM " + table
                                    + " WHERE discord_id = :discord")
                    .bind("discord", discordId)
                    .map((resultSet, context) -> map(resultSet))
                    .findFirst();
            existing.ifPresent(link -> handle.createUpdate(
                            "DELETE FROM " + table + " WHERE discord_id = :discord")
                    .bind("discord", discordId)
                    .execute());
            return existing;
        });
    }

    private static AccountLink map(ResultSet resultSet) throws SQLException
    {
        return new AccountLink(
                UUID.fromString(resultSet.getString("minecraft_uuid")),
                resultSet.getString("minecraft_name"),
                resultSet.getString("discord_id"),
                resultSet.getLong("linked_at"));
    }
}
