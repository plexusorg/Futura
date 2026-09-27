package dev.plex.futura.service;

import dev.plex.futura.FuturaPlugin;
import dev.plex.futura.cache.PlayerHeadCache;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PlayerHeadService {

    private static final String API_URL = "https://visage.surgeplay.com/face/64/%s.png";
    private final FuturaPlugin plugin;
    private final HttpClient client;
    private final PlayerHeadCache playerHeadCache;
    private final ConcurrentMap<UUID, CompletableFuture<BufferedImage>> loading = new ConcurrentHashMap<>();

    public PlayerHeadService(FuturaPlugin plugin, PlayerHeadCache playerHeadCache) {
        this.plugin = plugin;
        this.client = HttpClient.newHttpClient();
        this.playerHeadCache = playerHeadCache;
    }

    public CompletableFuture<BufferedImage> getHead(UUID uuid) {
        BufferedImage image = playerHeadCache.get(uuid);
        if (image != null) {
            return CompletableFuture.completedFuture(image);
        }

        return loading.computeIfAbsent(uuid, this::load);
    }

    private CompletableFuture<BufferedImage> load(UUID uuid) {
        return fetchHead(uuid).thenApply(head -> {
            playerHeadCache.put(uuid, head);
            return head;
        }).whenComplete((ignored, error) -> {
            loading.remove(uuid);
            if (error != null) {
                plugin.getLogger().severe(error.getMessage());
            }
        });
    }

    private CompletableFuture<BufferedImage> fetchHead(UUID uuid) {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(String.format(API_URL, uuid))).timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Futura/3.0 (https://github.com/plexusorg/Futura)").GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Failed to download player head for " + uuid + ": Received HTTP " + response.statusCode() + ":\n" + new String(response.body()));
            }

            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));

                if (image == null) {
                    throw new IllegalStateException("Downloaded data was not a valid image");
                }

                return image;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
