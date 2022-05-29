package mindustry.plugin.commands;

import arc.struct.Seq;
import mindustry.maps.Map;
import mindustry.plugin.discordcommands.Command;
import mindustry.plugin.discordcommands.Context;
import mindustry.plugin.discordcommands.DiscordCommands;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.JSONObject;

import static mindustry.Vars.maps;
import static mindustry.plugin.ioMain.api;
import static mindustry.plugin.utils.Utils.escapeEverything;
import static mindustry.plugin.utils.Utils.getTextChannel;

public class Public {
    private final JSONObject data;

    public Public(JSONObject data) {
        this.data = data;
    }

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
        if (data.has("appeal_roleid") && data.has("appeal_channel_id")) {
            String appealRole = data.getString("appeal_roleid");
            TextChannel appeal_channel = getTextChannel(data.getString("appeal_channel_id"));

            handler.registerCommand(new Command("appeal") {
                {
                    help = "Request an appeal";
                }

                public void run(Context ctx) {
                    ctx.author.asUser().get().addRole(api.getRoleById(appealRole).get());
                    EmbedBuilder eb = new EmbedBuilder().setTitle("Successfully requested an appeal").setDescription("Please head over to <#" + appeal_channel.getIdAsString() + ">!");
                    ctx.sendMessage(eb);
                    EmbedBuilder appealMessage = new EmbedBuilder().setTitle("Please use this format to appeal:").addField("1.Names", "All names used in game.").addField("2.Screenshot", "Send a screenshot of your ban screen").addField("3.Reason", "Explain what you did and why you want to get unbanned");
                    appeal_channel.sendMessage("<@" + ctx.author.getIdAsString() + ">");
                    appeal_channel.sendMessage(appealMessage);
                }
            });
        }
    }
}
