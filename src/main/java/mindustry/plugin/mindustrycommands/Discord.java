package mindustry.plugin.mindustrycommands;

import arc.util.CommandHandler;
import mindustry.content.Blocks;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.plugin.requests.Translate;
import mindustry.world.Tile;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.JSONObject;

import static arc.util.Log.debug;
import static mindustry.Vars.world;
import static mindustry.plugin.ioMain.*;
import static mindustry.plugin.utils.Utils.escapeEverything;
import static mindustry.plugin.utils.Utils.getTextChannel;

public class Discord {
    public Discord() {

    }

    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("bug", "[description...]", "Send a bug report to the discord server. (Please do not spam, because this command pings developers)", (args, player) -> {
            for (Long key : CommandCooldowns.keys()) {
                if (key + CDT < System.currentTimeMillis() / 1000L) {
                    CommandCooldowns.remove(key);
                } else if (player.uuid().equals(CommandCooldowns.get(key))) {
                    player.sendMessage("[scarlet]This command is on a 5 minute cooldown!");
                    return;
                }
            }

            if (args.length == 0) {
                player.sendMessage("[orange]Please describe exactly what the bug is or how you got it!\n");
            } else {
                TextChannel bugReportChannel = getTextChannel("864957934513684480");
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("Player " + escapeEverything(player) + " reported a bug!");
                eb.setDescription("Description: " + String.join(" ", args));
                assert bugReportChannel != null;
                bugReportChannel.sendMessage(" <@770240444466069514> ");
                bugReportChannel.sendMessage(eb);
                Call.sendMessage("[sky]The bug is reported to discord.");
                CommandCooldowns.put(System.currentTimeMillis() / 1000L, player.uuid());
            }
        });
        handler.<Player>register("discord", "Place a message block below a player with links for our discord server.", (args, player) -> {
            float x = player.getX();
            float y = player.getY();
            Tile tile = world.tileWorld(x, y);
            if ((tile.block() == null || tile.block() == Blocks.air)) {
                tile.setNet(Blocks.message, player.team(), 0);
                tile.build.configure("https://discord.phoenix-network.dev\n\nor\n\nhttps://discord.gg/qtjqCUbbdR");
                player.sendMessage("[green]Successfully placed a message block.");
            } else {
                player.sendMessage("[scarlet]Cant place a message block here, because there is already a block here!");
            }
        });
    }
}
