package dev.plex.discordbridge.common.service;

import dev.plex.discordbridge.common.config.BridgeSettings;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

/** Batches the server's Log4j console stream for a rate-limited Discord sink. */
public final class ConsoleLogRelay extends AbstractAppender implements AutoCloseable
{
    private static final String APPENDER_NAME = "PlexDiscordBridgeConsole";
    private static final int MAX_LINE_LENGTH = 1_500;

    private final LoggerContext context;
    private final ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedLines = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final BridgeSettings.ConsoleRoute settings;
    private final Consumer<String> sink;
    private final ScheduledExecutorService scheduler;

    private ConsoleLogRelay(
            LoggerContext context,
            BridgeSettings.ConsoleRoute settings,
            Consumer<String> sink)
    {
        super(
                APPENDER_NAME,
                (Filter)null,
                (Layout<? extends Serializable>)PatternLayout.newBuilder()
                        .withPattern("[%d{HH:mm:ss} %level]: %msg%notEmpty{ %throwable}%n")
                        .build(),
                true,
                Property.EMPTY_ARRAY);
        this.context = context;
        this.settings = settings;
        this.sink = sink;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("plex-discord-console-relay").factory());
    }

    public static ConsoleLogRelay attach(BridgeSettings.ConsoleRoute settings, Consumer<String> sink)
    {
        LoggerContext context = (LoggerContext)LogManager.getContext(false);
        ConsoleLogRelay relay = new ConsoleLogRelay(context, settings, sink);
        relay.start();
        context.getConfiguration().getRootLogger().addAppender(relay, null, null);
        context.updateLoggers();
        relay.scheduler.scheduleAtFixedRate(
                relay::flushSafely,
                settings.batchIntervalMillis(),
                settings.batchIntervalMillis(),
                TimeUnit.MILLISECONDS);
        return relay;
    }

    @Override
    public void append(LogEvent event)
    {
        if (closed.get())
        {
            return;
        }
        String formatted = new String(getLayout().toByteArray(event), java.nio.charset.StandardCharsets.UTF_8);
        for (String line : formatted.split("\\R"))
        {
            if (line.isBlank())
            {
                continue;
            }
            while (queuedLines.get() >= settings.maxQueuedLines())
            {
                if (lines.poll() == null)
                {
                    break;
                }
                queuedLines.decrementAndGet();
            }
            lines.offer(truncate(line, MAX_LINE_LENGTH));
            queuedLines.incrementAndGet();
        }
    }

    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        context.getConfiguration().getRootLogger().removeAppender(APPENDER_NAME);
        context.updateLoggers();
        scheduler.shutdownNow();
        lines.clear();
        queuedLines.set(0);
        stop();
    }

    private void flushSafely()
    {
        try
        {
            List<String> batch = new ArrayList<>(settings.maxLinesPerBatch());
            for (int i = 0; i < settings.maxLinesPerBatch(); i++)
            {
                String line = lines.poll();
                if (line == null)
                {
                    break;
                }
                queuedLines.decrementAndGet();
                batch.add(line);
            }
            if (!batch.isEmpty())
            {
                sink.accept(String.join("\n", batch));
            }
        }
        catch (RuntimeException ignored)
        {
            // Logging a relay failure would feed the same relay recursively.
        }
    }

    private static String truncate(String input, int maximum)
    {
        return input.length() <= maximum ? input : input.substring(0, maximum - 1) + "…";
    }
}
