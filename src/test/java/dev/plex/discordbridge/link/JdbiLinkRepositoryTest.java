package dev.plex.discordbridge.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.UUID;
import java.nio.file.Path;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbiLinkRepositoryTest
{
    private JdbiLinkRepository repository;
    private HikariDataSource dataSource;

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void setUp()
    {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("DiscordBridge-Test");
        hikari.setDriverClassName("org.sqlite.JDBC");
        hikari.setJdbcUrl("jdbc:sqlite:" + temporaryDirectory.resolve("links.db"));
        hikari.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(hikari);
        Jdbi jdbi = Jdbi.create(dataSource);
        jdbi.useHandle(handle -> handle.execute("""
                CREATE TABLE discord_bridge_links (
                    minecraft_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                    minecraft_name VARCHAR(64) NOT NULL,
                    discord_id VARCHAR(32) NOT NULL UNIQUE,
                    linked_at BIGINT NOT NULL
                )
                """));
        repository = new JdbiLinkRepository(jdbi, "discord_bridge_links");
    }

    @AfterEach
    void tearDown()
    {
        dataSource.close();
    }

    @Test
    void storesAndLoadsBothDirections()
    {
        UUID minecraftId = UUID.randomUUID();
        repository.forceLink(minecraftId, "PlayerOne", "123456789012345678");

        assertEquals("PlayerOne", repository.byMinecraft(minecraftId).orElseThrow().minecraftName());
        assertEquals(minecraftId, repository.byDiscord("123456789012345678").orElseThrow().minecraftId());
    }

    @Test
    void forceLinkMaintainsOneToOneRelationship()
    {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.forceLink(first, "First", "123456789012345678");
        repository.forceLink(second, "Second", "123456789012345678");

        assertTrue(repository.byMinecraft(first).isEmpty());
        assertEquals(second, repository.byDiscord("123456789012345678").orElseThrow().minecraftId());
        assertEquals(1, repository.all().size());
    }

    @Test
    void unlinkReturnsRemovedLink()
    {
        UUID minecraftId = UUID.randomUUID();
        repository.forceLink(minecraftId, "PlayerOne", "123456789012345678");

        assertEquals("123456789012345678", repository.unlinkMinecraft(minecraftId).orElseThrow().discordId());
        assertTrue(repository.all().isEmpty());
    }
}
