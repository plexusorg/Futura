package dev.plex.futura.console;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.io.Serializable;

public final class ConsoleAppender extends AbstractAppender {

    private final ConsoleBridge bridge;

    public ConsoleAppender(ConsoleBridge bridge, Layout<? extends Serializable> layout) {
        super("Futura-Console", null, layout, true, Property.EMPTY_ARRAY);
        this.bridge = bridge;
    }

    @Override
    public void append(LogEvent event) {
        if (event == null) {
            return;
        }

        String message = getLayout().toSerializable(event).toString();
        bridge.queue(message);
    }
}
