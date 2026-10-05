package dev.plex.futura.image;

import dev.plex.futura.service.PlayerHeadService;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerListImage {

    private static final int WIDTH = 760;
    private static final int PADDING = 32;
    private static final int HEADER_HEIGHT = 85;
    private static final int COLUMNS = 2;
    private static final int COLUMN_GAP = 16;
    private static final int ROW_GAP = 16;
    private static final int CARD_HEIGHT = 76;
    private static final int CARD_PADDING = 12;
    private static final int HEAD_SIZE = 52;
    private static final int HEAD_RADIUS = 8;
    private static final int SCALE = 2;
    private static final int FOOTER_HEIGHT = 32;

    private static final Font REGULAR_FONT = loadFont("Inter-Regular.ttf");
    private static final Font BOLD_FONT = loadFont("Inter-Bold.ttf");
    private static final Font FOOTER_FONT = REGULAR_FONT.deriveFont(12f);
    private static final Font TITLE_FONT = BOLD_FONT.deriveFont(24f);
    private static final Font COUNT_FONT = BOLD_FONT.deriveFont(15f);
    private static final Font PLAYER_FONT = BOLD_FONT.deriveFont(18f);
    private static final Font EMPTY_TITLE_FONT = BOLD_FONT.deriveFont(18f);
    private static final Font EMPTY_SUBTITLE_FONT = REGULAR_FONT.deriveFont(14f);

    private static final Color BACKGROUND_TOP = new Color(24, 26, 38);
    private static final Color BACKGROUND_BOTTOM = new Color(31, 35, 52);
    private static final Color CARD_BACKGROUND = new Color(38, 42, 60);
    private static final Color CARD_BORDER = new Color(61, 68, 94);
    private static final Color PRIMARY = new Color(129, 140, 248);
    private static final Color ACCENT = new Color(56, 189, 248);
    private static final Color TEXT_PRIMARY = new Color(245, 247, 255);
    private static final Color TEXT_SECONDARY = new Color(156, 163, 190);
    private static final Color ONLINE = new Color(74, 222, 128);
    private static final Color HEAD_FALLBACK = new Color(65, 67, 74);

    private record PlayerEntry(UUID uuid, String name, BufferedImage head) {
    }

    private static Font loadFont(String name) {
        try (InputStream input = Objects.requireNonNull(PlayerListImage.class.getResourceAsStream("/fonts/" + name), "Missing font " + name)) {
            return Font.createFont(Font.TRUETYPE_FONT, input);
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("Failed to load font " + name, e);
        }
    }

    public static CompletableFuture<byte[]> generate(PlayerHeadService service, Collection<? extends Player> onlinePlayers, int maxPlayers) {
        List<PlayerEntry> players = onlinePlayers.stream().map(player -> new PlayerEntry(player.getUniqueId(), player.getName(), null)).toList();
        List<CompletableFuture<PlayerEntry>> futures = players.stream().map(player -> service.getHead(player.uuid()).handle((head, error) -> {
            if (error != null) {
                return new PlayerEntry(player.uuid(), player.name(), null);
            }

            return new PlayerEntry(player.uuid(), player.name(), head);
        })).toList();

        CompletableFuture<Void> allHeads = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return allHeads.thenApply(ignored -> {
            List<PlayerEntry> loadedPlayers = futures.stream().map(CompletableFuture::join).toList();

            return render(loadedPlayers, maxPlayers);
        });
    }

    private static byte[] render(List<PlayerEntry> players, int maxPlayers) {
        int rows = (int) Math.ceil(players.size() / (double) COLUMNS);
        int height = HEADER_HEIGHT + (rows * CARD_HEIGHT) + (Math.max(0, rows - 1) * ROW_GAP) + FOOTER_HEIGHT + PADDING;

        height = Math.max(height, 180);

        BufferedImage image = new BufferedImage(WIDTH * SCALE, height * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(SCALE, SCALE);

        try {
            configureGraphics(graphics);
            drawBackground(graphics, height);
            drawHeader(graphics, players.size(), maxPlayers);

            if (players.isEmpty()) {
                drawEmpty(graphics, height);
            } else {
                drawPlayers(graphics, players);
            }

            drawFooter(graphics, height);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void configureGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }

    private static void drawBackground(Graphics2D graphics, int height) {
        GradientPaint gradient = new GradientPaint(0, 0, BACKGROUND_TOP, WIDTH, height, BACKGROUND_BOTTOM);
        graphics.setPaint(gradient);
        graphics.fillRect(0, 0, WIDTH, height);
    }

    private static void drawHeader(Graphics2D graphics, int online, int maximum) {
        graphics.setFont(TITLE_FONT);
        graphics.setColor(TEXT_PRIMARY);
        graphics.drawString("Online Players", PADDING, 40);

        GradientPaint accent = new GradientPaint(PADDING, 0, PRIMARY, PADDING + 125, 0, ACCENT);
        graphics.setPaint(accent);
        graphics.fillRoundRect(PADDING, 54, 167, 4, 4, 4);

        String count = online + " / " + maximum;
        graphics.setFont(COUNT_FONT);

        FontMetrics metrics = graphics.getFontMetrics();
        int textWidth = metrics.stringWidth(count);
        int badgeWidth = textWidth + 42;
        int badgeHeight = 34;
        int badgeX = WIDTH - PADDING - badgeWidth;
        int badgeY = 22;

        graphics.setColor(new Color(PRIMARY.getRed(), PRIMARY.getGreen(), PRIMARY.getBlue(), 35));
        graphics.fillRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 16, 16);
        graphics.setColor(PRIMARY);
        graphics.drawRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 16, 16);

        graphics.setColor(ONLINE);
        graphics.fillOval(badgeX + 12, badgeY + 13, 8, 8);

        graphics.setColor(TEXT_PRIMARY);

        int textY = badgeY + ((badgeHeight - metrics.getHeight()) / 2) + metrics.getAscent();
        graphics.drawString(count, badgeX + 28, textY);
    }

    private static void drawFooter(Graphics2D graphics, int height) {
        String text = "Powered by Futura";

        graphics.setFont(FOOTER_FONT);
        graphics.setColor(TEXT_SECONDARY);

        FontMetrics metrics = graphics.getFontMetrics();
        int x = (WIDTH - metrics.stringWidth(text)) / 2;
        int y = height - 18;

        graphics.drawString(text, x, y);
    }

    private static void drawPlayers(Graphics2D graphics, List<PlayerEntry> players) {
        int availableWidth = WIDTH - (PADDING * 2);
        int cardWidth = (availableWidth - COLUMN_GAP) / COLUMNS;

        for (int i = 0; i < players.size(); i++) {
            PlayerEntry player = players.get(i);
            int column = i % COLUMNS;
            int row = i / COLUMNS;
            int x = PADDING + column * (cardWidth + COLUMN_GAP);
            int y = HEADER_HEIGHT + row * (CARD_HEIGHT + ROW_GAP);

            Color accent = column % 2 == 0 ? PRIMARY : ACCENT;

            drawPlayer(graphics, player, x, y, cardWidth, accent);
        }
    }

    private static void drawPlayer(Graphics2D graphics, PlayerEntry player, int x, int y, int width, Color accent) {
        graphics.setColor(CARD_BACKGROUND);
        graphics.fillRoundRect(x, y, width, CARD_HEIGHT, 16, 16);
        graphics.setColor(CARD_BORDER);
        graphics.drawRoundRect(x, y, width, CARD_HEIGHT, 16, 16);
        graphics.setColor(accent);

        int accentWidth = 4;
        int accentX = x - (accentWidth / 2);
        graphics.fillRoundRect(accentX, y + 9, accentWidth, CARD_HEIGHT - 18, accentWidth, 4);

        int headX = x + CARD_PADDING + 4;
        int headY = y + ((CARD_HEIGHT - HEAD_SIZE) / 2);

        graphics.setColor(new Color(0, 0, 0, 60));
        graphics.fillRoundRect(headX + 2, headY + 3, HEAD_SIZE, HEAD_SIZE, HEAD_RADIUS, HEAD_RADIUS);

        if (player.head() != null) {
            graphics.setPaint(new TexturePaint(player.head(), new Rectangle2D.Float(headX, headY, HEAD_SIZE, HEAD_SIZE)));
        } else {
            graphics.setColor(HEAD_FALLBACK);
        }
        graphics.fillRoundRect(headX, headY, HEAD_SIZE, HEAD_SIZE, HEAD_RADIUS, HEAD_RADIUS);

        graphics.setFont(PLAYER_FONT);
        graphics.setColor(TEXT_PRIMARY);

        FontMetrics metrics = graphics.getFontMetrics();
        int textX = headX + HEAD_SIZE + 16;
        int textY = y + ((CARD_HEIGHT - metrics.getHeight()) / 2) + metrics.getAscent();

        graphics.drawString(fitText(metrics, player.name(), x + width - CARD_PADDING - textX), textX, textY);
    }

    private static String fitText(FontMetrics metrics, String text, int maxWidth) {
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }

        return text.substring(0, end) + ellipsis;
    }

    private static void drawEmpty(Graphics2D graphics, int height) {
        String title = "Nobody is online";
        String subtitle = "The server is feeling a little quiet.";

        int contentTop = HEADER_HEIGHT;
        int contentBottom = height - FOOTER_HEIGHT - PADDING;
        int contentHeight = contentBottom - contentTop;
        int centerY = contentTop + (contentHeight / 2);

        graphics.setFont(EMPTY_TITLE_FONT);
        graphics.setColor(TEXT_PRIMARY);

        FontMetrics titleMetrics = graphics.getFontMetrics();
        int titleX = (WIDTH - titleMetrics.stringWidth(title)) / 2;

        graphics.drawString(title, titleX, centerY - 8);
        graphics.setFont(EMPTY_SUBTITLE_FONT);
        graphics.setColor(TEXT_SECONDARY);

        FontMetrics subtitleMetrics = graphics.getFontMetrics();

        int subtitleX = (WIDTH - subtitleMetrics.stringWidth(subtitle)) / 2;

        graphics.drawString(subtitle, subtitleX, centerY + 18);
    }
}