package dev.plex.futura.util;

import dev.plex.futura.Futura;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

/**
 * @author Taah
 * @since 8:02 PM [01-27-2024]
 */
public class FuturaLog
{
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    public static void debug(String s, Object... object) {
        for (int i = 0; i < object.length; i++) {
            s = s.replace("{" + i + "}", object[i].toString());
        }
        Bukkit.getConsoleSender().sendMessage(MINI.deserialize("<dark_purple>[Futura | DEBUG] <gold>" + s));
    }

    public static void info(String s, Object... object) {
        for (int i = 0; i < object.length; i++) {
            s = s.replace("{" + i + "}", object[i].toString());
        }
        Bukkit.getConsoleSender().sendMessage(MINI.deserialize("<dark_gray>[Futura | INFO] <yellow>" + s));
    }

    public static void error(String s, Object... object) {
        for (int i = 0; i < object.length; i++) {
            s = s.replace("{" + i + "}", object[i].toString());
        }
        Bukkit.getConsoleSender().sendMessage(MINI.deserialize("<dark_red>[Futura | ERROR] <red>" + s));
    }
}
