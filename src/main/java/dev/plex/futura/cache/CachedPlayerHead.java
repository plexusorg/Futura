package dev.plex.futura.cache;

import java.awt.image.BufferedImage;
import java.time.Instant;

public record CachedPlayerHead(BufferedImage image, Instant expiration) {

    public boolean expired() {
        return Instant.now().isAfter(expiration);
    }
}
