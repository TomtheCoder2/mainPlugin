package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Query;

public final class RTV implements MiniMod {
    public static long VOTE_TIME = 60 * 1000;
    private Session session;

    private static void changeMap(Map map) {
        Vars.maps.setNextMapOverride(map);
        Events.fire(new EventType.GameOverEvent(Team.crux));
    }

    private String randomMap() {
        int idx = (int) (Math.random() * (double) Vars.maps.customMaps().size);
        return Vars.maps.customMaps().get(idx).name();
    }

    /**
     * Returns a map for a given query. Return value may be null.
     */
    private Map queryMap(String query) {
        String normalisedQuery = Strings.stripColors(query.replaceAll(" ", "")).toLowerCase();
        Seq<Map> matched = Vars.maps.all().select(map -> {
            String mapName = Strings.stripColors(map.name().replaceAll(" ", "")).toLowerCase();
            return mapName.contains(normalisedQuery);
        });
        if (matched.size == 1) return matched.first();
        return null;
    }

    /**
     * Returns the number of required votes for a map to pass, which is a simple majority.
     */
    private int requiredVotes() {
        return (Groups.player.size() / 2) + 1;
    }

    @Override
    public void registerEvents() {
        // Clear votes when a new game occurs.
        Events.on(EventType.WorldLoadEvent.class, event -> {
            if (session != null) {
                session.clear();
            }
        });
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("rtv", "[map/n...]", "RTV to a different map", (args, player) -> {
            if (session == null) {
                String map = randomMap();
                if (args.length != 0) {
                    Map mapObj = queryMap(args[0]);
                    if (mapObj == null) {
                        player.sendMessage(GameMsg.error("RTV", "Could not find map [orange]" + args[0]));
                        return;
                    }
                    map = mapObj.name();
                }

                session = new Session(map);
            } else {
                if (args.length != 0 && !(args[0].equalsIgnoreCase("no") || args[0].equalsIgnoreCase("n") || args[0].equalsIgnoreCase("yes") || args[0].equalsIgnoreCase("y"))) {
                    player.sendMessage(GameMsg.error("RTV", "Voting for map [orange]" + session.map + "[scarlet] has already begun"));
                    return;
                }
            }

            // set vote
            boolean vote = !(args.length != 0 && (args[0].equalsIgnoreCase("no") || args[0].equalsIgnoreCase("n")));
            if (vote) {
                if (session.votes.contains(player.uuid())) {
                    player.sendMessage(GameMsg.error("RTV", "You've already voted! Type [sky]/rtv n[scarlet] to redact your vote."));
                    return;
                }

                session.votes.add(player.uuid());
            } else {
                if (!session.votes.contains(player.uuid())) {
                    player.sendMessage(GameMsg.error("RTV", "You haven't voted, so you can't redact your vote! Type [sky]/rtv[scarlet] to vote."));
                    return;
                }

                session.votes.remove(player.uuid());
            }

            // extend voting time
            session.endTime = System.currentTimeMillis() + VOTE_TIME;

            session.removeInvalid();

            // send message
            int votes = session.votes.size;
            Call.sendMessage(GameMsg.info("RTV", "Player [orange]" + player.name + "[lightgray] has " + (vote ? "voted" : "redacted their vote") + " to change the map to [orange]" + session.map + "[lightgray] " +
                    "(" + votes + "/" + requiredVotes() + "). " + (vote ? "Type [sky]/rtv[lightgray] to vote." : "")));

            // check & change map
            boolean passed = votes >= requiredVotes();
            if (passed) {
                Call.sendMessage(GameMsg.success("RTV", "Changing map to [orange]" + session.map));

                Map mapObj = Vars.maps.all().find(x -> x.name().equals(session.map));

                // WTF?
                if (mapObj == null) {
                    Call.sendMessage(GameMsg.error("RTV", "Map [orange]" + session.map + "[scarlet] does not exist."));
                    return;
                }

                changeMap(mapObj);
            }
        });

        handler.<Player>register("rtvotes", "", "Show RTV votes", (args, player) -> {
            if (session == null) {
                player.sendMessage(GameMsg.info("RTV", "No votes have been cast."));
            }
            session.removeInvalid();

            player.sendMessage(GameMsg.info("RTV", "[orange]" + session.map + "[lightgray] - [orange]" + session.votes.size + "[lightgray] / " + requiredVotes() + " votes"));
        });
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("changemap", "<map...>",
                data -> {
                    data.help = "Change map to the one specified";
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD, Roles.APPRENTICE};
                    data.category = "Management";
                },
                ctx -> {
                    Map map = Query.findMap(ctx.args.get("map"));
                    if (map == null) {
                        ctx.error("No such map", "Map '" + ctx.args.get("map") + "' not found");
                    }

                    changeMap(map);
                    ctx.success("Changed map", "Forced game over and changed map to " + map.name());
                }
        );
    }

    private class Session {
        /**
         * UUIDs of people who vote
         */
        public ObjectSet<String> votes = new ObjectSet<>();

        /**
         * Name of map
         */
        public String map;

        /**
         * Time at which the RTV session ends
         */
        public long endTime;

        /**
         * Whether the Task should stop itself
         */
        public boolean canceled;

        public Session(String map) {
            this.map = map;
            this.endTime = System.currentTimeMillis() + VOTE_TIME;

            Timer.schedule(new Task(this), (this.endTime - System.currentTimeMillis()) / 1000);
        }

        /**
         * Setes canceled to true and removes from the RTV
         */
        public void clear() {
            canceled = true;
            if (RTV.this.session == Session.this)
                RTV.this.session = null;
        }

        /**
         * Removes UUIDs of players that are no longer in Gruops.players
         */
        public void removeInvalid() {
            ObjectSet<String> invalids = new ObjectSet<>();
            for (String uuid : votes) {
                Player player = Groups.player.find(x -> x.uuid().equals(uuid));
                if (player == null) {
                    invalids.add(uuid);
                }
            }
            for (String invalid : invalids) {
                votes.remove(invalid);
            }
        }

        /**
         * This task is responsible for ending the session if the time has expired
         */
        public static class Task extends Timer.Task {
            Session session;

            public Task(Session session) {
                this.session = session;
            }

            @Override
            public void run() {
                if (session.canceled) {
                    return;
                }

                if (System.currentTimeMillis() < session.endTime) {
                    Timer.schedule(new Task(session), (session.endTime - System.currentTimeMillis()) / 1000);
                    return;
                }

                // vote failed if not canceled
                Call.sendMessage(GameMsg.error("RTV", "Vote for [orange]" + session.map + "[scarlet] has failed."));
                session.clear();
            }
        }
    }
}