package mindustry.plugin.mindustrycommands;

import arc.math.Mathf;
import arc.util.CommandHandler;
import arc.util.Strings;
import arc.util.Timekeeper;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.plugin.data.PersistentPlayerData;
import mindustry.plugin.utils.MapVoteSession;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import static mindustry.Vars.*;
import static mindustry.plugin.ioMain.*;
import static mindustry.plugin.utils.Utils.*;
import static mindustry.plugin.utils.Utils.escapeEverything;

public class Map {
    public Map() {

    }
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("rate", "<positive|negative|advice> [advice...]", "Rate the current map.", (args, player) -> {
            String mapName = Vars.state.map.name();
            TextChannel map_rating_channel = getTextChannel(map_rating_channel_id);
            PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
            switch (args[0]) {
                case "positive" -> {
                    if (!tdata.votedMap) {
                        if (!state.gameOver) {
                            ratePositive(mapName, player);
                            tdata.votedMap = true;
                        } else {
                            player.sendMessage("[scarlet]The game is already over!");
                        }
                    } else {
                        player.sendMessage("[scarlet]You already voted in this game!");
                    }
                }
                case "negative" -> {
                    if (!tdata.votedMap) {
                        if (!state.gameOver) {
                            rateNegative(mapName, player);
                            tdata.votedMap = true;
                        } else {
                            player.sendMessage("[scarlet]The game is already over!");
                        }
                    } else {
                        player.sendMessage("[scarlet]You already voted in this game!");
                    }
                }
                case "advice" -> {
                    EmbedBuilder eb = new EmbedBuilder().setTitle("Feedback for map " + escapeEverything(mapName) + "!").addField("Advice", args[1]).addField("By", escapeEverything(player.name));
                    assert map_rating_channel != null;
                    map_rating_channel.sendMessage(eb);
                    player.sendMessage("Successfully gave an [cyan]advice [white]for " + mapName + "[white]!");
                }
                default -> {
                    player.sendMessage("[white]Select [cyan]/rate [green]positive[white], [scarlet]negative [white]or [white]advice[white]!");
                }
            }
        });

        handler.<Player>register("maps", "[page]", "Display all maps in the playlist.", (args, player) -> { // self info
            if (args.length > 0 && !Strings.canParseInt(args[0])) {
                player.sendMessage("[scarlet]'page' must be a number.");
                return;
            }
            int commandsPerPage = 6;
            int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
            int pages = Mathf.ceil((float) Vars.maps.customMaps().size / commandsPerPage);

            page--;

            if (page >= pages || page < 0) {
                player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] " + pages + "[scarlet].");
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append("[orange]-- Maps Page[lightgray] ").append(page + 1).append("[gray]/[lightgray]").append(pages).append("[orange] --\n\n");

            for (int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), Vars.maps.customMaps().size); i++) {
                mindustry.maps.Map map = Vars.maps.customMaps().get(i);
                result.append("[white] - [accent]").append(escapeColorCodes(map.name())).append("\n");
            }
            player.sendMessage(result.toString());
        });

        Timekeeper vtime = new Timekeeper(voteCooldown);

        final MapVoteSession[] currentMapVoting = {null};

        handler.<Player>register("rtv", "[map...]", "Vote to change the map.", (args, player) -> {
            if (currentMapVoting[0] == null) {
                mindustry.maps.Map found;
                if (args.length > 0) {
                    found = getMapBySelector(args[0]);
                    if (found == null) {
                        String targetMap = escapeEverything(args[0]);
                        for (mindustry.maps.Map map : maps.customMaps()) {
                            if (escapeEverything(map.name()).startsWith(targetMap)) {
                                found = map;
                                break;
                            }
                        }
                    }
                } else {
                    found = getMapBySelector(String.valueOf((int) (Math.random() * 5)));
                }
                if (found != null) {
                    if (!vtime.get()) {
                        player.sendMessage("[scarlet]You must wait " + voteCooldown / 20 + " minutes between nominations.");
                        return;
                    }

                    MapVoteSession session = new MapVoteSession(currentMapVoting[0], found);

                    if (!session.vote(player, 1)) {
                        currentMapVoting[0] = session;
                    } else {
                        currentMapVoting[0] = null;
                    }
                    vtime.reset();
                } else {
                    player.sendMessage("[scarlet]No map[orange]'" + args[0] + "'[scarlet] found.");
                }
            } else {
                //hosts can vote all they want
                if (player.uuid() != null &&
                        (currentMapVoting[0].voted.contains(player.uuid()) ||
                                currentMapVoting[0].voted.contains(netServer.admins.getInfo(player.uuid()).lastIP))) {
                    player.sendMessage("[scarlet]You've already voted. Sit down.");
                    return;
                }

                if (currentMapVoting[0].vote(player, 1)) {
                    currentMapVoting[0] = null;
                }
            }
        });

//            mapChange.registerClientCommands(handler);

//            handler.<Player>register("rtv", "Vote to change the map.", (args, player) -> { // self info
//                if (currentMapVoting[0][0] == null) {
//                    player.sendMessage("[scarlet]No map is being voted on.");
//                } else {
//                    //hosts can vote all they want
//                    if (player.uuid() != null && (currentMapVoting[0][0].voted.contains(player.uuid()) || currentMapVoting[0][0].voted.contains(netServer.admins.getInfo(player.uuid()).lastIP))) {
//                        player.sendMessage("[scarlet]You've already voted. Sit down.");
//                        return;
//                    }
//
//                    currentMapVoting[0][0].vote(player, 1);
//                }
//            });
    }
}
