package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.ObjectMap;
import arc.util.*;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordLog;
import mindustry.plugin.utils.*;
import org.javacord.api.entity.message.embed.EmbedBuilder;
// import org.jetbrains.annotations.NotNull; not used

import java.awt.*;
import java.time.Instant;

import static mindustry.plugin.minimods.Moderation.frozen;
import static mindustry.plugin.minimods.Ranks.warned;

/**
 * Manages vote kicking
 */
public class Kick implements MiniMod {
    /**
     * Duration to increase voting time by
     */
    private static final long VOTE_TIME = 30 * 1000;
    /**
     * Time before repeated votes, in milliseconds
     */
    private static final long VOTE_COOLDOWN = 120 * 1000;

    /**
     * Kick duration in seconds
     */
    private static final long KICK_DURATION = 60 * 60;

    /* Outline of votekicking:
     *  - Votekicks can be started with the /votekick command.
     *  - Timer starts with 30 seconds. Every 'yes' vote extends the timer by an additional 30 seconds.
     *  - When the timer runs out (30s after last kick), if sufficient votes the player is kicked and banned for 60 minutes.
     *  - If a player leaves while being voted, they are banned for 60 minutes.
     */
    /**
     * Timestamp of end of previous vote, in milliseconds
     */
    private final long previousVoteTime = 0;
    private VoteSession session;

    /**
     * Kick and ban a player.
     */
    private static void kick(VoteSession session) {
        Player target = Groups.player.find(x -> x.uuid().equals(session.target));
        Player plaintiff = Groups.player.find(x -> x.uuid().equals(session.plaintiff));
        String plaintiffName = "";
        if (plaintiff == null) {
            plaintiffName = Utils.calculatePhash(session.plaintiff);
        } else {
            plaintiffName = plaintiff.name;
        }
        if (target != null) {
            target.con.kick("Votekicked by " + plaintiffName);
        }
        plaintiffName = "#" + session.plaintiff.substring(0, 4);
        if (plaintiff != null) {
            plaintiffName = plaintiff.name;
        }
        Undo.instance.rollback(session.target, session.startTime - (3 * 60 * 1000L));
        long banUntil = Instant.now().getEpochSecond() + KICK_DURATION;
        Database.Player pd = Database.getPlayerData(session.target);
        if (pd == null) {
            pd = new Database.Player(session.target, 0);
        }
        pd.bannedUntil = banUntil;
        pd.banReason = "Votekicked by " + plaintiffName + " for 60 minutes";
        Database.setPlayerData(pd);
    }

    @Override
    public void registerEvents() {
        Events.on(EventType.PlayerLeave.class, event -> {
            if (session != null && session.target.equals(event.player.uuid())) {
                Call.sendMessage(GameMsg.info("Kick", "Player [white]" + event.player.name() + " [" + GameMsg.INFO + "] has left while a defendant and will be banned for 60 minutes."));
                kick(session);
                session.clear();
            }
        });
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("vote", "<y/n/c>", "Vote for current votekick", arg -> {
            if (session == null) {
                Log.err("No one is being voted on.");
            } else {
                if (arg[0].equalsIgnoreCase("c")) {
                    Call.sendMessage("[scarlet]Server []canceled the kick.");
                    session.clear();
                }

                int sign = switch (arg[0].toLowerCase()) {
                    case "y", "yes" -> 1;
                    case "n", "no" -> -1;
                    default -> 0;
                };

                if (sign == 0) {
                    Log.err("[scarlet]Vote either 'y' (yes) or 'n' (no).");
                    return;
                }

                session.addVote("server", sign);
            }
        });
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        Cooldowns.instance.set("votekick", 5);
        handler.<Player>register("votekickd", "[player] [reason...]", "votekick a player.", (args, player) -> {
            if (!Cooldowns.instance.canRun("votekick", player.uuid())) {
                player.sendMessage(GameMsg.ratelimit("Kick", "votekick"));
                return;
            }
            Cooldowns.instance.run("votekick", player.uuid());

//               CustomLog.debug("vk @.", args[0]);
            if (!Administration.Config.enableVotekick.bool()) {
                player.sendMessage(GameMsg.error("Kick", "Votekicking is disabled on this server."));
                return;
            }

            if (warned.contains(player.uuid())) {
                player.sendMessage(GameMsg.error("Kick", "You can't votekick other players, because you are flagged as a potential griefer!"));
                return;
            }

            if (Groups.player.size() < 3) {
                player.sendMessage(GameMsg.error("Kick", "At least 3 people are required to start a votekick."));
                return;
            }

            if (session != null) {
                Player target = Groups.player.find(x -> x.uuid().equals(session.target));
                String name = target == null ? session.target : target.name;
                player.sendMessage(GameMsg.error("Kick", "Votekick of []" + name + "[scarlet] already in progress."));
                return;
            }

            if (args.length == 0) {
                player.sendMessage(Utils.playerList(p -> !p.admin && p.con != null && p != player));
                return;
            }
            // Find by id first
            Player found = null;
            String reason = args.length > 1 ? args[1] : "";
            if (args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))) {
                int id = Strings.parseInt(args[0].substring(1));
                found = Groups.player.find(p -> p.id == id);
            }
            if (found == null) found = Query.findPlayerEntity(args[0]);
            if (found == null) found = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));

            if (found == null) {
                player.sendMessage(GameMsg.error("Kick", "No player [orange]" + args[0] + "[scarlet] found."));
                return;
            }

            var pd = Database.getPlayerData(found.uuid());
            if (pd != null && pd.rank >= Rank.APPRENTICE) {
                player.sendMessage(GameMsg.error("Kick", "Can't kick a mod."));
                return;
            }

            if (found == player) {
                player.sendMessage(GameMsg.error("Kick", "Can't kick yourself."));
                return;
            } else if (found.admin) {
                player.sendMessage(GameMsg.error("Kick", "Can't kick an admin."));
                return;
            } else if (found.isLocal()) {
                player.sendMessage(GameMsg.error("Kick", "Can't kick local players."));
                return;
            } else if (found.team() != player.team()) {
                player.sendMessage(GameMsg.error("Kick", "Can't kick players on opposing teams."));
                return;
            }
            if (System.currentTimeMillis() - previousVoteTime < VOTE_COOLDOWN) {
                player.sendMessage(GameMsg.error("Kick", "You must wait " +
                        (System.currentTimeMillis() - previousVoteTime - VOTE_COOLDOWN) / 1000 + " seconds until the next votekick."));
            }

            session = new VoteSession(found.uuid());
            session.plaintiff = player.uuid();
            session.endTime = System.currentTimeMillis() + VOTE_TIME;
            session.addVote(player.uuid(), 1);
            Timer.schedule(new VoteSession.Task(session), VOTE_TIME / 1000);
            Call.sendMessage(GameMsg.info("Kick", "Plaintiff [white]" + player.name + "[" + GameMsg.INFO + "] has voted to kick defendant [white]" + found.name + "[white] for:" + reason +
                    "[" + GameMsg.INFO + "] " + "(1/" + session.requiredVotes() + "). " +
                    "Type [" + GameMsg.CMD + "]/vote y[" + GameMsg.INFO + "] to agree and [" + GameMsg.CMD + "]/vote n[" + GameMsg.INFO + "] to disagree."));

            // freeze the player
            frozen.add(found.uuid());
        });

        handler.<Player>register("vote", "<y/n/c>", "Vote to kick the current player. Or cancel the current kick.", (arg, player) -> {
            if (!Cooldowns.instance.canRun("votekick", player.uuid())) {
                player.sendMessage(GameMsg.ratelimit("Kick", "vote"));
                return;
            }
            Cooldowns.instance.run("votekick", player.uuid());

            if (session == null) {
                player.sendMessage("[scarlet]Nobody is being voted on.");
                return;
            }

            Player target = Groups.player.find(x -> x.uuid().equals(session.target));
            if (target == null) {
                player.sendMessage(GameMsg.error("Kick", "Defendant already left."));
                return;
            }

            if (arg[0].equalsIgnoreCase("c")) {
                if (session.plaintiff.equals(player.uuid()) || player.admin) {
                    session.clear();
                    Call.sendMessage(GameMsg.info("Kick", "Player [orange]" + player.name() + "[" + GameMsg.INFO + "] canceled the votekick of [white]" + target.name));
                } else {
                    player.sendMessage(GameMsg.error("Kick", "[" + GameMsg.CMD + "]/vote c[" + GameMsg.ERROR + "] can only be used by the plaintiff and admins."));
                }
                return;
            }

            if (session.target.equals(player.uuid())) {
                player.sendMessage(GameMsg.error("Kick", "You can't vote on your own trial."));
                return;
            }


            if (target.team() != player.team()) {
                player.sendMessage(GameMsg.error("Kick", "You can't vote for other teams."));
                return;
            }

            int sign = switch (arg[0].toLowerCase()) {
                case "y", "yes" -> 1;
                case "n", "no" -> -1;
                default -> 0;
            };

            if (sign == 0) {
                player.sendMessage(GameMsg.error("Kick", "Vote either 'y' or 'n'."));
                return;
            }

            session.addVote(player.uuid(), sign);
            Call.sendMessage(GameMsg.info("Kick", "Player [white]" + player.name + "[" + GameMsg.INFO + "] has voted to " + (sign > 0 ? "kick" : "not kick") + " [white]" + target.name + "[" + GameMsg.INFO + "] " +
                    "(" + session.countVotes() + "/" + session.requiredVotes() + "). " +
                    "Type [" + GameMsg.CMD + "]/vote y[" + GameMsg.INFO + "] to kick and [" + GameMsg.CMD + "]/vote n[" + GameMsg.INFO + "] to not kick."));
        });

    }

    /**
     * Represents a vote kick session
     */
    private class VoteSession {
        /**
         * UUID of player to be kicked
         */
        public String target;

        /**
         * UUID of player who started votekick
         */
        public String plaintiff = null;

        /**
         * When the votekick started
         */
        public long startTime = Time.millis();
        /**
         * Time in which votekick ends
         */
        public long endTime = -1;

        public boolean canceled = false;

        /**
         * -1 for no and +1 for yes
         */
        public ObjectMap<String, Integer> votes = new ObjectMap<>();

        public VoteSession(String target) {
            this.target = target;
        }

        public int requiredVotes() {
            if (Groups.player.size() <= 3) {
                return 2 - 1;
            } else {
                return 3;
            }
        }

        public void addVote(String uuid, int vote) {
            if (vote == 1) {
                this.endTime = System.currentTimeMillis() + VOTE_TIME;
            }
            votes.put(uuid, vote);
        }

        public int countVotes() {
            int votes = 0;
            for (var entry : this.votes) {
                votes += entry.value;
            }
            return votes;
        }

        /**
         * Cancels the task and removes it from the Kick
         */
        public void clear() {
            canceled = true;
            if (Kick.this.session == this) {
                // unfreeze the player
                frozen.remove(target);
                Kick.this.session = null;
            }
        }

        public static class Task extends Timer.Task {
            private final VoteSession session;

            public Task(VoteSession session) {
                this.session = session;
            }

            @Override
            public void run() {
                if (session.canceled) {
                    this.log();
                    return;
                }

                if (session.endTime > System.currentTimeMillis()) {
                    Timer.schedule(new Task(session), (session.endTime - System.currentTimeMillis()) / 1000);
                    return;
                }

                Player plaintiff = Groups.player.find(x -> x.uuid().equals(session.plaintiff));
                String plaintiffName = plaintiff == null ? "" : Utils.escapeEverything(plaintiff.name);
                Player target = Groups.player.find(x -> x.uuid().equals(session.target));
                if (session.countVotes() >= session.requiredVotes()) {
                    Call.sendMessage(GameMsg.info("Kick", "Vote passed. Defendant [white]" + target.name + "[" + GameMsg.INFO + "] will be banned for 60 minutes."));
                    kick(session);

                    DiscordLog.moderation("Votekick", plaintiffName + " `" + session.plaintiff + "`", target.getInfo(), null, "Succeeded");
                    session.clear();
                } else {
                    Call.sendMessage(GameMsg.info("Kick", "Vote for [white]" + target.name + "[" + GameMsg.INFO + "] failed."));
                    DiscordLog.moderation("Votekick", plaintiffName + " `" + session.plaintiff + "`", target.getInfo(), null, "Failed");
                    // unfreeze player
                    frozen.remove(session.target);
                    session.clear();
                }

                this.log();
            }

            /**
             * Log votekick result to discord
             */
            public void log() {
                boolean success = session.countVotes() >= session.requiredVotes();
                Player target = Groups.player.find(x -> x.uuid().equals(session.target));
                Player plaintiff = Groups.player.find(x -> x.uuid().equals(session.plaintiff));
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("Votekick " + (session.canceled ? "canceled" : (success ? "succeeded" : "failed")) + "!")
                        .addField("Target:", Utils.escapeEverything(target != null ? target.name + "\n" : "") + session.target)
                        .setColor(session.canceled ? new Color(0x0000ff) : (success ? new Color(0xff0000) : new Color(0xFFff00)))
                        .addField("Plaintiff:", Utils.escapeEverything(plaintiff != null ? plaintiff.name + "\n" : "") + session.plaintiff);
                Channels.LOG.sendMessage(eb);
            }
        }
    }
}