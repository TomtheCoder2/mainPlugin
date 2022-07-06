package mindustry.plugin.minimods;

import arc.util.CommandHandler;
import mindustry.content.Blocks;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.ioMain;
import mindustry.plugin.data.PersistentPlayerData;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordVars;
import mindustry.plugin.utils.Cooldowns;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.world.Tile;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.util.concurrent.ExecutionException;

public class Discord implements MiniMod {
    @Override
    public void registerCommands(CommandHandler handler) {
        Cooldowns.instance.set("bug", 5*60);
        handler.<Player>register("bug", "[description...]", "Send a bug report to the discord server. (Please do not spam, because this command pings developers)", (args, player) -> {
            if (!Cooldowns.instance.canRun("bug", player.uuid())) {
                player.sendMessage("[scarlet]This command is on a 5 minute cooldown!");
                return;
            }
            Cooldowns.instance.run("bug", player.uuid());

            if (args.length == 0) {
                player.sendMessage("[orange]Please describe exactly what the bug is or how you got it!\n");
            } else {
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("Player " + Utils.escapeEverything(player) + " reported a bug!");
                eb.setDescription("Description: " + String.join(" ", args));
                Channels.BUG_REPORT.sendMessage(" <@770240444466069514> ");
                Channels.BUG_REPORT.sendMessage(eb);
                Call.sendMessage("[sky]The bug is reported to discord.");
            }
        });
    }
}
