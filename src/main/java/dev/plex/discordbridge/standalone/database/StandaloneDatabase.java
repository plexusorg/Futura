package dev.plex.discordbridge.standalone.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jdbi.v3.core.Jdbi;

public final class StandaloneDatabase implements AutoCloseable
{
    public static final String LINKS_TABLE = "discord_bridge_links";
    private static final String HISTORY_TABLE = "discord_bridge_schema_history";

    private final HikariDataSource dataSource;
    private final Jdbi jdbi;
    private final Dialect dialect;

    public StandaloneDatabase(JavaPlugin plugin, ConfigurationSection config) throws SQLException, IOException
    {
        dialect = Dialect.from(config.getString("database.storage", "sqlite"));
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("Plex-DiscordBridge");
        hikari.setConnectionTimeout(Math.max(1_000L, config.getLong("database.pool.connection-timeout-ms", 30_000L)));
        int maximumPoolSize = dialect == Dialect.SQLITE
                ? 1
                : Math.max(1, config.getInt("database.pool.maximum-size", 10));
        int minimumIdle = Math.clamp(config.getInt("database.pool.minimum-idle", 1), 0, maximumPoolSize);
        hikari.setMaximumPoolSize(maximumPoolSize);
        hikari.setMinimumIdle(minimumIdle);
        configureConnection(plugin, config, hikari);
        dataSource = new HikariDataSource(hikari);
        try
        {
            migrate(plugin, "001_links");
            jdbi = Jdbi.create(dataSource);
        }
        catch (SQLException | IOException exception)
        {
            dataSource.close();
            throw exception;
        }
    }

    public Jdbi jdbi()
    {
        return jdbi;
    }

    @Override
    public void close()
    {
        dataSource.close();
    }

    private void configureConnection(JavaPlugin plugin, ConfigurationSection config, HikariConfig hikari) throws IOException
    {
        switch (dialect)
        {
            case SQLITE ->
            {
                Files.createDirectories(plugin.getDataFolder().toPath());
                String filename = config.getString("database.sqlite.file", "links.db");
                Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
                Path databaseFile = dataFolder.resolve(filename).normalize();
                if (!databaseFile.startsWith(dataFolder))
                {
                    throw new IOException("database.sqlite.file must stay inside the plugin data folder");
                }
                hikari.setDriverClassName("org.sqlite.JDBC");
                hikari.setJdbcUrl("jdbc:sqlite:" + databaseFile);
            }
            case MARIADB ->
            {
                hikari.setDriverClassName("org.mariadb.jdbc.Driver");
                hikari.setJdbcUrl(jdbcUrl("mariadb", config, 3306));
                hikari.setUsername(config.getString("database.username", "root"));
                hikari.setPassword(config.getString("database.password", ""));
            }
            case POSTGRES ->
            {
                hikari.setDriverClassName("org.postgresql.Driver");
                hikari.setJdbcUrl(jdbcUrl("postgresql", config, 5432));
                hikari.setUsername(config.getString("database.username", "postgres"));
                hikari.setPassword(config.getString("database.password", ""));
            }
        }
    }

    private String jdbcUrl(String scheme, ConfigurationSection config, int defaultPort)
    {
        String host = config.getString("database.host", "127.0.0.1");
        int configuredPort = config.getInt("database.port", 0);
        int port = configuredPort > 0 ? configuredPort : defaultPort;
        String database = config.getString("database.name", "plex_discord_bridge");
        return "jdbc:" + scheme + "://" + host + ":" + port + "/" + database;
    }

    private void migrate(JavaPlugin plugin, String version) throws SQLException, IOException
    {
        String historyTable = quote(HISTORY_TABLE);
        try (Connection connection = dataSource.getConnection())
        {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement())
            {
                statement.execute("CREATE TABLE IF NOT EXISTS " + historyTable
                        + " (version VARCHAR(100) NOT NULL PRIMARY KEY, installed_at BIGINT NOT NULL)");
                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT version FROM " + historyTable + " WHERE version = '" + version + "'"))
                {
                    if (resultSet.next())
                    {
                        connection.rollback();
                        return;
                    }
                }

                String resource = "db/migration/" + dialect.directory + "/" + version + ".sql";
                String script;
                try (InputStream stream = plugin.getResource(resource))
                {
                    if (stream == null)
                    {
                        throw new IOException("Missing database migration " + resource);
                    }
                    script = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                            .replace("{{table:links}}", quote(LINKS_TABLE));
                }
                for (String sql : script.split(";"))
                {
                    if (!sql.isBlank())
                    {
                        statement.execute(sql.trim());
                    }
                }
                statement.executeUpdate("INSERT INTO " + historyTable
                        + " (version, installed_at) VALUES ('" + version + "', " + System.currentTimeMillis() + ")");
                connection.commit();
            }
            catch (SQLException | IOException exception)
            {
                connection.rollback();
                throw exception;
            }
        }
    }

    private String quote(String identifier)
    {
        return dialect == Dialect.MARIADB ? "`" + identifier + "`" : "\"" + identifier + "\"";
    }

    private enum Dialect
    {
        SQLITE("sqlite"),
        MARIADB("mariadb"),
        POSTGRES("postgres");

        private final String directory;

        Dialect(String directory)
        {
            this.directory = directory;
        }

        private static Dialect from(String configured)
        {
            return switch (configured.trim().toLowerCase(Locale.ROOT))
            {
                case "mariadb", "mysql" -> MARIADB;
                case "postgres", "postgresql" -> POSTGRES;
                default -> SQLITE;
            };
        }
    }
}
