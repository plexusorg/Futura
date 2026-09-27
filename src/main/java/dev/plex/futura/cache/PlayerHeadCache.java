package dev.plex.futura.cache;

import dev.plex.futura.FuturaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class PlayerHeadCache {

    private final Duration ttl = Duration.ofHours(1);
    private final ConcurrentHashMap<UUID, CachedPlayerHead> cache = new ConcurrentHashMap<>();
    private final Path cacheDirectory;

    public PlayerHeadCache(FuturaPlugin plugin) {
        this.cacheDirectory = plugin.getDataPath().resolve("cache").resolve("heads");
    }

    public void initialize() throws IOException {
        Files.createDirectories(cacheDirectory);
        cleanup();
    }

    public BufferedImage get(UUID uuid) {
        CachedPlayerHead cached = cache.get(uuid);
        if (cached != null) {
            if (cached.expired()) {
                deleteFromDisk(uuid);
                return null;
            }

            return cached.image();
        }

        return loadFromDisk(uuid);
    }

    public void put(UUID uuid, BufferedImage image) {
        cache.put(uuid, new CachedPlayerHead(image, Instant.now().plus(ttl)));
        saveToDisk(uuid, image);
    }

    private void cleanup() {
        try (Stream<Path> paths = Files.list(cacheDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".png"))
                    .forEach(filePath -> {
                        try {
                            if (isExpired(filePath)) {
                                Files.deleteIfExists(filePath);
                            }
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private BufferedImage loadFromDisk(UUID uuid) {
        Path path = getPath(uuid);
        if (!Files.exists(path)) return null;

        try {
            if (isExpired(path)) {
                Files.delete(path);
                return null;
            }

            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) return null;

            FileTime time = Files.getLastModifiedTime(path);
            Instant expires = time.toInstant().plus(ttl);
            cache.put(uuid, new CachedPlayerHead(image, expires));

            return image;
        } catch (IOException ignored) {
            try {
                Files.delete(path);
            } catch (IOException _) {
            }

            return null;
        }
    }

    private void saveToDisk(UUID uuid, BufferedImage image) {
        Path path = getPath(uuid);
        try {
            ImageIO.write(image, "png", path.toFile());
        } catch (IOException ignored) {
        }
    }

    private void deleteFromDisk(UUID uuid) {
        Path path = getPath(uuid);
        try {
            Files.delete(path);
            cache.remove(uuid);
        } catch (IOException ignored) {
        }
    }

    private Path getPath(UUID uuid) {
        return cacheDirectory.resolve(uuid.toString() + ".png");
    }

    private boolean isExpired(Path path) throws IOException {
        FileTime time = Files.getLastModifiedTime(path);
        Instant expires = time.toInstant().plus(ttl);
        return Instant.now().isAfter(expires);
    }
}
