package mindustry.plugin.minimods;

import java.time.Instant;

import org.javacord.api.entity.message.embed.EmbedBuilder;

import arc.util.CommandHandler;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.Context;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;

public class PlayerInfo implements MiniMod {
    @Override
    public void registerCommands(CommandHandler h) {}

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("info", "<player>", 
            data -> {
                data.roles = new long[] { Roles.ADMIN, Roles.MOD };
                data.help = "Get player information.";
                data.category = "Moderation";
                data.aliases = new String[] { "i" };
            },
            ctx -> {
                EmbedBuilder eb = new EmbedBuilder();
                Administration.PlayerInfo info = Utils.getPlayerInfo(ctx.args.get("player"));
                if (info == null) {
                    ctx.error("Error", "Player '" + ctx.args.get("player") + "' not found or is offline");
                    return;
                }
                eb.addField("Times Kicked", info.timesKicked + "");
                Database.Player pd = Database.getPlayerData(info.id);
                if (pd != null) {
                    eb.addField("Rank", Rank.all[pd.rank].name);
                    eb.addField("Play time", pd.playTime + " minutes");
                    eb.addField("Games", pd.gamesPlayed + "");
                    eb.addField("Buildings built", pd.buildingsBuilt + "");
                    if (pd.bannedUntil > Instant.now().getEpochSecond()) {
                        eb.addField("Banned", "Until " + Utils.epochToString(pd.bannedUntil));
                    } else if (pd.banned) {
                        eb.addField("Banned", "Forever");
                    }
                    if (pd.banReason != null && !pd.banReason.equals("")) {
                        eb.addField("Ban Reason", pd.banReason);
                    }

                    String names = "";
                    for (String name : info.names) {
                        names += name + "\n";
                    }
                    eb.addField("Names", names);
                    eb.addField("Current Name", info.lastName);
                    eb.addField("Current IP", info.lastIP);
                }
                eb.setColor(Context.Colors.INFO);
                ctx.sendEmbed(eb);
            }
        );

        handler.register("players", "", 
            data -> {
                data.help = "List online players and their IDs.";
                data.aliases = new String[] { "p" };
            },
            ctx -> {
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("Players: " + Groups.player.size());
                String desc = "```\n";
                for (Player p: Groups.player) {
                    desc += String.format("%-9d : %s", p.id(), p.name());
                    if (p.admin) {
                        desc += " (admin)";
                    }
                    desc += "\n";
                }
                desc += "```";
                eb.setDescription(desc);
                ctx.sendEmbed(eb);
            }
        );
    }
}
