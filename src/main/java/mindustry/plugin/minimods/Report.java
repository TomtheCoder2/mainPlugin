package mindustry.plugin.minimods;

import arc.util.CommandHandler;
import arc.util.Strings;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.utils.Cooldowns;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Query;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.util.Objects;


public class Report implements MiniMod {
    @Override
    public void registerCommands(CommandHandler handler) {
        Cooldowns.instance.set("bug", 5 * 60);
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
                Channels.BUG_REPORT.sendMessage("<@&" + Roles.DEV + ">");
                Channels.BUG_REPORT.sendMessage(eb);
                Call.sendMessage("[sky]The bug is reported to discord.");
            }
        });

        Cooldowns.instance.set("gr", 5 * 60);
        handler.<Player>register("gr", "[player] [reason...]", "Report a griefer by id (use '/Players' to get a list of ids)", (args, player) -> {
            if (!Cooldowns.instance.canRun("gr", player.uuid())) {
                player.sendMessage(GameMsg.ratelimit("Mod", "gr"));
                return;
            }
            Cooldowns.instance.run("gr", player.uuid());

            if (args.length == 0) {
                StringBuilder builder = new StringBuilder();
                builder.append("[orange]List or reportable players: \n");
                for (Player p : Groups.player) {
                    if (p.admin() || p.con == null) continue;

                    builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id).append(")\n");
                }
                player.sendMessage(builder.toString());
            } else {
                Player found = null;
                if (args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))) {
                    int id = Strings.parseInt(args[0].substring(1));
                    for (Player p : Groups.player) {
                        if (p.id == id) {
                            found = p;
                            break;
                        }
                    }
                } else {
                    found = Query.findPlayerEntity(args[0]);
                }

                if (found == null) {
                    player.sendMessage("[scarlet]No player[orange] '" + args[0] + "'[scarlet] found.");
                    return;
                }

                if (found.team() != player.team()) {
                    player.sendMessage("[scarlet]Only players on your team can be reported.");
                    return;
                }
                if (Objects.equals(found.uuid(), player.uuid())){
                    player.sendMessage("[scarlet]You can't report yourself anymore");
                    return;
                }

                EmbedBuilder eb = new EmbedBuilder().setTitle("Potential Griefer Online")
                        .addField("Name", Utils.escapeEverything(found.name))
                        .addField("Phash", Utils.calculatePhash(found.uuid()))
                        .setColor(DiscordPalette.ERROR)
                        .setFooter("Reported by: " + Utils.escapeEverything(player.name));

                if (args.length > 1) {
                    eb.addField("Reason", args[1]);
                }

                new MessageBuilder()
                        .setEmbed(eb)
                        .setContent("<@&" + Roles.RI + ">")
                        .send(Channels.GR_REPORT);
                Call.sendMessage(found.name + "[sky] is reported to discord.");
            }
        });
    }
}
