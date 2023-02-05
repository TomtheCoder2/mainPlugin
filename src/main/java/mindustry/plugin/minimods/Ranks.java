package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
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
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordLog;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.*;
import mindustry.ui.Menus;
import mindustry.world.Block;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.util.Arrays;
import java.util.Objects;

import static java.lang.Math.max;
import static mindustry.plugin.minimods.Moderation.frozen;
import static mindustry.plugin.utils.Utils.escapeEverything;

/**
 * Manages player ranks and other player information.
 */
public class Ranks implements MiniMod {
    /**
     * Uuid of players that have less than 60 minutes of playtime.
     */
    public static final ObjectMap<String, Utils.Pair<Integer, Integer>> newPlayers = new ObjectMap<>();
    public static final ObjectSet<String> warned = new ObjectSet<>();
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
     * Boulder
     * Snow Boulder
     * Shale Boulder
     * Sand Boulder
     * Dacite Boulder
     * Basalt Boulder
     * Carbon Boulder
     * Ferric Boulder
     * Beryllic Boulder
     * Yellow stone Boulder
     * Crystaline Boulder
     * Red Ice Boulder
     * Rhyolite Boulder
     * Red stone Boulder
     * Boulder Boulder
     */
    private final static Block[] excludedBlocksAntiGriefSystem = new Block[]{
            Blocks.boulder,
            Blocks.snowBoulder,
            Blocks.shaleBoulder,
            Blocks.sandBoulder,
            Blocks.daciteBoulder,
            Blocks.basaltBoulder,
            Blocks.carbonBoulder,
            Blocks.ferricBoulder,
            Blocks.beryllicBoulder,
            Blocks.yellowStoneBoulder,
            Blocks.crystallineBoulder,
            Blocks.redIceBoulder,
            Blocks.rhyoliteBoulder,
            Blocks.redStoneBoulder,

    };
    /**
     * Number of buildings built that have not been stored to the database.
     */
    private final ObjectMap<String, Integer> buildingsBuiltCache = new ObjectMap<>();
    /**
     * For auto ban system
     */
    private final ObjectMap<String, Integer> buildingsDestroyedCache = new ObjectMap<>();
    /**
     * Who has already rated the map
     */
    private final ObjectSet<String> hasRatedMap = new ObjectSet<>();
    private long mapStartTime = System.currentTimeMillis();

    @Override
    public void registerEvents() {
        // -- MAP SECTION -- //
        Events.on(EventType.WorldLoadEvent.class, event -> {
            // reset start time
            mapStartTime = System.currentTimeMillis();

            // rate menu
            hasRatedMap.clear();
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    rateMenu(null);
                }
            }, 10 * 60);
        });

        Events.on(EventType.PlayerJoin.class, event -> {
            Timer.schedule(() -> {
                if (event.player.con == null || !event.player.con.isConnected()) return;
                rateMenu(event.player);
            }, 10 * 60);
        });

        Events.on(EventType.GameOverEvent.class, event -> {
            // update time-based map stats
            String mapName = escapeEverything(Vars.state.map.name());
            Database.Map md = Database.getMapData(mapName);
            if (md == null) {
                md = new Database.Map(mapName);
            }
            md.highscoreWaves = max(md.highscoreWaves, Vars.state.stats.wavesLasted);
            long mapTime = ((System.currentTimeMillis() - mapStartTime) / 1000) / 60;
            md.highscoreTime = max(md.highscoreTime, mapTime);
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
                String uuid = event.unit.getPlayer().uuid();


                if (event.tile.block() != null) {
                    if (!event.breaking) {
                        // this will increase the buildingsbuilt stats but its more important to stop griefing
//                    if (!Arrays.asList(excludedBlocks).contains(event.tile.block())) {
                        buildingsBuiltCache.put(uuid, buildingsBuiltCache.get(uuid, 0) + 1);
//                    }
                    } else {
                        if (!Arrays.asList(excludedBlocksAntiGriefSystem).contains(event.tile.block())) {
                            buildingsDestroyedCache.put(uuid, buildingsDestroyedCache.get(uuid, 0) + 1);
                        }
                    }

                    // check if its suspicious
                    if (newPlayers.containsKey(uuid)) {
                        if (buildingsBuiltCache.get(uuid, 0) < buildingsDestroyedCache.get(uuid, 0)) {
                            int warnThreshold = max(10, (buildingsBuiltCache.get(uuid, 0) + newPlayers.get(uuid).second) * newPlayers.get(uuid).first / 2);
                            if (buildingsDestroyedCache.get(uuid, 0) > warnThreshold) {
                                // freeze for 15 seconds
                                frozen.add(uuid);
                                Timer.schedule(() -> frozen.remove(uuid), 15);
                                if (!warned.contains(uuid)) {
                                    warned.add(uuid);

                                    // warn mods
                                    EmbedBuilder eb = new EmbedBuilder().setTitle("Potential Griefer Online (Auto Ban System)")
                                            .addField("Name", Utils.escapeEverything(event.unit.getPlayer().name))
                                            .addField("Phash", Utils.calculatePhash(uuid))
                                            .setColor(DiscordPalette.WARN)
                                            .setFooter("Reported by: Auto Ban System");
                                    new MessageBuilder()
                                            .setEmbed(eb)
                                            .setContent("<@&" + Roles.RI + ">")
                                            .send(Channels.GR_REPORT);

                                    // send a message to all players online
                                    for (Player p : Groups.player) {
                                        if (p == event.unit.getPlayer()) continue;
                                        if (warned.contains(p.uuid())) continue;
                                        p.sendMessage("[scarlet][Anti-griefer-system] Warning! Potential griefer found on the server, keep an eye on \"" + event.unit.getPlayer().name + "\"[scarlet]!" + (p.admin ? " [orange]Their UUID is " + uuid : ""));
                                    }
                                    // warn player
                                    event.unit.getPlayer().sendMessage("[scarlet]You are deconstructing too fast, to avoid mass grief you have been frozen for [cyan]15 seconds.[] This action will be reported to the moderators.");
                                    // log in console
                                    Log.info("[Anti-griefer-system] Warning! Potential griefer found on the server, keep an eye on \"" + event.unit.getPlayer().name + "\"[scarlet]!" + " Their UUID is " + uuid);

                                    // remove from warned after 5 minutes
                                    Timer.schedule(() -> {
                                        if (warned.contains(uuid)) {
                                            warned.remove(uuid);
                                        }
                                    }, 5 * 60);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.err("There was an error while saving block status: ");
                e.printStackTrace();
            }
        });

        Events.on(EventType.PlayerLeave.class, event -> {
            // remove from new players
            newPlayers.remove(event.player.uuid());
            // also from warned
            warned.remove(event.player.uuid());
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
                    if (newPlayers.containsKey(player.uuid())) {
                        if (newPlayers.get(player.uuid()).first + elapsedTimeMin >= 60) {
                            newPlayers.remove(player.uuid());
                        } else {
                            newPlayers.put(player.uuid(), new Utils.Pair<>(newPlayers.get(player.uuid()).first + elapsedTimeMin, pd.buildingsBuilt));
                        }
                    }
                }

                timeStart = System.currentTimeMillis();
            }
        }, 120, 120);
    }

    /**
     * Popup a rate menu for the given player, or all players
     * If a player has already rated the current map, the rate menu is not shown
     *
     * @param p The player to popup, or null for all players
     * @return false if a player has already voted
     */
    private boolean rateMenu(Player p) {
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
            } else if (selection == 2) {
                player.sendMessage("Successfully skipped voting for " + mapName + "[white]!");
            } else {
                return;
            }

            hasRatedMap.add(player.uuid());
            Database.setMapData(md);
        });

        Iterable<Player> players;
        if (p == null) {
            players = Seq.with(p);
        } else {
            players = Groups.player;
        }

        boolean alreadyVoted = false;
        for (var player : players) {
            if (player == null) continue; // ????
            if (hasRatedMap.contains(player.uuid())) {
                alreadyVoted = true;
                continue;
            }
            Call.menu(player.con, id,
                    "Rate this map! [pink]" + mapName,
                    "Do you like this map? Vote [green]yes [white]or [scarlet]no[white]:",
                    new String[][]{
                            new String[]{"[green]Yes", "[scarlet]No", "Skip"},
                            new String[]{"Later"}
                    }
            );
        }
        return !alreadyVoted;
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

        handler.<Player>register("ranks", "Show all ranks.", (args, player) -> { // self info
            StringBuilder sb = new StringBuilder("[accent]Ranks\n");
            for (int i = 0; i < Rank.all.length; i++) {
                Rank rank = Rank.all[i];
                sb.append(Utils.formatName(rank, "[white]" + rank.name) + "\n");
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
        handler.<Player>register("rate", "", "Rate the current map", (args, player) -> {
            if (!rateMenu(player)) {
                player.sendMessage(GameMsg.error("Rate", "You've already rated this map"));
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
                        if (Query.findMap(ranking[i].name) == null) continue;
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
                    data.roles = new long[]{Roles.MOD, Roles.ADMIN};
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
                    for (int i = 0; i < Rank.all.length; i++) {
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

                    target.name = Utils.formatName(pd, target);
                    ctx.success("Rank set", Utils.escapeEverything(target) + "'s rank is now set to " + Rank.all[rank].name);

                    DiscordLog.moderation("Set rank", ctx.author(), Vars.netServer.admins.getInfo(pd.uuid), null, "Rank: " + Rank.all[rank].name + " (" + rank + ")");
                }
        );

        handler.register("subrank", "<list|add|remove> <player> [rank]",
                data -> {
                    data.help = "Set a player's in-game subrank \nList of all ranks:" + Arrays.toString(SubRank.all);
                    data.roles = new long[]{Roles.MOD, Roles.ADMIN};
                    data.category = "Management";
                },
                ctx -> {
                    Player target = Query.findPlayerEntity(ctx.args.get("player"));
                    if (target == null) {
                        ctx.error("Player not found", ctx.args.get("player") + " is not online");
                        return;
                    }

                    var pd = Database.getPlayerData(target.uuid());
                    if (pd == null) {
                        // player not found
                        ctx.error("Player not found", "Player data not found for " + Utils.escapeEverything(target));
                        return;
                    }

                    String action = ctx.args.get("list|add|remove");
                    String rankQuery;
                    int rank = -1;
                    if (!Objects.equals(action, "list")) {
                        rankQuery = ctx.args.get("rank");
                        for (int i = 0; i < SubRank.all.length; i++) {
                            if (Integer.toString(i).equals(rankQuery) || SubRank.all[i].name.equalsIgnoreCase(rankQuery)) {
                                rank = i;
                            }
                        }
                        if (rank == -1) {
                            ctx.error("Invalid rank", rankQuery + " is not a valid rank");
                            return;
                        }
                    }
                    switch (action) {
                        case "list" -> {
                            if (pd.subranks.isEmpty()) {
                                ctx.error("No subranks", Utils.escapeEverything(target) + " has no subranks");
                                return;
                            }

                            StringBuilder table = new StringBuilder("```\n");
                            table.append(String.format("%3s %-10s\n", "", "Subrank"));
                            for (int i = 0; i < pd.subranks.size(); i++) {
                                table.append(String.format("%3s %-10s\n", i + 1, SubRank.all[pd.subranks.get(i)].name));
                            }
                            table.append("```");

                            ctx.sendEmbed(new EmbedBuilder()
                                    .setColor(DiscordPalette.INFO)
                                    .setTitle("Subranks for " + Utils.escapeEverything(target))
                                    .setDescription(table.toString())
                            );
                        }
                        case "add" -> {
                            if (pd.subranks.contains(rank)) {
                                ctx.error("Subrank already exists", Utils.escapeEverything(target) + " already has the subrank " + SubRank.all[rank].name);
                                return;
                            }

                            pd.subranks.add(rank);
                            Database.setPlayerData(pd);

                            target.name = Utils.formatName(pd, target);
                            ctx.success("Subrank added", Utils.escapeEverything(target) + " now has the subrank " + SubRank.all[rank].name);

                            DiscordLog.moderation("Added subrank", ctx.author(), Vars.netServer.admins.getInfo(pd.uuid), null, "Subrank: " + SubRank.all[rank].name + " (" + rank + ")");
                        }
                        case "remove" -> {
                            if (!pd.subranks.contains(rank)) {
                                ctx.error("Subrank not found", Utils.escapeEverything(target) + " does not have the subrank " + SubRank.all[rank].name);
                                return;
                            }

                            pd.subranks.remove((Integer) rank);
                            Database.setPlayerData(pd);

                            target.name = Utils.formatName(pd, target);
                            ctx.success("Subrank removed", Utils.escapeEverything(target) + " no longer has the subrank " + SubRank.all[rank].name);

                            DiscordLog.moderation("Removed subrank", ctx.author(), Vars.netServer.admins.getInfo(pd.uuid), null, "Subrank: " + SubRank.all[rank].name + " (" + rank + ")");
                        }
                    }
                }
        );

        handler.register("setstats", "<player> <playtime> <buildingsbuilt> <gamesplayed>",
                data -> {
                    data.help = "Set a player's game statistics";
                    data.category = "Management";
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD};
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

                    Database.setPlayerData(pd);

                    ctx.success("Set player stats", "Play time: " + playtime + " min\nBuildings built: " + buildingsbuilt + "\nGames played: " + gamesplayed);
                }
        );
    }
}
