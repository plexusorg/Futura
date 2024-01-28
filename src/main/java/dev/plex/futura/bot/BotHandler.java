package dev.plex.futura.bot;

import dev.plex.futura.Futura;
import dev.plex.futura.util.FuturaLog;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;

/**
 * @author Taah
 * @since 7:01 PM [01-25-2024]
 */
public class BotHandler
{
    private final JDA jda;
    private boolean ready;

    public BotHandler()
    {
        this.ready = false;

        final String token = System.getenv("BOT_TOKEN").isEmpty() ? Futura.plugin().getConfig().getString("bot.token", System.getProperty("BOT_TOKEN", "")) : System.getenv("BOT_TOKEN");
        if (token.isEmpty())
        {
            FuturaLog.error("Bot Token was empty! Make sure to specify one in the config.yml, or as a System Property / Environment with the name BOT_TOKEN");
            jda = null;
            return;
        }
        jda = JDABuilder
                .create(token, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .build();

        try
        {
            jda.awaitReady();
            this.ready = true;

            final String status = Futura.plugin().getConfig().getString("bot.status", "").replace("%players%", String.valueOf(Bukkit.getOnlinePlayers().size()));
            this.jda.getPresence().setActivity(Activity.customStatus(status));

            FuturaLog.info("Discord Bot has logged in as {0} and is ready to go!", jda.getSelfUser().getName());
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    public JDA jda()
    {
        return this.jda;
    }

    public boolean ready()
    {
        return this.ready;
    }
}
