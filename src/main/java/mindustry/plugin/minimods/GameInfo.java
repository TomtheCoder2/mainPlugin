package mindustry.plugin.minimods;

import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
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
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Config;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.type.Item;
import mindustry.world.modules.ItemModule;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Provides information relating to the current game.
 */
public class GameInfo implements MiniMod {
    private static long serverStartTime;
    static {
        serverStartTime = System.currentTimeMillis();
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("info", "<player>",
                data -> {
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD};
                    data.help = "Get player information.";
                    data.category = "Moderation";
                    data.aliases = new String[]{"i"};
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
                    data.aliases = new String[]{"p"};
                },
                ctx -> {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("Players: " + Groups.player.size());
                    String desc = "```\n";
                    for (Player p : Groups.player) {
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
                    data.aliases = new String[]{"s"};
                },
                ctx -> {
                    if (!Vars.state.is(GameState.State.playing)) {
                        ctx.error("Not Currently Hosting", "Please ping an admin.");
                        return;
                    }

                    long serverUptime = System.currentTimeMillis() - serverStartTime;
                    long serverUptimeSecs = serverUptime / 1000;
                    long serverUptimeMins = serverUptimeSecs / 60;
                    serverUptimeSecs -= serverUptimeMins * 60;
                    long serverUptimeHours = serverUptimeMins / 60;
                    serverUptimeMins -= serverUptimeHours * 60;
                    long serverUptimeDays = serverUptimeHours / 24;
                    serverUptimeHours -= serverUptimeDays * 24; 

                    StringBuilder uptimeSb = new StringBuilder();
                    if (serverUptimeDays != 0) {
                        uptimeSb.append(serverUptimeDays).append("d");
                    }
                    if (serverUptimeHours != 0) {
                        uptimeSb.append(serverUptimeHours).append("h");
                    }
                    if (serverUptimeMins != 0) {
                        uptimeSb.append(serverUptimeMins).append("m");
                    }
                    if (serverUptimeSecs != 0) {
                        uptimeSb.append(serverUptimeSecs).append("s");
                    }

                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle(Config.serverName)
                            .addInlineField("Players", Groups.player.size() + "")
                            .addInlineField("Map", Vars.state.map.name())
                            .addInlineField("Wave", Vars.state.wave + "")
                            .addInlineField("TPS", Vars.state.serverTps + "")
                            .addInlineField("Next wave in", Math.round(Vars.state.wavetime / Vars.state.serverTps) + " seconds")
                            .addInlineField("Waves lasted", String.valueOf(Vars.state.stats.wavesLasted))
                            .addInlineField("Units Created", String.valueOf(Vars.state.stats.unitsCreated))
                            .addInlineField("Server Uptime", uptimeSb.toString())
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
                    if (data.core() == null) {
                        ctx.error("Error", "Team " + team.name + " does not have any cores!");
                        return;
                    }
                    ItemModule items = data.core().items;
                    EmbedBuilder eb = new EmbedBuilder().setTitle("Core: " + team.name);
                    for (Field field : Items.class.getDeclaredFields()) {
                        if (field.getType().equals(Item.class)) {
                            try {
                                Item item = (Item) field.get(null);
                                eb.addInlineField(item.localizedName, items.get(item) + "");
                            } catch (IllegalAccessException e) {
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
