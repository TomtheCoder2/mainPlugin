package mindustry.plugin;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.core.GameState;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.io.SaveIO;
import mindustry.maps.Map;
import mindustry.net.Administration;
import mindustry.net.Packets;
import mindustry.plugin.discordcommands.Command;
import mindustry.plugin.discordcommands.Context;
import mindustry.plugin.discordcommands.DiscordCommands;
import mindustry.plugin.discordcommands.RoleRestrictedCommand;
import mindustry.plugin.requests.GetMap;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.json.JSONObject;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.zip.InflaterInputStream;

import static mindustry.Vars.*;
import static mindustry.plugin.Utils.*;

public class ServerCommands {
    public GetMap map = new GetMap();

    private JSONObject data;

    public ServerCommands(JSONObject data) {
        this.data = data;
    }

    public void registerCommands(DiscordCommands handler) {

        handler.registerCommand(new Command("maps") {
            {
                help = "Check a list of available maps and their ids.";
            }

            public void run(Context ctx) {
                StringBuilder msg = new StringBuilder().append("**All available maps in the playlist:**\n```");
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("**All available maps in the playlist:**");
                Seq<Map> mapList = maps.customMaps();
                for (int i = 0; i < mapList.size; i++) {
                    Map m = mapList.get(i);
                    msg.append(i).append(" : ").append(m.name()).append(" : ").append(m.width).append(" x ").append(m.height).append("\n");
                    eb.addField(m.name(), m.width + " x " + m.height);
                }
                msg.append("```");
//                ctx.channel.sendMessage(msg.toString());
                ctx.channel.sendMessage(eb);
            }
        });
        if (data.has("administrator_roleid")) {
            String adminRole = data.getString("administrator_roleid");
            // TODO: make an update command
        }

        if (data.has("exit_roleid")) {
            handler.registerCommand(new RoleRestrictedCommand("exit") {
                {
                    help = "Close the server.";
                    role = data.getString("exit_roleid");
                }

                public void run(Context ctx) {
                    net.dispose();
                    Core.app.exit();
                }
            });
        }

        if (data.has("apprentice_roleid")) {
            String apprenticeRole = data.getString("apprentice_roleid");

            handler.registerCommand(new RoleRestrictedCommand("banish") {
                {
                    help = "Ban the provided player for a specific duration with a specific reason.";
                    role = apprenticeRole;
                    usage = "<player> <duration (minutes)> [reason...]";
                    category = "moderation";
                }

                public void run(Context ctx) {
                    CompletableFuture.runAsync(() -> {
                        EmbedBuilder eb = new EmbedBuilder();
                        String target = ctx.args[1];
                        String targetDuration = ctx.args[2];
                        String reason = ctx.message.substring(target.length() + targetDuration.length() + 2);
                        long now = Instant.now().getEpochSecond();

                        Player player = findPlayer(target);
                        if (player != null) {
                            String uuid = player.uuid();
                            String banId = uuid.substring(0, 4);
                            PlayerData pd = getData(uuid);
                            long until = now + Integer.parseInt(targetDuration) * 60L;
                            if (pd != null) {
                                pd.bannedUntil = until;
                                pd.banReason = reason + "\n" + "[accent]Until: " + epochToString(until) + "\n[accent]Ban ID:[] " + banId;
                                setData(uuid, pd);
                            }

                            eb.setTitle("Banned" + escapeCharacters(player.name) + " for " + targetDuration + " minutes. ");
                            eb.addField("Ban ID", banId);
                            eb.addField("For", targetDuration + " minutes.");
                            eb.addField("Until", epochToString(until));
                            eb.addInlineField("Reason", reason);
                            ctx.channel.sendMessage(eb);

                            player.con.kick(Packets.KickReason.banned);
                        } else {
                            eb.setTitle("Player `" + escapeCharacters(target) + "` not found.");
                            eb.setColor(Pals.error);
                            ctx.channel.sendMessage(eb);
                        }
                    });
                }
            });
        }
        if (data.has("moderator_roleid")) {
            String banRole = data.getString("moderator_roleid");

            handler.registerCommand(new RoleRestrictedCommand("changemap") {
                {
                    help = "Change the current map to the one provided.";
                    role = banRole;
                    usage = "<mapname/mapid>";
                    category = "management";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    if (ctx.args.length < 2) {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Pals.error);
                        eb.setDescription("Not enough arguments, use `%changemap <mapname|mapid>`".replace("%", ioMain.prefix));
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    Map found = getMapBySelector(ctx.message.trim());
                    if (found == null) {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Pals.error);
                        eb.setDescription("Map \"" + escapeCharacters(ctx.message.trim()) + "\" not found!");
                        ctx.channel.sendMessage(eb);
                        return;
                    }

                    changeMap(found);

                    eb.setTitle("Command executed.");
                    eb.setDescription("Changed map to " + found.name());
                    ctx.channel.sendMessage(eb);

                    maps.reload();
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("announce") {
                {
                    help = "Announces a message to in-game chat.";
                    role = banRole;
                    usage = "<message>";
                    category = "moderation";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();

                    if (ctx.message.length() <= 0) {
                        eb.setTitle("Command terminated");
                        eb.setColor(Pals.error);
                        eb.setDescription("No message given");
                        ctx.channel.sendMessage(eb);
                        return;
                    }

                    for (Player p : Groups.player) {
                        Call.infoMessage(p.con, ctx.message);
                    }

                    eb.setTitle("Command executed");
                    eb.setDescription("Your message was announced.");
                    ctx.channel.sendMessage(eb);

                }
            });

            handler.registerCommand(new RoleRestrictedCommand("event") {
                {
                    help = "Changes the event command ip.";
                    role = banRole;
                    usage = "<ip/none>";
                    category = "admin";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();

                    if (ctx.message.length() <= 0) {
                        eb.setTitle("Command terminated");
                        eb.setColor(Pals.error);
                        eb.setDescription("No message given");
                        ctx.channel.sendMessage(eb);
                        return;
                    }

                    if (ctx.message.toLowerCase().contains("none")) {
                        eventIp = "";
                        eb.setTitle("Command executed");
                        eb.setDescription("Event command is now disabled.");
                        ctx.channel.sendMessage(eb);
                        return;
                    }

                    String[] m = ctx.message.split(":");
                    eventIp = m[0];
                    eventPort = Integer.parseInt(m[1]);

                    eb.setTitle("Command executed");
                    eb.setDescription("Event ip was changed to " + ctx.message);
                    ctx.channel.sendMessage(eb);
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("admin") {
                {
                    help = "Toggle the admin status on a player.";
                    role = banRole;
                    usage = "<playerid|ip|name|teamid> <message>";
                    category = "moderation";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1].toLowerCase();

                    Player p = findPlayer(target);
                    if (p != null) {
//                        Call.infoMessage(p.con, ctx.message.split(" ", 2)[1]);
                        p.admin = !p.admin;
                        eb.setTitle("Command executed!");
                        if (p.admin) {
                            eb.setDescription("Promoted " + escapeColorCodes(p.name.replaceAll(" ", "")).replaceAll("<.*?>", "").replaceAll("\\[.*?\\]", "") + " to admin");
                        } else {
                            eb.setDescription("Demoted " + escapeColorCodes(p.name.replaceAll(" ", "")).replaceAll("<.*?>", "").replaceAll("\\[.*?\\]", "") + " to admin");
                        }
                    } else {
                        eb.setTitle("Command terminated!");
                        eb.setColor(Pals.error);
                        eb.setDescription("Player could not be found or is offline.");

                    }
                    ctx.channel.sendMessage(eb);
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("alert") {
                {
                    help = "Alerts a player(s) using on-screen messages.";
                    role = banRole;
                    usage = "<playerid|ip|name|teamid> <message>";
                    category = "moderation";
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
                        ctx.channel.sendMessage(eb);
                    } else if (target.matches("[0-9]+") && target.length() == 1) {
                        for (Player p : Groups.player) {
                            if (p.team().id == Byte.parseByte(target)) {
                                Call.infoMessage(p.con, ctx.message.split(" ", 2)[1]);
                            }
                        }
                        eb.setTitle("Command executed");
                        eb.setDescription("Alert was sent to all players.");
                        ctx.channel.sendMessage(eb);
                    } else {
                        Player p = findPlayer(target);
                        if (p != null) {
                            Call.infoMessage(p.con, ctx.message.split(" ", 2)[1]);
                            eb.setTitle("Command executed");
                            eb.setDescription("Alert was sent to " + escapeColorCodes(p.name.replaceAll(" ", "")).replaceAll("<.*?>", "").replaceAll("\\[.*?\\]", ""));
                        } else {
                            eb.setTitle("Command terminated");
                            eb.setColor(Pals.error);
                            eb.setDescription("Player could not be found or is offline.");

                        }
                        ctx.channel.sendMessage(eb);
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("gameover") {
                {
                    help = "Force a game over.";
                    role = banRole;
                    category = "management";
                }

                public void run(Context ctx) {
                    if (state.is(GameState.State.menu)) {
                        ctx.reply("Invalid state");
                        return;
                    }
                    Events.fire(new GameOverEvent(Team.crux));
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Command executed.")
                            .setDescription("Done. New game starting in 10 seconds.");
                    ctx.channel.sendMessage(eb);
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("ban") {
                {
                    help = "Ban the provided player with a specific reason.";
                    usage = "<player> [reason..]";
                    role = banRole;
                    category = "moderation";
                }

                public void run(Context ctx) {
                    CompletableFuture.runAsync(() -> {
                        EmbedBuilder eb = new EmbedBuilder();
                        String target = ctx.args[1];
                        String reason = ctx.message.substring(target.length() + 1);

                        Player player = findPlayer(target);
                        if (player != null) {
                            String uuid = player.uuid();
                            String banId = uuid.substring(0, 4);
                            PlayerData pd = getData(uuid);
                            if (pd != null) {
                                pd.banned = true;
                                pd.banReason = reason + "\n[accent]Ban ID:[] " + banId;
                                setData(uuid, pd);
                            }
                            netServer.admins.banPlayerIP(player.con.address);
                            eb.setTitle("Banned `" + escapeCharacters(player.name) + "` permanently.");
                            eb.addField("UUID", uuid);
                            eb.addField("Ban ID", banId);
                            eb.addField("IP", player.con.address);
                            eb.addInlineField("Reason", reason);
                            ctx.channel.sendMessage(eb);

                            player.con.kick(Packets.KickReason.banned);
                        } else {
                            eb.setTitle("Player `" + escapeCharacters(target) + "` not found.");
                            eb.setColor(Pals.error);
                            ctx.channel.sendMessage(eb);
                        }
                    });
                }
            });
//
            handler.registerCommand(new RoleRestrictedCommand("blacklist") {
                {
                    help = "Ban a player by the provided uuid.";
                    usage = "<uuid>";
                    role = banRole;
                    category = "moderation";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTimestampToNow();
                    String target = ctx.args[1];
                    PlayerData pd = getData(target);
                    Administration.PlayerInfo info = netServer.admins.getInfoOptional(target);

                    if (pd != null && info != null) {
                        pd.banned = true;
                        setData(target, pd);
                        eb.setTitle("Blacklisted successfully.");
                        eb.setDescription("`" + escapeCharacters(info.lastName) + "` was banned.");
                    } else {
                        eb.setTitle("Command terminated");
                        eb.setColor(Pals.error);
                        eb.setDescription("UUID `" + escapeCharacters(target) + "` was not found in the database.");
                    }
                    ctx.channel.sendMessage(eb);
                }
            });
//
//
            handler.registerCommand(new RoleRestrictedCommand("expel") {
                {
                    help = "Ban the provided player for a specific duration with a specific reason.";
                    usage = "<player> <duration (minutes)> [reason..]";
                    role = banRole;
                    category = "moderation";
                }

                public void run(Context ctx) {
                    CompletableFuture.runAsync(() -> {
                        EmbedBuilder eb = new EmbedBuilder();
                        String target = ctx.args[1];
                        String targetDuration = ctx.args[2];
                        String reason = ctx.message.substring(target.length() + targetDuration.length() + 2);
                        long now = Instant.now().getEpochSecond();

                        Player player = findPlayer(target);
                        if (player != null) {
                            String uuid = player.uuid();
                            String banId = uuid.substring(0, 4);
                            PlayerData pd = getData(uuid);
                            long until = now + Integer.parseInt(targetDuration) * 60;
                            if (pd != null) {
                                pd.bannedUntil = until;
                                pd.banReason = reason + "\n" + "[accent]Until: " + epochToString(until) + "\n[accent]Ban ID:[] " + banId;
                                setData(uuid, pd);
                            }

                            eb.setTitle("Banned `" + escapeCharacters(player.name) + "` permanently.");
                            eb.addField("UUID", uuid);
                            eb.addField("Ban ID", banId);
                            eb.addField("For", targetDuration + " minutes.");
                            eb.addField("Until", epochToString(until));
                            eb.addInlineField("Reason", reason);
                            ctx.channel.sendMessage(eb);

                            player.con.kick(Packets.KickReason.banned);
                        } else {
                            eb.setTitle("Player `" + escapeCharacters(target) + "` not found.");
                            eb.setColor(Pals.error);
                            ctx.channel.sendMessage(eb);
                        }
                    });
                }
            });
//
            handler.registerCommand(new RoleRestrictedCommand("kick") {
                {
                    help = "Kick the provided player with a specific reason.";
                    usage = "<player> [reason..]";
                    role = banRole;
                    category = "moderation";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    String reason = ctx.message.substring(target.length() + 1);

                    Player player = findPlayer(target);
                    if (player != null) {
                        String uuid = player.uuid();
                        eb.setTitle("Kicked `" + escapeCharacters(player.name) + "`.");
                        eb.addField("UUID", uuid);
                        eb.addInlineField("Reason", reason);
                        ctx.channel.sendMessage(eb);

                        player.con.kick(Packets.KickReason.kick);
                    } else {
                        eb.setTitle("Player `" + escapeCharacters(target) + "` not found.");
                        eb.setColor(Pals.error);
                        ctx.channel.sendMessage(eb);
                    }
                }
            });
//
            handler.registerCommand(new RoleRestrictedCommand("unban") {
                {
                    help = "Unban the player by the provided uuid.";
                    usage = "<uuid>";
                    role = banRole;
                    category = "moderation";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    PlayerData pd = getData(target);

                    if (pd != null) {
                        pd.banned = false;
                        pd.bannedUntil = 0;
                        Administration.PlayerInfo info = netServer.admins.getInfo(target);
                        netServer.admins.unbanPlayerID(target);
                        eb.setTitle("Unbanned `" + escapeCharacters(info.lastName) + "`.");
                        ctx.channel.sendMessage(eb);
                        setData(target, pd);
                    } else {
                        eb.setTitle("UUID `" + escapeCharacters(target) + "` not found in the database.");
                        eb.setColor(Pals.error);
                        ctx.channel.sendMessage(eb);
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("unbanip") {
                {
                    help = "Unban the player by the provided IP.";
                    usage = "<uuid>";
                    role = banRole;
                    category = "moderation";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    if (!netServer.admins.isIPBanned(target)) {
                        eb.setTitle("IP `" + escapeCharacters(target) + "` was not banned");
                        eb.setColor(Pals.error);
                    } else {
                        netServer.admins.unbanPlayerIP(target);
                        eb.setTitle("Unbanned IP `" + escapeCharacters(target) + "`");
                    }
                    ctx.channel.sendMessage(eb);
                }
            });
//
            handler.registerCommand(new RoleRestrictedCommand("unvotekick") {
                EmbedBuilder eb = new EmbedBuilder();

                {
                    help = "Unvotekickban the specified player";
                    usage = "<uuid>";
                    role = banRole;
                    category = "moderation";
                }

                public void run(Context ctx) {
                    String target = ctx.args[1];
                    Administration.PlayerInfo info = netServer.admins.getInfo(target);

                    if (info != null) {
                        info.lastKicked = 0;
                        eb.setTitle("Command executed.");
                        eb.setDescription("Unvotekickbanned `" + target + "` succeessfully.");
                    } else {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Pals.error);
                        eb.setDescription("That ID isn't votekickbanned!");
                    }
                    ctx.channel.sendMessage(eb);
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("playersinfo") {
                {
                    help = "Check the information about all players on the server.";
                    role = banRole;
                    category = "moderation";
                }

                public void run(Context ctx) {
                    StringBuilder msg = new StringBuilder("**Players online: " + Groups.player.size() + "**\n```\n");
                    for (Player player : Groups.player) {
                        msg.append("· ").append(escapeColorCodes(player.name.replaceAll(" ", "")).replaceAll("<.*?>", "").replaceAll("\\[.*?\\]", ""));
                        if (!player.admin) {
                            msg.append(" : ").append(player.con.address).append(" : ").append(player.uuid()).append("\n");
                        } else {
                            msg.append("\n");
                        }
                    }
                    msg.append("```");

                    StringBuilder lijst = new StringBuilder();
//                StringBuilder admins = new StringBuilder();

                    if (Groups.player.size() == 0) {
                        lijst.append("No players are currently in the server.");// + Vars.playerGroup.all().count(p->p.isAdmin)+"\n");
                    }
                    for (Player player : Groups.player) {
                        lijst.append("`* ").append(escapeColorCodes(player.name.replaceAll(" ", "")).replaceAll("<.*?>", "").replaceAll("\\[.*?\\]", ""));
                        if (player.admin()) {
                            lijst.append("`\n");
                        } else {
                            lijst.append(" : ").append(player.con.address).append(" : ").append(player.uuid()).append("`\n");
                        }
                    }

                    new MessageBuilder()
                            .setEmbed(new EmbedBuilder()
                                    .setTitle("Players online: " + Groups.player.size())
//                                    .setDescription( "Info about the Server: ")
                                    .setDescription(lijst.toString())
//                                .addField("Admins: ", admins+" ")
//                                .addField("Players:", lijst.toString())
                                    .setColor(Color.ORANGE))
                            .send(ctx.channel);

//                    ctx.channel.sendMessage(msg.toString());
                }
            });
//
            handler.registerCommand(new RoleRestrictedCommand("lookup") {
                {
                    help = "Check all information about the specified player.";
                    usage = "<player>";
                    role = banRole;
                    category = "moderation";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];

                    Administration.PlayerInfo info = null;
                    Player player = findPlayer(target);
                    if (player != null) {
                        info = netServer.admins.getInfo(player.uuid());
                    } else {
                        info = netServer.admins.getInfoOptional(target);
                    }

                    if (info != null) {
                        eb.setTitle(escapeCharacters(info.lastName) + "'s lookup");
                        eb.addField("UUID", info.id);
                        eb.addField("Last used ip", info.lastIP);
                        eb.addField("Times kicked", String.valueOf(info.timesKicked));

                        PlayerData pd = getData(info.id);
                        if (pd != null) {
                            eb.addField("Rank", rankNames.get(pd.rank).name);
                            eb.addField("Playtime", pd.playTime + " minutes");
                            eb.addField("Games", String.valueOf(pd.gamesPlayed));
                            eb.addField("Buildings built", String.valueOf(pd.buildingsBuilt));

                            CompletableFuture<User> user = ioMain.api.getUserById(pd.discordLink);
                            user.thenAccept(user1 -> {
                                eb.addField("Discord Link", user1.getDiscriminatedName());
                            });
                        }
                        StringBuilder s = new StringBuilder();
                        s.append("**All used names: **\n");
                        for (String name : info.names) {
                            s.append(escapeColorCodes(name.replaceAll(" ", "")).replaceAll("<.*?>", "").replaceAll("\\[.*?\\]", "")).append(" / ");
                        }
                        s.append("\n\n**All used IPs: **\n");
                        for (String ip : info.ips) {
                            s.append(escapeCharacters(ip)).append(" / ");
                        }
                        eb.setDescription(s.toString());
                        ctx.channel.sendMessage(eb);
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("syncserver") {
                {
                    help = "Tell everyone to resync.";
                    role = banRole;
                    category = "management";
                }

                public void run(Context ctx) {
                    for (Player p : Groups.player) {
                        Call.worldDataBegin(p.con);
                        netServer.sendWorldData(p);
                    }
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Command executed.")
                            .setDescription("Synchronized every player's client with the server.");
                    ctx.channel.sendMessage(eb);
                }
            });
//
            handler.registerCommand(new RoleRestrictedCommand("convert") {
                {
                    help = "Change the provided player into a specific unit.";
                    role = banRole;
                    usage = "<playerid|ip|all|name|teamid> <unit>";
                    category = "management";
                }

                public void run(Context ctx) {
                    String target = ctx.args[1];
                    String targetMech = ctx.args[2];
//                    Mech desiredMech = Mechs.alpha;
                    UnitType desiredUnit = UnitTypes.alpha;
                    if (target.length() > 0 && targetMech.length() > 0) {
                        try {
                            Field field = UnitTypes.class.getDeclaredField(targetMech);
                            desiredUnit = (UnitType) field.get(null);
                        } catch (NoSuchFieldException | IllegalAccessException ignored) {
                        }

                        EmbedBuilder eb = new EmbedBuilder();

                        if (target.equals("all")) {
                            for (Player p : Groups.player) {
                                p.unit(desiredUnit.spawn(p.team(), p.x, p.y));
                            }
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed everyone's unit into " + desiredUnit.name);
                            ctx.channel.sendMessage(eb);
                            return;
                        } else if (target.matches("[0-9]+") && target.length() == 1) {
                            for (Player p : Groups.player) {
                                if (p.team().id == Byte.parseByte(target)) {
                                    p.unit(desiredUnit.spawn(p.team(), p.x, p.y));
                                }
                            }
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed everyone's unit into " + desiredUnit.name);
                            ctx.channel.sendMessage(eb);
                            return;
                        }
                        Player player = findPlayer(target);
                        if (player != null) {
                            player.unit(desiredUnit.spawn(player.team(), player.x, player.y));
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed " + escapeColorCodes(player.name.replaceAll(" ", "")).replaceAll("<.*?>", "").replaceAll("\\[.*?\\]", "") + "s unit into " + desiredUnit.name);
                            ctx.channel.sendMessage(eb);
                        }
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("changeteam") {
                {
                    help = "Change the provided player's team into the provided one.";
                    role = banRole;
                    usage = "<playerid|ip|all|name|teamid> <team>";
                    category = "management";
                }

                public void run(Context ctx) {
                    String target = ctx.args[1];
                    String targetTeam = ctx.args[2];
                    Team desiredTeam = Team.crux;


                    if (target.length() > 0 && targetTeam.length() > 0) {
                        try {
                            Field field = Team.class.getDeclaredField(targetTeam);
                            desiredTeam = (Team) field.get(null);
                        } catch (NoSuchFieldException | IllegalAccessException ignored) {
                        }

                        EmbedBuilder eb = new EmbedBuilder();
                        if (target.equals("all")) {
                            for (Player p : Groups.player) {
                                p.team(desiredTeam);
//                                p.spawner = getCore(p.getTeam());
                            }
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed everyone's team to " + desiredTeam.name);
                            ctx.channel.sendMessage(eb);
                            return;
                        } else if (target.matches("[0-9]+") && target.length() == 1) {
                            for (Player p : Groups.player) {
                                if (p.team().id == Byte.parseByte(target)) {
                                    p.team(desiredTeam);
//                                    p.spawner = getCore(p.getTeam());
                                }
                            }
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed everyone's team to " + desiredTeam.name);
                            ctx.channel.sendMessage(eb);
                            return;
                        }
                        Player player = findPlayer(target);
                        if (player != null) {
                            player.team(desiredTeam);
//                            player.spawner = getCore(player.getTeam());
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed " + escapeColorCodes(player.name.replaceAll(" ", "")).replaceAll("<.*?>", "").replaceAll("\\[.*?\\]", "") + "s team to " + desiredTeam.name);
                            ctx.channel.sendMessage(eb);
                        }
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("changeteamid") {
                {
                    help = "Change the provided player's team into a generated int.";
                    role = banRole;
                    usage = "<playerid|ip|all|name> <team>";
                    category = "management";
                }

                public void run(Context ctx) {
                    String target = ctx.args[1];
                    int targetTeam = Integer.parseInt(ctx.args[2]);
                    if (target.length() > 0 && targetTeam > 0) {
                        EmbedBuilder eb = new EmbedBuilder();

                        if (target.equals("all")) {
                            for (Player p : Groups.player) {
                                p.team(Team.get(targetTeam));
//                                p.spawner = getCore(p.getTeam());
                            }
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed everyone's team to " + targetTeam);
                            ctx.channel.sendMessage(eb);
                            return;
                        } else if (target.matches("[0-9]+") && target.length() == 1) {
                            for (Player p : Groups.player) {
                                if (p.team().id == Byte.parseByte(target)) {
                                    p.team(Team.get(targetTeam));
//                                    p.spawner = getCore(p.getTeam());
                                }
                            }
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed everyone's team to " + targetTeam);
                            ctx.channel.sendMessage(eb);
                            return;
                        }
                        Player player = findPlayer(target);
                        if (player != null) {
                            player.team(Team.get(targetTeam));
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed " + escapeCharacters(player.name) + "s team to " + targetTeam);
                            ctx.channel.sendMessage(eb);
                        }
                    }
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("rename") {
                {
                    help = "Rename the provided player";
                    role = banRole;
                    usage = "<playerid|ip|name> <name>";
                    category = "management";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    String name = ctx.message.substring(target.length() + 1);
                    if (target.length() > 0 && name.length() > 0) {
                        Player player = findPlayer(target);
                        if (player != null) {
                            player.name = name;
//                            PersistentPlayerData tdata = ioMain.playerDataGroup.get(player.uuid);
//                            if (tdata != null) tdata.origName = name;
                            eb.setTitle("Command executed successfully");
                            eb.setDescription("Changed name to " + escapeCharacters(player.name));
                            ctx.channel.sendMessage(eb);
                            Call.infoMessage(player.con, "[scarlet]Your name was changed by a moderator.");
                        }
                    }
                }

            });

            handler.registerCommand(new RoleRestrictedCommand("motd") {
                {
                    help = "Change / set a welcome message";
                    role = banRole;
                    usage = "<newmessage>";
                    category = "management";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("Command executed successfully");
                    String message = ctx.message;
                    if (message.length() > 0 && !message.equals("disable")) {
                        welcomeMessage = message;
                        Core.settings.put("welcomeMessage", message);
                        Core.settings.autosave();
                        eb.setDescription("Changed welcome message.");
                        ctx.channel.sendMessage(eb);
                    } else {
                        eb.setDescription("Disabled welcome message.");
                        ctx.channel.sendMessage(eb);
                    }
                }

            });

            handler.registerCommand(new RoleRestrictedCommand("screenmessage") {
                {
                    help = "List, remove or add on-screen messages.";
                    role = banRole;
                    usage = "<list/remove/add> <message>";
                    category = "moderation";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1].toLowerCase();
                    String message = "";
                    if (!target.equals("list")) {
                        message = ctx.message.split(" ", 2)[1];
                    }

                    switch (target) {
                        case "list" -> {
                            eb.setTitle("All on-screen messages:");
                            for (String msg : onScreenMessages) {
                                eb.addField(String.valueOf(onScreenMessages.indexOf(msg)), msg);
                            }
                            ctx.channel.sendMessage(eb);
                        }
                        case "remove" -> {
                            if (onScreenMessages.get(Integer.parseInt(message.trim())) != null) {
                                onScreenMessages.remove(Integer.parseInt(message.trim()));
                                eb.setTitle("Command executed");
                                eb.setDescription("Removed provided on-screen message.");
                            } else {
                                eb.setTitle("Command terminated");
                                eb.setDescription("That on-screen message does not exist.");
                                eb.setColor(Pals.error);
                            }
                            ctx.channel.sendMessage(eb);
                        }
                        case "add" -> {
                            if (message.length() > 0) {
                                onScreenMessages.add(message);
                                eb.setTitle("Command executed");
                                eb.setDescription("Added on-screen message `" + message + "`.");
                            } else {
                                eb.setTitle("Command terminated");
                                eb.setDescription("On-screen messages must be longer than 0 characters.");
                                eb.setColor(Pals.error);
                            }
                            ctx.channel.sendMessage(eb);
                        }
                        default -> {
                            eb.setTitle("Command terminated");
                            eb.setDescription("Invalid arguments provided.");
                            eb.setColor(Pals.error);
                            ctx.channel.sendMessage(eb);
                        }
                    }
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("edit") {
                {
                    help = "Change / set a message";
                    role = banRole;
                    usage = "<stats|rank|req|rule> <newmessage>";
                    category = "management";
                }

                public void run(Context ctx) {
                    String target = ctx.args[1].toLowerCase();
                    switch (target) {
                        case "stats" -> {
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle("Command executed successfully");
                            String message = ctx.message.split(" ", 2)[1];
//                        message = message.split(" ", 2)[1];
                            if (message.length() > 0) {
                                System.out.println("new stat message: " + message);
                                statMessage = message;
                                Core.settings.put("statMessage", message);
                                Core.settings.autosave();
                                eb.setDescription("Changed stat message.");
                            } else {
                                eb.setTitle("Command terminated");
                                eb.setDescription("No message provided.");
                            }
                            ctx.channel.sendMessage(eb);
                        }
                        case "req" -> {
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle("Command executed successfully");
                            String message = ctx.message.split(" ", 2)[1];
                            if (message.length() > 0) {
                                reqMessage = message;
                                Core.settings.put("reqMessage", message);
                                Core.settings.autosave();
                                eb.setDescription("Changed reqMessage.");
                            } else {
                                eb.setTitle("Command terminated");
                                eb.setDescription("No message provided.");
                            }
                            ctx.channel.sendMessage(eb);
                        }
                        case "rank" -> {
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle("Command executed successfully");
                            String message = ctx.message.split(" ", 2)[1];
                            if (message.length() > 0) {
                                rankMessage = message;
                                Core.settings.put("rankMessage", message);
                                Core.settings.autosave();
                                eb.setDescription("Changed rankMessage.");
                            } else {
                                eb.setTitle("Command terminated");
                                eb.setDescription("No message provided.");
                            }
                            ctx.channel.sendMessage(eb);
                        }
                        case "rule" -> {
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle("Command executed successfully");
                            String message = ctx.message.split(" ", 2)[1];
                            if (message != null) {
                                ruleMessage = message;
                                Core.settings.put("ruleMessage", message);
                                Core.settings.autosave();
                                eb.setDescription("Changed rules.");
                                eb.setColor(Pals.success);
                            } else {
                                eb.setTitle("Command terminated");
                                eb.setDescription("No message provided.");
                                eb.setColor(Pals.error);
                            }
                            ctx.channel.sendMessage(eb);
                        }
                        default -> {
                            EmbedBuilder eb = new EmbedBuilder();
                            eb.setTitle("Please select a message to edit!");
                            eb.setColor(Pals.error);
                            ctx.channel.sendMessage(eb);
                        }
                    }
                }
            });

//            handler.registerCommand(new RoleRestrictedCommand("statmessage") {
//                {
//                    help = "Change / set a stat message";
//                    role = banRole;
//                    usage = "<newmessage>";
//                    category = "management";
//                }
//
//                public void run(Context ctx) {
//                    EmbedBuilder eb = new EmbedBuilder();
//                    eb.setTitle("Command executed successfully");
//                    String message = ctx.message;
//                    if (message.length() > 0) {
//                        statMessage = message;
//                        Core.settings.put("statMessage", message);
//                        Core.settings.autosave();
//                        eb.setDescription("Changed stat message.");
//                    } else {
//                        eb.setTitle("Command terminated");
//                        eb.setDescription("No message provided.");
//                    }
//                    ctx.channel.sendMessage(eb);
//                }
//
//            });

//            handler.registerCommand(new RoleRestrictedCommand("reqMessage") {
//                {
//                    help = "Change / set a requirement Message";
//                    role = banRole;
//                    usage = "<newmessage>";
//                    category = "management";
//                }
//
//                public void run(Context ctx) {
//                    EmbedBuilder eb = new EmbedBuilder();
//                    eb.setTitle("Command executed successfully");
//                    String message = ctx.message;
//                    if (message.length() > 0) {
//                        reqMessage = message;
//                        Core.settings.put("reqMessage", message);
//                        Core.settings.autosave();
//                        eb.setDescription("Changed reqMessage.");
//                    } else {
//                        eb.setTitle("Command terminated");
//                        eb.setDescription("No message provided.");
//                    }
//                    ctx.channel.sendMessage(eb);
//                }
//
//            });
//
//            handler.registerCommand(new RoleRestrictedCommand("rankMessage") {
//                {
//                    help = "Change / set a rank Message";
//                    role = banRole;
//                    usage = "<newmessage>";
//                    category = "management";
//                }
//
//                public void run(Context ctx) {
//                    EmbedBuilder eb = new EmbedBuilder();
//                    eb.setTitle("Command executed successfully");
//                    String message = ctx.message;
//                    if (message.length() > 0) {
//                        rankMessage = message;
//                        Core.settings.put("rankMessage", message);
//                        Core.settings.autosave();
//                        eb.setDescription("Changed rankMessage.");
//                    } else {
//                        eb.setTitle("Command terminated");
//                        eb.setDescription("No message provided.");
//                    }
//                    ctx.channel.sendMessage(eb);
//                }
//
//            });

//            handler.registerCommand(new RoleRestrictedCommand("rulemessage") {
//                {
//                    help = "Change server rules. Use approriate prefix";
//                    role = banRole;
//                    category = "management";
//                }
//
//                public void run(Context ctx) {
//                    EmbedBuilder eb = new EmbedBuilder();
//                    eb.setTitle("Command executed successfully");
//                    String message = ctx.message;
//                    if (message != null) {
//                        ruleMessage = message;
//                        Core.settings.put("ruleMessage", message);
//                        Core.settings.autosave();
//                        eb.setDescription("Changed rules.");
//                        eb.setColor(Pals.success);
//                    } else {
//                        eb.setTitle("Command terminated");
//                        eb.setDescription("No message provided.");
//                        eb.setColor(Pals.error);
//                    }
//                    ctx.channel.sendMessage(eb);
//                }
//
//            });


            handler.registerCommand(new RoleRestrictedCommand("spawn") {
                {
                    help = "Spawn x units at the location of the specified player";
                    role = banRole;
                    category = "management";
                    usage = "<playerid|ip|name> <unit> <amount>";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    String targetUnit = ctx.args[2];
                    int amount = Integer.parseInt(ctx.args[3]);
                    UnitType desiredUnit = UnitTypes.dagger;
                    if (target.length() > 0 && targetUnit.length() > 0 && amount > 0 && amount < 1000) {
                        try {
                            Field field = UnitTypes.class.getDeclaredField(targetUnit);
                            desiredUnit = (UnitType) field.get(null);
                        } catch (NoSuchFieldException | IllegalAccessException ignored) {
                        }

                        Player player = findPlayer(target);
                        if (player != null) {
                            UnitType finalDesiredUnit = desiredUnit;
                            IntStream.range(0, amount).forEach(i -> {
                                Unit unit = finalDesiredUnit.create(player.team());
                                unit.set(player.getX(), player.getY());
                                unit.add();
                            });
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Spawned " + amount + " " + targetUnit + " near " + Utils.escapeCharacters(player.name) + ".");
                            ctx.channel.sendMessage(eb);
                        }
                    } else {
                        eb.setTitle("Command terminated");
                        eb.setDescription("Invalid arguments provided.");
                        eb.setColor(Pals.error);
                        ctx.channel.sendMessage(eb);
                    }
                }
            });

//            handler.registerCommand(new RoleRestrictedCommand("killunits") {
//                {
//                    help = "<playerid|ip|name> <unit> Kills all units of the team of the specified player";
//                    role = banRole;
//                    category = "management";
//                }
//                public void run(Context ctx) {
//                    EmbedBuilder eb = new EmbedBuilder();
//                    String target = ctx.args[1];
//                    String targetUnit = ctx.args[2];
//                    UnitType desiredUnit = UnitTypes.dagger;
//                    if(target.length() > 0 && targetUnit.length() > 0) {
//                        try {
//                            Field field = UnitTypes.class.getDeclaredField(targetUnit);
//                            desiredUnit = (UnitType)field.get(null);
//                        } catch (NoSuchFieldException | IllegalAccessException ignored) {}
//
//                        Player player = findPlayer(target);
//                        if(player!=null){
//                            int amount = 0;
//                            for(Unit unit : ) {
//                                if(unit.team == player.team()){
//                                    if(unit.type == desiredUnit) {
//                                        unit.kill();
//                                        amount++;
//                                    }
//                                }
//                            }
//                            eb.setTitle("Command executed successfully.");
//                            eb.setDescription("Killed " + amount + " " + targetUnit + "s on team " + player.team());
//                            ctx.channel.sendMessage(eb);
//                        }
//                    } else{
//                        eb.setTitle("Command terminated");
//                        eb.setDescription("Invalid arguments provided.");
//                        eb.setColor(Pals.error);
//                        ctx.channel.sendMessage(eb);
//                    }
//                }
//            });

            handler.registerCommand(new RoleRestrictedCommand("setblock") {
                {
                    help = "Create a block at the player's current location and on the player's current team.";
                    role = banRole;
                    usage = "<playerid|ip|name> <block>";
                    category = "management";
                }

                public void run(Context ctx) {
                    String target = ctx.args[1];
                    String targetBlock = ctx.args[2];
                    Block desiredBlock = Blocks.copperWall;

                    try {
                        Field field = Blocks.class.getDeclaredField(targetBlock);
                        desiredBlock = (Block) field.get(null);
                    } catch (NoSuchFieldException | IllegalAccessException ignored) {
                    }

                    EmbedBuilder eb = new EmbedBuilder();
                    Player player = findPlayer(target);

                    if (player != null) {
                        float x = player.getX();
                        float y = player.getY();
                        Tile tile = world.tileWorld(x, y);
                        tile.setNet(desiredBlock, player.team(), 0);

                        eb.setTitle("Command executed successfully.");
                        eb.setDescription("Spawned " + desiredBlock.name + " on " + Utils.escapeCharacters(player.name) + "'s position.");
                        ctx.channel.sendMessage(eb);
                    } else {
                        eb.setTitle("Command terminated");
                        eb.setDescription("Invalid arguments provided.");
                        eb.setColor(Pals.error);
                        ctx.channel.sendMessage(eb);
                    }
                }
            });

//            handler.registerCommand(new RoleRestrictedCommand("weaponmod") { // OH NO
//                {
//                    help = "<playerid|ip|name|all(oh no)> <bullet-type> <lifetime-modifier> <velocity-modifier> Mod the current weapon of a player.";
//                    role = banRole;
//                }
//                public void run(Context ctx) {
//                    EmbedBuilder eb = new EmbedBuilder();
//
//                    String target = ctx.args[1];
//                    String targetBullet = ctx.args[2];
//                    float targetL = Float.parseFloat(ctx.args[3]);
//                    float targetV = Float.parseFloat(ctx.args[4]);
//                    BulletType desiredBullet = null;
//
//                    if(target.length() > 0 && targetBullet.length() > 0) {
//                        try {
//                            Field field = Bullets.class.getDeclaredField(targetBullet);
//                            desiredBullet = (BulletType)field.get(null);
//                        } catch (NoSuchFieldException | IllegalAccessException ignored) {}
//
//                        if(target.equals("all")){
//                            for(Player p : Groups.player){
//                                if(player!=null) { // what???    ...    how does this happen
//                                    try {
//                                        if (desiredBullet == null) {
//                                            player.unit().wea
//                                            player.bt = null;
//                                        } else {
//                                            player.bt = desiredBullet;
//                                            player.sclLifetime = targetL;
//                                            player.sclVelocity = targetV;
//                                        }
//                                    } catch(Exception ignored){}
//                                }
//                            }
//                            eb.setTitle("Command executed");
//                            eb.setDescription("Changed everyone's weapon mod. sorry. i dont know how to explain the rest");
//                            ctx.channel.sendMessage(eb);
//                        }
//
//                        Player player = findPlayer(target);
//                        if(player!=null){
//                            if(desiredBullet == null){
//                                player.bt = null;
//                                eb.setTitle("Command executed");
//                                eb.setDescription("Reverted " + escapeCharacters(player.name) + "'s weapon to default.");
//                                ctx.channel.sendMessage(eb);
//                            } else{
//                                player.bt = desiredBullet;
//                                player.sclLifetime = targetL;
//                                player.sclVelocity = targetV;
//                                eb.setTitle("Command executed");
//                                eb.setDescription("Modded " + escapeCharacters(player.name) + "'s weapon to " + targetBullet + " with " + targetL + "x lifetime modifier and " + targetV + "x velocity modifier.");
//                                ctx.channel.sendMessage(eb);
//                            }
//                        }
//                    } else{
//                        eb.setTitle("Command terminated");
//                        eb.setDescription("Invalid arguments provided.");
//                        eb.setColor(Pals.error);
//                        ctx.channel.sendMessage(eb);
//                    }
//                }
//            });


            handler.registerCommand(new RoleRestrictedCommand("setrank") {
                {
                    help = "Change the player's rank to the provided one.";
                    usage = "<playerid|ip|name> <rank>";
                    role = banRole;
                    category = "management";
                }

                public void run(Context ctx) {
                    CompletableFuture.runAsync(() -> {
                        EmbedBuilder eb = new EmbedBuilder();
                        String target = ctx.args[1];
                        int targetRank = Integer.parseInt(ctx.args[2]);
                        if (target.length() > 0 && targetRank > -1 && targetRank < 6) {
                            Player player = findPlayer(target);
                            if (player == null) {
                                eb.setTitle("Command terminated");
                                eb.setDescription("Player not found.");
                                eb.setColor(Pals.error);
                                ctx.channel.sendMessage(eb);
                                return;
                            }

                            PlayerData pd = getData(player.uuid());
                            if (pd != null) {
                                pd.rank = targetRank;
                                setData(player.uuid(), pd);
                                eb.setTitle("Command executed successfully");
                                eb.setDescription("Promoted " + escapeColorCodes(player.name.replaceAll(" ", "")).replaceAll("<.*?>", "").replaceAll("\\[.*?\\]", "") + " to " + targetRank);
                                ctx.channel.sendMessage(eb);
                                player.con.kick("Your rank was modified, please rejoin.", 0);
                            }

                            if (targetRank == 5) netServer.admins.adminPlayer(player.uuid(), player.usid());
                        }
                    });
                }

            });
            handler.registerCommand(new RoleRestrictedCommand("setstats") {
                {
                    help = "Change the player's statistics to the provided one.";
                    usage = "<playerid|ip|name> <rank> <playTime> <buildingsBuilt> <gamesPlayed>";
                    role = banRole;
                    category = "management";
                }

                public void run(Context ctx) {
                    CompletableFuture.runAsync(() -> {
                        EmbedBuilder eb = new EmbedBuilder();
                        String target = ctx.args[1];
                        int targetRank = Integer.parseInt(ctx.args[2]);
                        int playTime = Integer.parseInt(ctx.args[3]);
                        int buildingsBuilt = Integer.parseInt(ctx.args[4]);
                        int gamesPlayed = Integer.parseInt(ctx.args[5]);
                        if (target.length() > 0 && targetRank > -1 && targetRank < 6) {
                            Player player = findPlayer(target);
                            if (player == null) {
                                eb.setTitle("Command terminated");
                                eb.setDescription("Player not found.");
                                eb.setColor(Pals.error);
                                ctx.channel.sendMessage(eb);
                                return;
                            }

                            PlayerData pd = getData(player.uuid());
                            if (pd != null) {
                                pd.buildingsBuilt = buildingsBuilt;
                                pd.gamesPlayed = gamesPlayed;
                                pd.playTime = playTime;
                                pd.rank = targetRank;
                                setData(player.uuid(), pd);
                                eb.setTitle("Command executed successfully");
                                eb.setDescription(String.format("Set stats of %s to:\nPlaytime: %d\nBuildings built: %d\nGames played: %d", escapeColorCodes(player.name.replaceAll(" ", "")).replaceAll("<.*?>", "").replaceAll("\\[.*?\\]", ""), playTime, buildingsBuilt, gamesPlayed));
//                                eb.setDescription("Promoted " + escapeCharacters(player.name) + " to " + targetRank);
                                ctx.channel.sendMessage(eb);
//                                player.con.kick("Your rank was modified, please rejoin.", 0);
                            }

                            if (targetRank == 5) netServer.admins.adminPlayer(player.uuid(), player.usid());
                        }
                    });
                }

            });

        }

            /*handler.registerCommand(new Command("sendm"){ // use sendm to send embed messages when needed locally, disable for now
                public void run(Context ctx){
                    EmbedBuilder eb = new EmbedBuilder()
                            .setColor(Utils.Pals.info)
                            .setTitle("Support mindustry.io by donating, and receive custom ranks!")
                            .setUrl("https://donate.mindustry.io/")
                            .setDescription("By donating, you directly help me pay for the monthly server bills I receive for hosting 4 servers with **150+** concurrent players daily.")
                            .addField("VIP", "**VIP** is obtainable through __nitro boosting__ the server or __donating $1.59+__ to the server.", false)
                            .addField("__**MVP**__", "**MVP** is a more enchanced **vip** rank, obtainable only through __donating $3.39+__ to the server.", false)
                            .addField("Where do I get it?", "You can purchase **vip** & **mvp** ranks here: https://donate.mindustry.io", false)
                            .addField("\uD83E\uDD14 io is pay2win???", "Nope. All perks vips & mvp's gain are aesthetic items **or** items that indirectly help the team. Powerful commands that could give you an advantage are __disabled on pvp.__", true);
                    ctx.channel.sendMessage(eb);
                }
            });*/


        if (data.has("mapSubmissions_roleid")) {
            String reviewerRole = data.getString("mapSubmissions_roleid");
            handler.registerCommand(new RoleRestrictedCommand("uploadmap") {
                {
                    help = "Upload a new map (Include a .msav file with command message)";
                    role = reviewerRole;
                    usage = "<.msav attachment>";
                    category = "mapReviewer";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    Seq<MessageAttachment> ml = new Seq<>();
                    for (MessageAttachment ma : ctx.event.getMessageAttachments()) {
                        if (ma.getFileName().split("\\.", 2)[1].trim().equals("msav")) {
                            ml.add(ma);
                        }
                    }
                    if (ml.size != 1) {
                        eb.setTitle("Map upload terminated.");
                        eb.setColor(Pals.error);
                        eb.setDescription("You need to add one valid .msav file!");
                        ctx.channel.sendMessage(eb);
                        return;
                    } else if (Core.settings.getDataDirectory().child("maps").child(ml.get(0).getFileName()).exists()) {
                        eb.setTitle("Map upload terminated.");
                        eb.setColor(Pals.error);
                        eb.setDescription("There is already a map with this name on the server!");
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    // more custom filename checks possible

                    CompletableFuture<byte[]> cf = ml.get(0).downloadAsByteArray();
                    Fi fh = Core.settings.getDataDirectory().child("maps").child(ml.get(0).getFileName());

                    try {
                        byte[] data = cf.get();
                        if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
                            eb.setTitle("Map upload terminated.");
                            eb.setColor(Pals.error);
                            eb.setDescription("Map file corrupted or invalid.");
                            ctx.channel.sendMessage(eb);
                            return;
                        }
                        fh.writeBytes(cf.get(), false);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    maps.reload();
                    eb.setTitle("Map upload completed.");
                    eb.setDescription(ml.get(0).getFileName() + " was added succesfully into the playlist!");
                    ctx.channel.sendMessage(eb);
                    //Utils.LogAction("uploadmap", "Uploaded a new map", ctx.author, null);
                }
            });

            handler.registerCommand(new RoleRestrictedCommand("removemap") {
                {
                    help = "Remove a map from the playlist (use mapname/mapid retrieved from the %maps command)".replace("%", ioMain.prefix);
                    role = reviewerRole;
                    usage = "<mapname/mapid>";
                    category = "mapReviewer";
                }

                @Override
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    if (ctx.args.length < 2) {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Pals.error);
                        eb.setDescription("Not enough arguments, use `%removemap <mapname/mapid>`".replace("%", ioMain.prefix));
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    Map found = getMapBySelector(ctx.message.trim());
                    if (found == null) {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Pals.error);
                        eb.setDescription("Map not found");
                        ctx.channel.sendMessage(eb);
                        return;
                    }

                    maps.removeMap(found);
                    maps.reload();

                    eb.setTitle("Command executed.");
                    eb.setDescription(found.name() + " was successfully removed from the playlist.");
                    ctx.channel.sendMessage(eb);
                }
            });
        }

        if (data.has("mapSubmissions_id")) {
            TextChannel tc = ioMain.getTextChannel(ioMain.data.getString("mapSubmissions_id"));
            handler.registerCommand(new Command("submitmap") {
                {
                    help = " Submit a new map to be added into the server playlist in a .msav file format.";
                    usage = "<.msav attachment>";
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    Seq<MessageAttachment> ml = new Seq<>();
                    for (MessageAttachment ma : ctx.event.getMessageAttachments()) {
                        if (ma.getFileName().split("\\.", 2)[1].trim().equals("msav")) {
                            ml.add(ma);
                        }
                    }
                    if (ml.size != 1) {
                        eb.setTitle("Map upload terminated.");
                        eb.setColor(Pals.error);
                        eb.setDescription("You need to add one valid .msav file!");
                        ctx.channel.sendMessage(eb);
                        return;
                    } else if (Core.settings.getDataDirectory().child("maps").child(ml.get(0).getFileName()).exists()) {
                        eb.setTitle("Map upload terminated.");
                        eb.setColor(Pals.error);
                        eb.setDescription("There is already a map with this name on the server!");
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    CompletableFuture<byte[]> cf = ml.get(0).downloadAsByteArray();

                    try {
                        byte[] data = cf.get();
                        if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
                            eb.setTitle("Map upload terminated.");
                            eb.setColor(Pals.error);
                            eb.setDescription("Map file corrupted or invalid.");
                            ctx.channel.sendMessage(eb);
                            return;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    eb.setTitle("Map upload completed.");
                    eb.setDescription(ml.get(0).getFileName() + " was successfully queued for review by moderators!");
                    ctx.channel.sendMessage(eb);
                    EmbedBuilder eb2 = new EmbedBuilder()
                            .setTitle("A map submission has been made for " + ioMain.serverName)
                            .setAuthor(ctx.author)
                            .setTimestampToNow()
                            .addField("Name", ml.get(0).getFileName())
                            .addField("URL", String.valueOf(ml.get(0).getUrl()));

                    try {
//                        byte[] data = cf.get();
//                        CompletableFuture<byte[]> data = ml.get(0).downloadAsByteArray();
                        InputStream data = ml.get(0).downloadAsInputStream();
//                        data.exceptionally(error -> { // handle possible errors
//                            error.printStackTrace();
//                            return null;
//                        });
                        File outFile = new File("./temp/upload.msav");
//                        Files.write(Paths.get("./temp/upload.msav"), data);
//                        DataOutputStream dout = new DataOutputStream(outFile);
//                        dout.write(data);
                        java.nio.file.Files.copy(
                                data,
                                outFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);

//                        IOUtils.closeQuietly(data);
                        List<String> mapData = map.getMap(new Fi("./temp/upload.msav"));
                        System.out.println(mapData.get(0));

                        EmbedBuilder embed = new EmbedBuilder()
                                .setTitle(escapeCharacters(mapData.get(6)))
                                .setDescription(escapeCharacters(mapData.get(5)))
//                                .setAuthor(escapeCharacters(mapData.get(1)))
                                .setAuthor(ctx.author.getName(), ctx.author.getAvatar().getUrl().toString(), ctx.author.getAvatar().getUrl().toString())
                                .setImage("attachment://" + mapData.get(6) + ".png");
                        MessageBuilder mb = new MessageBuilder();
                        mb.addEmbed(embed);
//                        mb.addFile(new File(mapData.get(0)));
                        InputStream PNG = new FileInputStream(mapData.get(0));
                        mb.addFile(PNG, mapData.get(6) + ".png");
//                        mb.addFile(new File("./temp/upload.msav"));
                        InputStream initialStream = new FileInputStream(
                                new File("./temp/upload.msav"));
                        mb.addFile(initialStream, mapData.get(6) + ".msav");
                        mb.send(tc);
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                    assert tc != null;
//                    tc.sendMessage(eb2);
                }
            });
        }
    }
}