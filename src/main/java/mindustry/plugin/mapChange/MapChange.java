package mindustry.plugin.mapChange;

import arc.Core;
import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.*;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.mod.Plugin;
import mindustry.server.ServerControl;

import static mindustry.Vars.maps;

public class MapChange extends Plugin {
    public Map currentlyVoting = null;
    public int currectSelectVotes = 0;
    public Timer.Task selectVoteTask = null;
    public Seq<String> selectVoted = new Seq<>(); // UUIDs of players that voted

    public int currentSkipVotes = 0;
    public ObjectMap<String, Boolean> skipVoted = new ObjectMap<>(); // UUIDs of players that voted mapped to their vote

    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("changemap", "[name...]", "Vote to change the next map to be played, specified by name or #id. Does not end the game. Use without arguments to display maplist.", (args, player) -> {
            try {
                if (currentlyVoting != null) {
                    if (args[0].equalsIgnoreCase("yes") || args[0].equalsIgnoreCase("y")) {
                        if (selectVoted.contains(player.uuid())) {
                            player.sendMessage("[scarlet]You can't vote twice.");
                        } else {
                            voteSelect(player);
                        }
                    } else {
                        player.sendMessage("[scarlet]A vote is already in progress.");
                    }
                    return;
                }
                if (args.length == 0) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("[orange]Maps: \n");
                    int id = 0;
                    for (Map m : availableMaps()) {
                        builder.append("[accent]id:").append(id).append("[white] ").append(m.name()).append(" ");
                        id++;
                    }
                    player.sendMessage(builder.toString());
                    return;
                }
                Map found;
                if (args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))) {
                    int id = Strings.parseInt(args[0].substring(1));
                    found = availableMaps().get(id);
                } else {
                    found = findMap(args[0]);
                }
                if (found == null) {
                    player.sendMessage("[scarlet]No map [orange]'" + args[0] + "'[scarlet] found.");
                    return;
                }
                currentlyVoting = found;
                voteSelect(player);
                selectVoteTask = Timer.schedule(() -> {
                    Call.sendMessage("[scarlet]Next map override vote failed.");
                    currentlyVoting = null;
                    selectVoted.clear();
                    currectSelectVotes = 0;
                }, Config.selectVoteLength.f());
            } catch (Exception badArguments) {
                player.sendMessage("[scarlet]Something went wrong. Please check command arguments.");
            }
        });

        handler.<Player>register("rtv", "<y/n>", "Vote to skip the current map.", (args, player) -> {
            if (args[0].equalsIgnoreCase("y") || args[0].equalsIgnoreCase("yes")) {
                voteSkip(player, true);
            } else if (args[0].equalsIgnoreCase("n") || args[0].equalsIgnoreCase("no")) {
                voteSkip(player, false);
            } else {
                player.sendMessage("[scarlet]Voteskip only accepts 'y' (yes) or 'n' (no) as arguments.");
            }
        });
    }

    public void voteSelect(Player player) {
        selectVoted.add(player.uuid());
        currectSelectVotes++;
        int voteCap = (int) Math.ceil((float) Groups.player.size() * Config.selectVoteFraction.f());
        Call.sendMessage(player.coloredName() + " [lightgray]has voted to select " + currentlyVoting.name() + " [lightgray]as the next map (" + currectSelectVotes + "/" + voteCap + "). Type '/rtv yes' to agree.");
        if (currectSelectVotes >= voteCap) {
            Core.app.getListeners().each(lst -> {
                if (lst instanceof ServerControl) {
                    ServerControl scont = (ServerControl) lst;
                    Reflect.set(scont, "nextMapOverride", currentlyVoting);
                }
            });
            Call.sendMessage("[accent]Next map overridden to be " + currentlyVoting.name() + "[accent].");
            currentlyVoting = null;
            selectVoted.clear();
            currectSelectVotes = 0;
            selectVoteTask.cancel();
        }
    }

    public void voteSkip(Player player, boolean agree) {
        int voteCap = (int) Math.ceil((float) Groups.player.size() * Config.skipVoteFraction.f());
        if (skipVoted.isEmpty()) {
            Timer.schedule(() -> {
                if (currentSkipVotes < (int) Math.ceil((float) Groups.player.size() * Config.skipVoteFraction.f())) {
                    Call.sendMessage("[scarlet]Map skip vote failed.");
                } else {
                    Call.sendMessage("[accent]Map skip vote successful.");
                    Events.fire(new GameOverEvent(Team.derelict));
                }
                skipVoted.clear();
                currentSkipVotes = 0;
            }, Config.skipVoteLength.f());
        }
        if (skipVoted.containsKey(player.uuid())) {
            if (skipVoted.get(player.uuid()) != agree) {
                currentSkipVotes += agree ? 1 : -1;
                skipVoted.remove(player.uuid());
                player.sendMessage("[accent]Swapped vote.");
            } else {
                player.sendMessage("[scarlet]You can't vote twice.");
                return;
            }
        }
        skipVoted.put(player.uuid(), agree);
        currentSkipVotes += agree ? 1 : -1;
        Call.sendMessage(player.coloredName() + " [lightgray]has voted to skip map (" + currentSkipVotes + "/" + voteCap + "). Type '/rtv y/n' to agree or disagree.");
    }

    public Map findMap(String mapName) {
        return availableMaps().find(map -> Strings.stripColors(map.name().replace('_', ' ')).equalsIgnoreCase(Strings.stripColors(mapName).replace('_', ' ')));
    }

    public Seq<Map> availableMaps() {
        return Config.allowBuiltinMaps.b() ? maps.all() : maps.customMaps();
    }

    public void registerServerCommands(CommandHandler handler) {
        handler.register("mapchangeconfig", "[name] [value]", "Configure bot plugin settings. Run with no arguments to list values.", args -> {
            if (args.length == 0) {
                Log.info("All config values:");
                for (Config c : Config.all) {
                    Log.info("&lk| @: @", c.name(), "&lc&fi" + c.s());
                    Log.info("&lk| | &lw" + c.description);
                    Log.info("&lk|");
                }
                return;
            }
            try {
                Config c = Config.valueOf(args[0]);
                if (args.length == 1) {
                    Log.info("'@' is currently @.", c.name(), c.s());
                } else {
                    if (args[1].equals("default")) {
                        c.set(c.defaultValue);
                    } else {
                        try {
                            if (c.defaultValue instanceof Float) {
                                c.set(Float.parseFloat(args[1]));
                            } else {
                                c.set(Boolean.parseBoolean(args[1]));
                            }
                        } catch (NumberFormatException e) {
                            Log.err("Not a valid number: @", args[1]);
                            return;
                        }
                    }
                    Log.info("@ set to @.", c.name(), c.s());
                    Core.settings.forceSave();
                }
            } catch (IllegalArgumentException e) {
                Log.err("Unknown config: '@'. Run the command with no arguments to get a list of valid configs.", args[0]);
            }
        });
    }

    public enum Config {
        selectVoteFraction("The fraction of players that need to vote for map selection.", 0.4f),
        selectVoteLength("The length, in seconds, of a map selection vote.", 30f),
        allowBuiltinMaps("Whether to allow built-in maps in selection votes.", true),
        skipVoteFraction("The fraction of players that need to vote to skip the current map.", 0.5f),
        skipVoteLength("The length, in seconds, of a map skip vote.", 40f);

        public static final Config[] all = values();

        public final Object defaultValue;
        public String description;

        Config(String description, Object value) {
            this.description = description;
            this.defaultValue = value;
        }

        public float f() {
            return Core.settings.getFloat(name(), (float) defaultValue);
        }

        public boolean b() {
            return Core.settings.getBool(name(), (boolean) defaultValue);
        }

        public String s() {
            return Core.settings.get(name(), defaultValue).toString();
        }

        public void set(Object value) {
            Core.settings.put(name(), value);
        }
    }
}
