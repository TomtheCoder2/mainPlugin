package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.ObjectMap;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.DiscordLog;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Query;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.ui.Menus;
import mindustry.world.Block;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.util.Arrays;

import static mindustry.plugin.utils.Utils.escapeEverything;

/**
 * Manages player ranks and other player information.
 */
public class Ranks implements MiniMod {
    private final static String promotionMessage = """
            [sky]%player%, you have been promoted to [sky]<%rank%>[]!
            [#4287f5]You reached a playtime of - %playtime% minutes!
            [#f54263]You played a total of %games% games!
            [#9342f5]You built a total of %buildings% buildings!
            [sky]Enjoy your time on the [white][#ff2400]P[#ff4900]H[#ff6d00]O[#ff9200]E[#ffb600]N[#ffdb00]I[#ffff00]X [white]Servers[sky]!""";
    /**
     * Blocks that do not count towards the stats for a player
     */
    private final static Block[] excludedBlocks = new Block[]{
            Blocks.conveyor,
            Blocks.titaniumConveyor,
            Blocks.plastaniumConveyor,
            Blocks.junction,
            Blocks.router,
            Blocks.underflowGate,
            Blocks.overflowGate
    };
    /**
     * Number of buildings built that have not been stored to the database.
     */
    private final ObjectMap<String, Integer> buildingsBuiltCache = new ObjectMap<>();
    private long mapStartTime = System.currentTimeMillis();

    @Override
    public void registerEvents() {
        // -- MAP SECTION -- //
        Events.on(EventType.WorldLoadEvent.class, event -> {
            // reset start time
            mapStartTime = System.currentTimeMillis();

            // rate menu
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    rateMenu(null);
                }
            }, 10 * 60);
        });

        Events.on(EventType.GameOverEvent.class, event -> {
            // update time-based map stats
            String mapName = escapeEverything(Vars.state.map.name());
            Database.Map md = Database.getMapData(mapName);
            if (md == null) {
                md = new Database.Map(mapName);
            }
            md.highscoreWaves = Math.max(md.highscoreWaves, Vars.state.stats.wavesLasted);
            long mapTime = ((System.currentTimeMillis() - mapStartTime) / 1000) / 60;
            md.highscoreTime = Math.max(md.highscoreTime, mapTime);
            if (md.shortestGame != 0) {
                md.shortestGame = Math.min(md.shortestGame, mapTime);
            } else {
                md.shortestGame = mapTime;
            }
            md.playTime += mapTime;
            Database.setMapData(md);
        });

        // -- PLAYER SECTION -- //
        Events.on(EventType.GameOverEvent.class, event -> {
            // +1 games to everyone
            // updates player's gamesPlayed
            for (Player p : Groups.player) {
                Database.Player pd = Database.getPlayerData(p.uuid());
                if (pd == null) {
                    pd = new Database.Player(p.uuid(), 0);
                }
                pd.gamesPlayed++;
                Call.infoMessage(p.con, "[accent]+1 games played");
                Database.setPlayerData(pd);
            }

            promoteRanks();
        });

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            try {
                if (event.unit.getPlayer() == null) return;
                if (event.breaking) return;

                String uuid = event.unit.getPlayer().uuid();
                if (event.tile.block() != null) {
                    if (!Arrays.asList(excludedBlocks).contains(event.tile.block())) {
                        buildingsBuiltCache.put(uuid, buildingsBuiltCache.get(uuid, 0) + 1);
                    }
                }
            } catch (Exception e) {
                Log.err("There was an error while saving block status: ");
                e.printStackTrace();
            }
        });

        Timer.schedule(new Timer.Task() {
            long timeStart = System.currentTimeMillis();

            @Override
            public void run() {
                int elapsedTimeMin = Math.round((System.currentTimeMillis() - timeStart) / 1000) / 60;

                for (Player player : Groups.player) {
                    Database.Player pd = Database.getPlayerData(player.uuid());
                    if (pd == null) {
                        pd = new Database.Player(player.uuid(), 0);
                    }
                    pd.playTime += elapsedTimeMin;
                    pd.buildingsBuilt += buildingsBuiltCache.get(player.uuid(), 0);
                    Database.setPlayerData(pd);
                }

                timeStart = System.currentTimeMillis();
            }
        }, 120, 120);
    }

    /**
     * Popup a rate menu for the given player, or all player
     *
     * @param p The player to popup, or null for all players
     */
    private void rateMenu(Player p) {
        String mapName = escapeEverything(Vars.state.map.name());
        int id = Menus.registerMenu((player, selection) -> {
            Database.Map md = Database.getMapData(mapName);
            if (md == null) {
                md = new Database.Map(mapName);
            }
            if (selection == 0) {
                md.positiveRating += 1;
                player.sendMessage("Successfully gave a [green]positive [white]feedback for " + mapName + "[white]!");
            } else if (selection == 1) {
                md.negativeRating += 1;
                player.sendMessage("Successfully gave a [red]negative [white]feedback for " + mapName + "[white]!");
            }
            Database.setMapData(md);
        });
        if (p == null) {
            Call.menu(id,
                    "Rate this map! [pink]" + mapName,
                    "Do you like this map? Vote [green]yes [white]or [scarlet]no:",
                    new String[][]{
                            new String[]{"[green]Yes", "[scarlet]No"},
                            new String[]{"Close"}
                    }
            );
        } else {
            Call.menu(p.con, id,
                    "Rate this map! [pink]" + mapName,
                    "Do you like this map? Vote [green]yes [white]or [scarlet]no:",
                    new String[][]{
                            new String[]{"[green]Yes", "[scarlet]No"},
                            new String[]{"Close"}
                    }
            );
        }
    }

    /**
     * Check if anyone deserves to be promoted, and promote accordingly
     */
    private void promoteRanks() {
        // check if someone gets promoted
        for (Player player : Groups.player) {
            Database.Player pd = Database.getPlayerData(player.uuid());
            if (pd == null) {
                pd = new Database.Player(player.uuid(), 0);
            }

            for (var entry : Rank.reqs) {
                Rank.Req req = entry.value;
                if (pd.rank < entry.key && pd.playTime >= req.playTime
                        && pd.buildingsBuilt >= req.buildingsBuilt && pd.gamesPlayed >= req.gamesPlayed) {
                    Call.infoMessage(player.con, Utils.formatMessage(player, promotionMessage));
                    pd.rank = entry.key;
                    Log.info(escapeEverything(player.name) + " got promoted to " + Rank.all[pd.rank].name + "!");
                }
            }

            Database.setPlayerData(pd);
        }
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("players", "Display all players and their ids", (args, player) -> {
            StringBuilder builder = new StringBuilder();
            builder.append("[orange]List of players: \n");
            for (Player p : Groups.player) {
                if (p.admin) {
                    builder.append("[accent]");
                } else {
                    builder.append("[lightgray]");
                }
                builder.append(p.name).append("[accent] : ").append(p.id).append("\n");
            }
            player.sendMessage(builder.toString());
        });

        handler.<Player>register("ranks", "Show all ranks.", (args, player) -> { // self info
            StringBuilder sb = new StringBuilder("[accent]Ranks\n");
            for (int i = 0; i < Rank.all.length; i++) {
                Rank rank = Rank.all[i];
                sb.append(rank.tag)
                        .append(" [#")
                        .append(rank.color.toString(), 0, 6)
                        .append("]")
                        .append(rank.name)
                        .append("\n");
            }
            sb.append("\n[green]Type [sky]/req [green]to see the requirements for the ranks");
            Call.infoMessage(player.con, sb.toString());
        });
        handler.<Player>register("req", "Show the requirements for all ranks", (args, player) -> { // self info
            StringBuilder sb = new StringBuilder("[accent]List of all requirements:\n");
            for (int i = 0; i < Rank.all.length; i++) {
                Rank rank = Rank.all[i];
                sb.append("[#").append(rank.color.toString(), 0, 6).append("]")
                        .append(rank.name).append(" ");
                if (rank.requirements != null) {
                    sb.append("[] : [orange]").append(rank.requirements).append("\n");
                } else if (Rank.reqs.get(i) != null) {
                    Rank.Req req = Rank.reqs.get(i);
                    sb
                            .append("[] : [red]")
                            .append(Utils.formatInt(req.playTime))
                            .append(" mins[white] / [orange]")
                            .append(req.gamesPlayed)
                            .append(" games[white] / [yellow]")
                            .append(Utils.formatInt(req.buildingsBuilt))
                            .append(" built\n");
                }
            }

            Call.infoMessage(player.con, sb.toString());
        });
        handler.<Player>register("stats", "[player]", "Display stats of the specified player.", (args, player) -> {
            if (args.length > 0) {
                Player p = Query.findPlayerEntity(args[0]);
                if (p != null) {
                    Database.Player pd = Database.getPlayerData(p.uuid());
                    if (pd != null) {
                        Call.infoMessage(player.con, Utils.formatMessage(p, Utils.Message.stat()));
                    }
                } else {
                    player.sendMessage("[scarlet]Error: Player not found or offline");
                }
            } else {
                Call.infoMessage(player.con, Utils.formatMessage(player, Utils.Message.stat()));
            }
        });
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("ranking", "<playtime|gamesplayed|buildingsbuilt> [offset]",
                data -> {
                    data.help = "Returns a ranking of players.";
                },
                ctx -> {
                    String column = ctx.args.get("playtime|gamesplayed|buildingsbuilt");
                    switch (column) {
                        case "p" -> {
                            column = "playtime";
                        }
                        case "g" -> {
                            column = "gamesplayed";
                        }
                        case "b" -> {
                            column = "buildingsbuilt";
                        }
                    }
                    int offset = ctx.args.getInt("offset", 0);
                    Database.PlayerRank[] ranking = Database.rankPlayers(10, column, offset);
                    if (ranking == null || ranking.length == 0) {
                        ctx.error("No players found", "Make sure the stat is valid.");
                        return;
                    }

                    String table = "```\n";
                    table += String.format("%3s %-20s %-10s\n", "", "Player", column);
                    for (int i = 0; i < ranking.length; i++) {
                        var info = Vars.netServer.admins.getInfoOptional(ranking[i].uuid);
                        String name = "<unknown>";
                        if (info != null) {
                            name = escapeEverything(info.lastName);
                        }
                        table += String.format("%3s %-20s %-10s\n", offset + i + 1, name, ranking[i].stat);
                    }
                    table += "```";

                    ctx.sendEmbed(new EmbedBuilder()
                            .setColor(DiscordPalette.INFO)
                            .setTitle("Player Ranking")
                            .setDescription(table)
                    );
                }
        );

        handler.register("mapranking", "<positiverating|negativerating|highscorewaves|playtime> [offset]",
                data -> {
                    data.help = "Returns a ranking of maps";
                },
                ctx -> {
                    String column = ctx.args.get("positiverating|negativerating|highscorewaves|playtime");
                    int offset = ctx.args.getInt("offset", 0);
                    Database.MapRank[] ranking = Database.rankMaps(10, column, offset);
                    if (ranking == null || ranking.length == 0) {
                        ctx.error("No maps found", "Make sure the stat is valid.");
                        return;
                    }

                    String table = "```\n";
                    table += String.format("%3s %-30s %-10s\n", "", "Map", column);
                    for (int i = 0; i < ranking.length; i++) {
                        table += String.format("%3s %-30s %-10s\n", offset + i + 1, Strings.stripColors(ranking[i].name), ranking[i].stat);
                    }
                    table += "```";

                    ctx.sendEmbed(new EmbedBuilder()
                            .setColor(DiscordPalette.INFO)
                            .setTitle("Map Ranking")
                            .setDescription(table)
                    );
                }
        );

        handler.register("setrank", "<player> <rank>", 
                data -> {
                    data.help = "Set a player's in-game rank";
                    data.roles = new long[] { Roles.MOD, Roles.ADMIN };
                    data.category = "Management";
                },
                ctx -> {
                    Player target = Query.findPlayerEntity(ctx.args.get("player"));
                    if (target == null) {
                        ctx.error("Player not found", ctx.args.get("player") + " is not online");
                        return;
                    }

                    String rankQuery = ctx.args.get("rank");
                    int rank = -1;
                    for (int i =0 ; i < Rank.all.length; i++) {
                        if (Integer.toString(i).equals(rankQuery) || Rank.all[i].name.equalsIgnoreCase(rankQuery)) {
                            rank = i;
                        }
                    }
                    if (rank == -1) {
                        ctx.error("Invalid rank", rankQuery + " is not a valid rank");
                        return;
                    }

                    var pd = Database.getPlayerData(target.uuid());
                    if (pd == null) {
                        pd = new Database.Player(target.uuid(), rank);
                    }
                    pd.rank = rank;
                    Database.setPlayerData(pd);

                    target.name = Rank.all[rank].tag + " [#" + Rank.all[rank].color.toString().substring(0,6) + "]" +  Utils.escapeEverything(target);
                    ctx.success("Rank set", Utils.escapeEverything(target) + "'s rank is now set to " + Rank.all[rank].name);

                    DiscordLog.moderation("Set rank", ctx.author(), Vars.netServer.admins.getInfo(pd.uuid), null, "Rank: " + Rank.all[rank].name + " (" + rank + ")");
                }
        );

        handler.register("setstats", "<player> <playtime> <buildingsbuilt> <gamesplayed>", 
                data -> {
                    data.help = "Set a player's game statistics";
                    data.category = "Management";
                    data.roles = new long[] { Roles.ADMIN, Roles.MOD };
                },
                ctx -> {
                    var info = Query.findPlayerInfo(ctx.args.get("player"));
                    if (info == null) {
                        ctx.error("No such player", "There is no such player in the database");
                        return;
                    }
                    
                    var pd = Database.getPlayerData(info.id);
                    if (pd == null) {
                        ctx.error("No such player", "There is no such player in the database");
                        return;
                    }

                    int playtime = ctx.args.getInt("playtime");
                    int buildingsbuilt = ctx.args.getInt("buildingsbuilt");
                    int gamesplayed = ctx.args.getInt("gamesplayed");
                    pd.playTime = playtime;
                    pd.buildingsBuilt = buildingsbuilt;
                    pd.gamesPlayed = gamesplayed;

                    ctx.success("Set player stats", "Play time: " + playtime + " min\nBuildings built: " + buildingsbuilt + "\nGames played: " + gamesplayed);
                }
        );
    }
}