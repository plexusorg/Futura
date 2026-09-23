package dev.plex.futura.console;

import dev.plex.futura.FuturaPlugin;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConsoleBridge {

    private static final int MAX_BUFFERED_LINES = 5000;
    private static final int MAX_MESSAGE_LENGTH = 1900;

    private final FuturaPlugin plugin;
    private final Queue<String> queue = new ConcurrentLinkedQueue<>();

    private final AtomicInteger queueSize = new AtomicInteger();
    private final AtomicInteger droppedLines = new AtomicInteger();

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "Futura-Console");
                thread.setDaemon(true);
                return thread;
            });

    public ConsoleBridge(FuturaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        executor.scheduleAtFixedRate(this::flush, 1, 1, TimeUnit.SECONDS);
    }

    public void queue(String line) {
        if (queueSize.get() >= MAX_BUFFERED_LINES) {
            droppedLines.incrementAndGet();
            return;
        }

        queue.offer(line);
        queueSize.incrementAndGet();
    }

    private void flush() {
        if (queue.isEmpty()) {
            return;
        }

        List<String> messages = new ArrayList<>();

        StringBuilder current = new StringBuilder();

        String line;

        while ((line = queue.poll()) != null) {
            queueSize.decrementAndGet();

            if (current.length() + line.length() + 1 > MAX_MESSAGE_LENGTH) {
                if (!current.isEmpty()) {
                    messages.add(current.toString());
                    current.setLength(0);
                }
            }

            if (line.length() > MAX_MESSAGE_LENGTH) {
                messages.add(line.substring(0, MAX_MESSAGE_LENGTH));
                continue;
            }

            current.append(line).append('\n');
        }

        if (!current.isEmpty()) {
            messages.add(current.toString());
        }

        int dropped = droppedLines.getAndSet(0);

        if (dropped > 0) {
            messages.add("[Futura] " + dropped + " console lines were dropped due to excessive logging.");
        }

        for (String message : messages) {
            send(message);
        }
    }

    private void send(String content) {
        TextChannel channel = plugin.getBot().getConsoleChannel();

        if (channel == null) {
            return;
        }

        channel.sendMessage("```ansi\n" + content + "```").queue();
    }

    public void shutdown() {
        executor.shutdown();
        flush();
    }
}
