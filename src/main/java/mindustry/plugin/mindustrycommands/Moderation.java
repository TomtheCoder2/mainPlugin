package mindustry.plugin.mindustrycommands;

import arc.Events;
import arc.struct.ObjectMap;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timekeeper;
import arc.util.Timer;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.plugin.MiniMod;
import mindustry.plugin.data.PersistentPlayerData;
import mindustry.plugin.database.Database;
import mindustry.plugin.ioMain;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.world.Tile;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;

import java.awt.*;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static mindustry.Vars.netServer;
import static mindustry.Vars.world;

/** Manages kicks, mutes, and bans */
public class Moderation implements MiniMod {
    /** Duration to increase voting time by */
    private static final long VOTE_TIME = 30 * 1000;
    /** Time before repeated votes, in milliseconds */
    private static final long VOTE_COOLDOWN = 120 * 1000;

    /** Kick duration in seconds */
    private static final long KICK_DURATION = 60 * 60;

    /* Outline of votekicking:
     *  - Votekicks can be started with the /votekick command.
     *  - Timer starts with 30 seconds. Every 'yes' vote extends the timer by an additional 30 seconds.
     *  - When the timer runs out (30s after last kick), if sufficient votes the player is kicked and banned for 60 minutes.
     *  - If a player leaves while being voted, they are banned for 60 minutes.
     */

    /** Represents a vote kick session
     */
    private class VoteSession {
        /** UUID of player to be kicked */
        public String target;

        /** UUID of player who started votekick */
        public String plaintiff = null;

        /** Time in which votekick ends*/
        public long endTime = -1;

        public boolean canceled = false;

        /** -1 for no and +1 for yes */
        public ObjectMap<String, Integer> votes = new ObjectMap<>(); 

        public VoteSession(String target) {
            this.target = target;
        }

        public int requiredVotes() {
            if (Groups.player.size() <= 3) {
                return 2;
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

        /** Cancels the task and removes it from the Moderation */
        public void clear() {
            canceled = true;
            if (Moderation.this.session == this)
                Moderation.this.session = null;
        }

        public static class Task extends Timer.Task {
            private VoteSession session;
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
                    Timer.schedule(new Task(session), session.endTime - System.currentTimeMillis());
                    return;
                }

                Player target = Groups.player.find(x -> x.uuid().equals(session.target));
                if (session.countVotes() >= session.requiredVotes()) {
                    Call.sendMessage(GameMsg.info("Kick", "Vote passed. Defendant [orange]" + target.name + "[lightgray] will be banned for 60 minutes."));
                    kick(session);
                    session.clear();
                } else {
                    Call.sendMessage(GameMsg.info("Kick", "Vote for [orange]" + target.name + "[lightgray] failed."));
                    session.clear();
                }

                this.log();
            }

            /** Log votekick result to discord */
            public void log() {
                boolean success = session.countVotes() >= session.requiredVotes();
                Player target = Groups.player.find(x -> x.uuid().equals(session.target));
                Player plaintiff = Groups.player.find(x -> x.uuid().equals(session.plaintiff));
                EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("Votekick " + (session.canceled ? "canceled": (success ? "succeeded" : "failed")) + "!")
                    .addField("Target:", Utils.escapeEverything(target != null ? target.name + "\n" : "") + session.target)
                    .setColor(session.canceled ? new Color(0x0000ff) : (success ? new Color(0xff0000) : new Color(0xFFff00)))
                    .addField("Plaintiff:", Utils.escapeEverything(plaintiff != null ? plaintiff.name + "\n" : "") + session.plaintiff);
                    Utils.getTextChannel(ioMain.log_channel_id).sendMessage(eb);
            }
        }
    }

    /** Timestamp of end of previous vote, in milliseconds */
    private long previousVoteTime = 0;
    private VoteSession session;

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

    /** Kick and ban a player. */
    private static void kick(VoteSession session) {
        Player target = Groups.player.find(x -> x.uuid().equals(session.target));
        Player plaintiff = Groups.player.find(x -> x.uuid().equals(session.plaintiff));
        if (target != null) {
            target.con.kick("Votekicked by " + plaintiff.name);
        }
        String plaintiffName = "#" + session.plaintiff.substring(0, 4);
        if (plaintiff != null ){
            plaintiffName = plaintiff.name;
        }

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
                Call.sendMessage(GameMsg.info("Kick", "[orange]" + event.player.name() + " [lightgray] has left while a defendant and will be banned for 60 minutes."));
                kick(session);
                session.clear();
            }
        });
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("votekick", "[player...]", "votekick a player.", (args, player) -> {
//               CustomLog.debug("vk @.", args[0]);
            if (!Administration.Config.enableVotekick.bool()) {
                player.sendMessage(GameMsg.error("Kick", "Votekicking is disabled on this server."));
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
                StringBuilder builder = new StringBuilder();
                builder.append("[orange]Players to kick: \n");

                Groups.player.each(p -> !p.admin && p.con != null && p != player, p -> {
                    builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id()).append(")\n");
                });
                player.sendMessage(builder.toString());
                return;
            } 

            Player found = Utils.findPlayer(args[0]);
            if (found == null) {
                if (args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))) {
                    int id = Strings.parseInt(args[0].substring(1));
                    found = Groups.player.find(p -> p.id() == id);
                } else {
                    found = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
                }
            }

            if (found == null) {
                player.sendMessage(GameMsg.error("Kick", "No player [orange]" + args[0] + "[scarlet] found."));
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
            } else if (found.uuid().equals("VA8X0BlqyTsAAAAAFkLMBg==")) {
                player.sendMessage(GameMsg.error("Kick", "[cyan]Nautilus[scarlet] is god!!! HOW DARE YOU KICK"));
                return;
            }

            if (System.currentTimeMillis() - previousVoteTime < VOTE_COOLDOWN) {
                player.sendMessage(GameMsg.error("Kick", "You must wait " + 
                    (System.currentTimeMillis() - previousVoteTime - VOTE_COOLDOWN)/1000 + " seconds until the next votekick."));
            }

            session = new VoteSession(found.uuid());
            session.plaintiff = player.uuid();
            session.endTime = System.currentTimeMillis() + VOTE_TIME;
            session.addVote(player.uuid(), 1);
            Timer.schedule(new VoteSession.Task(session), VOTE_TIME);

            Call.sendMessage(GameMsg.info("Kick", "Plaintiff [orange]" + player.name + "[lightgray] has voted to kick defendent [orange]" + found.name + "[lightgray] " +
                "(1/" + session.requiredVotes() + "). " + 
                "Type [sky]/kick y[lightgray] to agree and [sky]/kick n[lightgray] to disagree."));
        });

        handler.<Player>register("vote", "<y/n/c>", "Vote to kick the current player. Or cancel the current kick.", (arg, player) -> {
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
                    Call.sendMessage(GameMsg.info("Kick", "Player [orange]" + player.name() + "[lightgray] canceled the votekick of " + target.name + "."));
                } else {
                    player.sendMessage(GameMsg.error("Kick", "[sky]/vote c[scarlet] can only be used by the plaintiff and admins."));
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

            Call.sendMessage(GameMsg.info("Kick", "Player [orange]" + player.name + "[lightgray] has voted to kick [orange]" + target.name + "[lightgray] " +
                "(" + session.countVotes() + "/" + session.requiredVotes() + "). " + 
                "Type [sky]/kick y[lightgray] to agree and [sky]/kick n[lightgray] to disagree."));

            session.addVote(player.uuid(), sign);
        });

        handler.<Player>register("inspector", "Toggle inspector.", (args, player) -> {
            PersistentPlayerData pd = (ioMain.playerDataGroup.getOrDefault(player.uuid(), null));
            pd.inspector = !pd.inspector;
            player.sendMessage((pd.inspector ? "Enabled" : "Disabled") + " the inspector.");
        });

        handler.<Player>register("freeze", "<player> [reason...]", "Freeze a player. To unfreeze just use this command again.", (args, player) -> {
            if (player.admin()) {
                Player target = Utils.findPlayer(args[0]);
                if (target != null) {
                    PersistentPlayerData tdata = (ioMain.playerDataGroup.getOrDefault(target.uuid(), null));
                    assert tdata != null;
                    tdata.frozen = !tdata.frozen;
                    player.sendMessage("[cyan]Successfully " + (tdata.frozen ? "froze" : "thawed") + " " + Utils.escapeEverything(target));
                    Call.infoMessage(target.con, "[cyan]You got " + (tdata.frozen ? "frozen" : "thawed") + " by a moderator. " + (args.length > 1 ? "Reason: " + args[1] : ""));
                } else {
                    player.sendMessage("Player not found!");
                }
            } else {
                player.sendMessage(Utils.noPermissionMessage);
            }
        });

        handler.<Player>register("mute", "<player> [reason...]", "Mute a player. To unmute just use this command again.", (args, player) -> {
            if (player.admin()) {
                Player target = Utils.findPlayer(args[0]);
                if (target != null) {
                    PersistentPlayerData tdata = (ioMain.playerDataGroup.getOrDefault(target.uuid(), null));
                    assert tdata != null;
                    tdata.muted = !tdata.muted;
                    player.sendMessage("[cyan]Successfully " + (tdata.muted ? "muted" : "unmuted") + " " + Utils.escapeEverything(target));
                    Call.infoMessage(target.con, "[cyan]You got " + (tdata.muted ? "muted" : "unmuted") + " by a moderator. " + (args.length > 1 ? "Reason: " + args[1] : ""));
                } else {
                    player.sendMessage("Player not found!");
                }
            } else {
                player.sendMessage(Utils.noPermissionMessage);
            }
        });
        TextChannel tc_c = Utils.getTextChannel("881300595875643452");
        handler.<Player>register("gr", "[player] [reason...]", "Report a griefer by id (use '/gr' to get a list of ids)", (args, player) -> {
            //https://github.com/Anuken/Mindustry/blob/master/core/src/io/anuke/mindustry/core/NetServer.java#L300-L351
            for (Long key : ioMain.CommandCooldowns.keys()) {
                if (key + ioMain.CDT < System.currentTimeMillis() / 1000L) {
                    ioMain.CommandCooldowns.remove(key);
                } else if (player.uuid().equals(ioMain.CommandCooldowns.get(key))) {
                    player.sendMessage("[scarlet]This command is on a 5 minute cooldown!");
                    return;
                }
            }

            if (args.length == 0) {
                StringBuilder builder = new StringBuilder();
                builder.append("[orange]List or reportable players: \n");
                for (Player p : Groups.player) {
                    if (p.admin() || p.con == null) continue;

                    builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id).append(")\n");
                }
                player.sendMessage(builder.toString());
            } else {
                Player found = null;
                if (args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))) {
                    int id = Strings.parseInt(args[0].substring(1));
                    for (Player p : Groups.player) {
                        if (p.id == id) {
                            found = p;
                            break;
                        }
                    }
                } else {
                    found = Utils.findPlayer(args[0]);
                }
                if (found != null) {
                    if (found.admin()) {
                        player.sendMessage("[scarlet]Did you really expect to be able to report an admin?");
                    } else if (found.team() != player.team()) {
                        player.sendMessage("[scarlet]Only players on your team can be reported.");
                    } else {
                        //send message
                        if (args.length > 1) {
                            Role ro = ioMain.discRoles.get("861523420076179457");
//                                Role role = .getRoleById(661155250123702302L);
                            new MessageBuilder().setEmbed(new EmbedBuilder().setTitle("Potential griefer online")
//                                                .setDescription("<@&861523420076179457>")
                                    .addField("name", Utils.escapeColorCodes(found.name)).addField("reason", args[1]).setColor(Color.RED).setFooter("Reported by " + player.name)).send(tc_c);
                            assert tc_c != null;
                            tc_c.sendMessage("<@&882340213551140935>");
                        } else {
                            Role ro = ioMain.discRoles.get("861523420076179457");
                            new MessageBuilder().setEmbed(new EmbedBuilder().setTitle("Potential griefer online")
//                                                .setDescription("<@&861523420076179457>")
                                    .addField("name", Utils.escapeColorCodes(found.name)).setColor(Color.RED).setFooter("Reported by " + player.name)).send(tc_c);
                            assert tc_c != null;
                            tc_c.sendMessage("<@&882340213551140935>");
                        }
                        Call.sendMessage(found.name + "[sky] is reported to discord.");
                        ioMain.CommandCooldowns.put(System.currentTimeMillis() / 1000L, player.uuid());
                    }
                } else {
                    player.sendMessage("[scarlet]No player[orange] '" + args[0] + "'[scarlet] found.");
                }
            }
        });
        handler.<Player>register("label", "<duration> <text...>", "[admin only] Create an in-world label at the current position.", (args, player) -> {
            if (args[0].length() <= 0 || args[1].length() <= 0)
                player.sendMessage("[scarlet]Invalid arguments provided.");
            if (player.admin) {
                float x = player.getX();
                float y = player.getY();

                Tile targetTile = world.tileWorld(x, y);
                Call.label(args[1], Float.parseFloat(args[0]), targetTile.worldx(), targetTile.worldy());
            } else {
                player.sendMessage(Utils.noPermissionMessage);
            }
        });

        handler.<Player>register("reset", "Set everyone's name back to the original name.", (args, player) -> {
            if (player.admin) {
                for (Player p : Groups.player) {
                    Database.Player pd = Database.getPlayerData(p.uuid());
                    PersistentPlayerData tdata = (ioMain.playerDataGroup.getOrDefault(p.uuid(), null));
                    if (tdata == null) continue; // shouldn't happen, ever
//                    tdata.doRainbow = false;
                    if (pd == null) continue;
                    p.name = Rank.all[pd.rank].tag + netServer.admins.getInfo(p.uuid()).lastName;
                }
                player.sendMessage("[cyan]Reset names!");
            } else {
                player.sendMessage(Utils.noPermissionMessage);
            }
        });
    }
}
