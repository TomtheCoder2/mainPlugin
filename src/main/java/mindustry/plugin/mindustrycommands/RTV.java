package mindustry.plugin.mindustrycommands;

import arc.ApplicationListener;
import arc.Core;
import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.util.CommandHandler;
import arc.util.Reflect;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.plugin.MiniMod;
import mindustry.plugin.utils.GameMsg;
import mindustry.server.ServerControl;

public final class RTV implements MiniMod {
    // map name => set<ip addr>
    private final ObjectMap<String, ObjectSet<String>> votes = new ObjectMap();

    /**
     * Returns the most popular map, or null if it does not exist.
     */
    private String popularMap() {
        int maxVotes = 0;
        String map = null;
        for (var entry : votes) {
            if (entry.value.size > maxVotes) {
                maxVotes = entry.value.size;
                map = entry.key;
            }
        }

        return map;
    }

    private String randomMap() {
        int idx = (int) (Math.random() * (double) Vars.maps.customMaps().size);
        return Vars.maps.customMaps().get(idx).name();
    }

    /**
     * Sets a vote on the `votes` HashMap. Returns the map name. Return value may be null.
     */
    private String setVote(Player player, String mapQuery, boolean vote) {
        Map map = queryMap(mapQuery);
        if (map == null) {
            return null;
        }

        if (!votes.containsKey(map.name())) {
            votes.put(map.name(), new ObjectSet());
        }

        ObjectSet<String> voteSet = votes.get(map.name());
        if (vote) {
            voteSet.add(player.ip());
        } else {
            voteSet.remove(player.ip());
        }
        return map.name();
    }

    /**
     * Gets the number of votes for a particular map.
     */
    private int getVote(String map) {
        ObjectSet<String> voteset = votes.get(map);
        if (voteset == null) {
            return 0;
        } else {
            return voteset.size;
        }
    }

    /**
     * Removes invalid votes from the `votes` hashmap.
     */
    private void removeInvalid() {
        for (ObjectSet<String> voteSet : votes.values()) {
            // Add IP addresses that are no longer in the game
            // to invalidIPs.
            ObjectSet<String> invalidIPs = new ObjectSet();
            for (String ip : voteSet) {
                boolean found = false;
                for (Player player : Groups.player) {
                    if (player.ip().equals(ip)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    invalidIPs.add(ip);
                }
            }

            // remove all from invalid IPs
            for (String ip : invalidIPs) {
                voteSet.remove(ip);
            }
        }
    }

    /**
     * Returns a map for a given query. Return value may be null.
     */
    private Map queryMap(String query) {
        for (Map map : Vars.maps.all()) {
            if (map.name().replaceAll(" ", "").toLowerCase().contains(query.replaceAll(" ", "").toLowerCase())) {
                return map;
            }
        }

        return null;
    }

    private void changeMap(Map map) {
        for (ApplicationListener listener : Core.app.getListeners()) {
            if (listener instanceof ServerControl) {
                Reflect.set(listener, "nextMapOverride", map);
                Events.fire(new EventType.GameOverEvent(Team.crux));
                return;
            }
        }
    }

    /**
     * Returns the number of required votes for a map to pass, which is a simple majority.
     */
    private int requiredVotes() {
        return (Groups.player.size() / 2) + 1;
    }

    /**
     * Returns the name of the map if a vote passed, otherwise null. Assumes that filterInvalid() was already called.
     */
    private String check() {
        for (var entry : votes) {
            if (entry.value.size >= requiredVotes()) {
                return entry.key;
            }
        }

        return null;
    }

    @Override
    public void registerEvents() {
        // Clear votes when a new game occurs.
        Events.on(EventType.GameOverEvent.class, event -> {
            votes.clear();
        });
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("rtv", "[map] [yesno]", "RTV to a different map", (args, player) -> {
            String mapQuery;
            if (args.length == 0) {
                mapQuery = popularMap();
                if (mapQuery == null) {
                    mapQuery = randomMap();
                }
            } else {
                mapQuery = args[0];
            }

            boolean vote = true;
            if (args.length > 1) {
                if (args[1].equals("yes") || args[1].equals("y")) {
                    vote = true;
                } else if (args[1].equals("no") || args[1].equals("n")) {
                    vote = false;
                } else {
                    player.sendMessage(GameMsg.error("RTV", "Vote must be [orange]yes[scarlet] or [orange]no"));
                    return;
                }
            }

            // configure voting
            String map = setVote(player, mapQuery, vote);
            if (map == null) {
                player.sendMessage(GameMsg.error("RTV", "Map [orange]" + mapQuery + "[scarlet] not found."));
            }

            removeInvalid();

            // send message
            int votes = getVote(map);
            Call.sendMessage(GameMsg.info("RTV", "Player [orange]" + player.name + "[lightgray] has " + (vote ? "voted" : "redacted their vote") + " to change the map to [orange]" + map + "[lightgray] " +
                    "(" + votes + "/" + requiredVotes() + ")"));

            // check & change map
            String passedMap = check();
            if (passedMap != null) {
                Call.sendMessage(GameMsg.success("RTV", "Changing map to [orange]" + passedMap));

                Map mapObj = null;
                for (Map map_ : Vars.maps.all()) {
                    if (map_.name().equals(passedMap)) {
                        mapObj = map_;
                        break;
                    }
                }

                // WTF?
                if (mapObj == null) {
                    Call.sendMessage(GameMsg.error("RTV", "Map [orange]" + passedMap + "[scarlet] does not exist."));
                    return;
                }

                changeMap(mapObj);
            }
        });

        handler.<Player>register("rtvotes", "", "Show RTV votes", (args, player) -> {
            removeInvalid();

            if (votes.size == 0) {
                player.sendMessage(GameMsg.info("RTV", "No votes have been cast."));
            } else {
                for (var entry : votes) {
                    player.sendMessage(GameMsg.info("RTV", "Map [orange]" + entry.key + "[lightgray] has [orange]" + entry.value.size + "[lightgray] / " + requiredVotes() + " votes"));
                }
            }
        });
    }
}