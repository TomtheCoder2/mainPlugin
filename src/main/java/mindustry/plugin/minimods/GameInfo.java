package mindustry.plugin.minimods;

import java.lang.reflect.Field;
import java.time.Instant;

import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Reflect;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.core.GameState;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.SaveIO;
import mindustry.net.Administration;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.Context;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Config;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.type.Item;
import mindustry.world.modules.ItemModule;

/** Provides information relating to the current game. */
public class GameInfo implements MiniMod {
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
                eb.setColor(DiscordPalette.INFO);
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
                eb.setColor(DiscordPalette.INFO);
                ctx.sendEmbed(eb);
            }
        );

        handler.register("status", "", 
            data -> {
                data.help = "Show current status of the server";
                data.aliases = new String[]  {"s"};
            },
            ctx -> {
                if (!Vars.state.is(GameState.State.playing)) {
                    ctx.error("Not Currently Hosting", "Please ping an admin.");
                    return;
                }

                EmbedBuilder eb = new EmbedBuilder()
                    .setTitle(Config.serverName)
                    .addInlineField("Players", Groups.player.size() + "")
                    .addInlineField("Map", Vars.state.map.name())
                    .addInlineField("Wave", Vars.state.wave + "")
                    .addInlineField("FPS", Core.graphics.getFramesPerSecond() + "")
                    .addInlineField("TPS", Vars.state.serverTps + "")
                    .addInlineField("Next wave in", Math.round(Vars.state.wavetime / Vars.state.serverTps) + " seconds")
                    .setColor(DiscordPalette.INFO);

                Fi tempDir = new Fi("temp/");
                Fi mapFile = tempDir.child(Config.serverName + "_" + Utils.escapeEverything(Vars.state.map.name()).replaceAll("[^a-zA-Z0-9\\.\\-]", "_") + ".msav");
                Core.app.post(() -> {
                    try {
                        SaveIO.write(mapFile);

                        Log.info("Saved to @", mapFile);
                        Log.debug(mapFile.absolutePath());

                        // TODO: Show image w/ content server
                        ctx.sendMessage(new MessageBuilder()
                            .addEmbed(eb)
                            .addAttachment(mapFile.file())
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                        ctx.error("Internal Error", e.getMessage());
                    }
                });
            }
        );

        handler.register("resinfo", "[team]",
            data -> {
                data.help = "Show amount of resources in core.";
            },
            ctx -> {
                Team team = Vars.state.rules.defaultTeam;
                if (ctx.args.containsKey("team")) {
                    team = Seq.with(Team.all).find(x -> x.name.equalsIgnoreCase(ctx.args.get("team")));
                    if (team == null) {
                        ctx.error("Error", "Team '" + ctx.args.get("team") + "' not found.");
                        return;
                    }
                }
                TeamData data = Vars.state.teams.get(team);
                ItemModule items = data.core().items;
                EmbedBuilder eb = new EmbedBuilder().setTitle("Team: " + team.name);
                for (Field field:  Items.class.getDeclaredFields()) {
                    if (field.getType().equals(Item.class)) {
                        try {
                            Item item = (Item)field.get(null);
                            eb.addInlineField(item.name, items.get(item) +"");
                        } catch(IllegalAccessException e) {
                            Log.err(e);
                        }
                    }
                }
                eb.setColor(DiscordPalette.INFO);
                ctx.sendEmbed(eb);
            }
        );
    }
}
