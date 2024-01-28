package dev.plex.futura.event;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DiscordBridgeChatEvent extends Event implements Cancellable
{
    private final HandlerList HANDLER_LIST = new HandlerList();

    private final Guild guild;
    private final Member member;
    private final Message message;
    private final BridgeType bridgeType;

    private boolean cancelled;

    public DiscordBridgeChatEvent(Guild guild, Member member, Message message, BridgeType bridgeType)
    {
        this.guild = guild;
        this.member = member;
        this.message = message;
        this.bridgeType = bridgeType;
        this.cancelled = false;
    }

    public Guild guild()
    {
        return this.guild;
    }

    public Member member()
    {
        return this.member;
    }

    public Message message()
    {
        return this.message;
    }

    public BridgeType bridgeType()
    {
        return this.bridgeType;
    }

    @Override
    public boolean isCancelled()
    {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel)
    {
        this.cancelled = cancel;
    }

    public enum BridgeType
    {
        DISCORD_TO_MINECRAFT,
        MINECRAFT_TO_DISCORD
    }

    @Override
    public @NotNull HandlerList getHandlers()
    {
        return HANDLER_LIST;
    }
}
