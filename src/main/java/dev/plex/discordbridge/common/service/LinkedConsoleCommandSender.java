package dev.plex.discordbridge.common.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

/** Creates a console-capable sender that reports a linked Minecraft account name. */
public final class LinkedConsoleCommandSender
{
    private LinkedConsoleCommandSender()
    {
    }

    public static ConsoleCommandSender create(String minecraftName)
    {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        return (ConsoleCommandSender)Proxy.newProxyInstance(
                ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[] { ConsoleCommandSender.class },
                (proxy, method, arguments) ->
                {
                    if (method.getName().equals("getName") && method.getParameterCount() == 0)
                    {
                        return minecraftName;
                    }
                    if (method.getName().equals("name") && method.getParameterCount() == 0)
                    {
                        return Component.text(minecraftName);
                    }
                    if (method.getDeclaringClass() == Object.class)
                    {
                        return switch (method.getName())
                        {
                            case "equals" -> proxy == arguments[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "LinkedConsoleCommandSender[" + minecraftName + "]";
                            default -> method.invoke(console, arguments);
                        };
                    }
                    try
                    {
                        return method.invoke(console, arguments);
                    }
                    catch (InvocationTargetException exception)
                    {
                        throw exception.getCause();
                    }
                });
    }
}
