package mindustry.plugin.commands;

import arc.struct.Seq;
import mindustry.maps.Map;
import mindustry.plugin.discordcommands.Command;
import mindustry.plugin.discordcommands.Context;
import mindustry.plugin.discordcommands.DiscordCommands;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import static mindustry.Vars.maps;
import static mindustry.plugin.utils.Utils.escapeEverything;

public class Public {
    public void registerCommands(DiscordCommands handler) {
        handler.registerCommand(new Command("maps") {
            {
                help = "Check a list of available maps and their ids.";
            }

            public void run(Context ctx) {
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("**All available maps in the playlist:**");
                Seq<Map> mapList = maps.customMaps();
                for (int i = 0; i < mapList.size; i++) {
                    Map m = mapList.get(i);
                    eb.addField(escapeEverything(m.name()), m.width + " x " + m.height, true);
                }
                ctx.sendMessage(eb);
            }
        });
    }
}
