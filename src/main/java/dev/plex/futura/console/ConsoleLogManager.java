package dev.plex.futura.console;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

public final class ConsoleLogManager {

    private final ConsoleBridge bridge;

    private LoggerContext context;
    private ConsoleAppender appender;

    public ConsoleLogManager(ConsoleBridge bridge) {
        this.bridge = bridge;
    }

    public void start() {
        context = (LoggerContext) LogManager.getContext(false);

        Configuration configuration = context.getConfiguration();
        PatternLayout layout = PatternLayout.newBuilder().withConfiguration(configuration).withPattern("[%d{HH:mm:ss} %level]: %msg%throwable").build();

        appender = new ConsoleAppender(bridge, layout);
        appender.start();
        configuration.addAppender(appender);

        LoggerConfig root = configuration.getRootLogger();
        root.addAppender(appender, Level.INFO, null);
        context.updateLoggers();
    }

    public void stop() {
        if (context == null || appender == null) {
            return;
        }

        Configuration configuration = context.getConfiguration();
        LoggerConfig root = configuration.getRootLogger();

        root.removeAppender(appender.getName());
        appender.stop();
        context.updateLoggers();

        appender = null;
        context = null;
    }
}