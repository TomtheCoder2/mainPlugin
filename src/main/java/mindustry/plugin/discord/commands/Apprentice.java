package mindustry.plugin.discord.commands;

import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.Packets;
import mindustry.plugin.data.PersistentPlayerData;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.discordcommands.Context;
import mindustry.plugin.discord.discordcommands.DiscordCommands;
import mindustry.plugin.discord.discordcommands.RoleRestrictedCommand;
import mindustry.plugin.requests.GetMap;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.JSONObject;

import arc.util.Strings;

import java.time.Instant;

import static mindustry.Vars.netServer;
import static mindustry.Vars.player;
import static mindustry.plugin.ioMain.playerDataGroup;
import static mindustry.plugin.utils.DiscordLog.logAction;
import static mindustry.plugin.utils.LogAction.ban;
import static mindustry.plugin.utils.Utils.Categories.moderation;
import static mindustry.plugin.utils.Utils.*;

public class Apprentice {
    private final JSONObject data;
    public GetMap map = new GetMap();

    public Apprentice(JSONObject data) {
        this.data = data;
    }

    public void registerCommands(DiscordCommands handler) {
        if (data.has("apprentice_roleid")) {
            long apprenticeRole = Strings.parseLong(data.getString("apprentice_roleid"), 0);
/*
            handler.registerCommand(new RoleRestrictedCommand("banish") {
                {
                    help = "Ban the provided player for a specific duration with a specific reason.";
                    roles = new long[] { apprenticeRole };
                    usage = "<player> <duration (minutes)> <reason...>";
                    category = moderation;
                    apprenticeCommand = true;
                    minArguments = 2;
                    aliases.add("b");
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    String targetDuration = ctx.args[2];
                    String reason;
                    long now = Instant.now().getEpochSecond();

                    Administration.PlayerInfo info = getPlayerInfo(target);
                    if (info != null) {
                        String uuid = info.id;
                        String banId = uuid.substring(0, 4);
                        Database.Player pd = Database.getPlayerData(uuid);
                        long until;
                        try {
                            until = now + Integer.parseInt(targetDuration) * 60L;
                            reason = ctx.message.substring(target.length() + targetDuration.length() + 2);
                        } catch (Exception e) {
//                                EmbedBuilder err = new EmbedBuilder()
//                                        .setTitle("Second argument has to be a number!")
//                                        .setColor(new Color(0xff0000));
//                                ctx.channel.sendMessage(err);
//                                return;
                            e.printStackTrace();
                            until = now + (long) (2 * 356 * 24 * 60 * 60); // 2 years
                            reason = ctx.message.substring(target.length() + 1);
                        }
                        if (pd != null) {
                            pd.bannedUntil = until;
                            pd.banReason = reason + "\n" + "[accent]Until: " + epochToString(until) + "\n[accent]Ban ID:[] " + banId;
                            Database.setPlayerData(pd);

                            eb.setTitle("Banned " + escapeEverything(info.lastName) + " for " + targetDuration + " minutes. ");
                            eb.addField("Ban ID", banId);
                            eb.addField("For", (until - now) / 60 + " minutes.");
                            eb.addField("Until", epochToString(until));
                            eb.addInlineField("Reason", reason);
                            ctx.sendMessage(eb);

                            Player player = findPlayer(uuid);
                            if (player != null) {
                                player.con.kick(Packets.KickReason.banned);
                            }
                            logAction(ban, info, ctx, reason);
                        } else {
                            playerNotFound(target, eb, ctx);
                        }
                    } else {
                        playerNotFound(target, eb, ctx);
                    }
                }
            });
*/
            handler.registerCommand(new RoleRestrictedCommand("alert") {
                {
                    help = "Alerts a player(s) using on-screen messages.";
                    roles = new long[] { apprenticeRole };
                    usage = "<playerid|ip|name|teamid> <message>";
                    category = moderation;
                    apprenticeCommand = true;
                    minArguments = 2;
                    aliases.add("a");
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1].toLowerCase();

                    if (target.equals("all")) {
                        for (Player p : Groups.player) {
                            Call.infoMessage(p.con, ctx.message.split(" ", 2)[1]);
                        }
                        eb.setTitle("Command executed");
                        eb.setDescription("Alert was sent to all players.");
                        ctx.sendMessage(eb);
                    } else if (target.matches("[0-9]+") && target.length() == 1) {
                        for (Player p : Groups.player) {
                            p.sendMessage("hello", player);
                            if (p.team().id == Byte.parseByte(target)) {
                                Call.infoMessage(p.con, ctx.message.split(" ", 2)[1]);
                            }
                        }
                        eb.setTitle("Command executed");
                        eb.setDescription("Alert was sent to all players.");
                        ctx.sendMessage(eb);
                    } else {
                        Player p = findPlayer(target);
                        if (p != null) {
                            Call.infoMessage(p.con, ctx.message.split(" ", 2)[1]);
                            eb.setTitle("Command executed");
                            eb.setDescription("Alert was sent to " + escapeEverything(p));
                        } else {
                            eb.setTitle("Command terminated");
                            eb.setColor(Pals.error);
                            eb.setDescription("Player could not be found or is offline.");

                        }
                        ctx.sendMessage(eb);
                    }
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("info") {
                {
                    help = "Get info about a specific player.";
                    usage = "<player>";
                    roles = new long[] { apprenticeRole };
                    category = moderation;
                    apprenticeCommand = true;
                    minArguments = 1;
                    aliases.add("i");
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    long now = Instant.now().getEpochSecond();

                    Administration.PlayerInfo info = null;
                    Player player = findPlayer(target);
                    if (player != null) {
                        info = netServer.admins.getInfoOptional(player.uuid());
                    }

                    if (info != null) {
                        eb.setTitle(escapeEverything(info.lastName) + "'s info");
                        StringBuilder s = lookup(eb, info);
                        eb.setDescription(s.toString());

                    } else {
                        eb.setTitle("Command terminated");
                        eb.setColor(Pals.error);
                        eb.setDescription("Player could not be found or is offline.");
                    }
                    ctx.sendMessage(eb);
                }
            });
        }
    }
}
