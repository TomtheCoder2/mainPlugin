package mindustry.plugin.minimods;

import arc.Core;
import arc.Events;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Strings;
import arc.util.Structs;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.Packets;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordLog;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.*;
import mindustry.world.Tile;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;


/**
 * Manages mutes, freezes, bans, and other moderation-related commands
 */
public class Moderation implements MiniMod {
    private final ObjectSet<String> frozen = new ObjectSet<>();
    private final ObjectSet<String> muted = new ObjectSet<>();

    @Override
    public void registerEvents() {
        Events.on(EventType.ServerLoadEvent.class, event -> {
            Vars.netServer.admins.addChatFilter((player, message) -> {
                assert player != null;
                if (muted.contains(player.uuid())) {
                    return null;
                }
                return message;
            });
            Vars.netServer.admins.addActionFilter(action -> {
                assert action.player != null;
                boolean isFrozen = frozen.contains(action.player.uuid());
                if (isFrozen) {
                    action.player.sendMessage("[cyan]You are frozen! Ask a mod to unfreeze you.");
                }
                return !isFrozen;
            });
        });
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("mute", "<player> [reason...]",
                data -> {
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD, Roles.APPRENTICE};
                    data.help = "Mute or unmute a player";
                    data.category = "Moderation";
                },
                ctx -> {
                    String target = ctx.args.get("player");
                    Player player = Utils.findPlayer(target);
                    if (player == null) {
                        ctx.reply("Player " + target + " not online.");
                        return;
                    }

                    if (!muted.contains(player.uuid())) {
                        muted.add(player.uuid());
                    } else {
                        muted.remove(player.uuid());
                    }

                    boolean isMuted = muted.contains(player.uuid());
                    ctx.reply("Successfully " + (isMuted ? "muted" : "unmuted") + " " + Utils.escapeEverything(target));
                    DiscordLog.moderation(isMuted ? "Muted" : "Unmuted", ctx.author(), Vars.netServer.admins.getInfo(player.uuid()), ctx.args.get("reason"), null);
                    Call.infoMessage(player.con, "[cyan]You got " + (isMuted ? "muted" : "unmuted") + " by a moderator.\n" +
                            "[lightgray]" + (ctx.args.containsKey("reason") ? "Reason: [accent]" + ctx.args.get("reason") : ""));

                }
        );

        // TODO: this should be in communication
        handler.register("banword", "[add/remove] [word]",
                data -> {
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD};
                    data.help = "Ban a bad word.";
                    data.category = "Moderation";
                },
                ctx -> {
                    ArrayList<String> bannedWords = (ArrayList<String>) Core.settings.get("bannedWords", ArrayList.class);
                    if (ctx.args.size > 2) {
                        switch (ctx.args.get("add/remove")) {
                            // TODO: maybe make database idk
                        }
                    }
                }
        );

        handler.register("freeze", "<player> [reason...]",
                data -> {
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD, Roles.APPRENTICE};
                    data.help = "Freeze or thaw a player.";
                    data.category = "Moderation";
                },
                ctx -> {
                    String target = ctx.args.get("player");
                    Player player = Utils.findPlayer(target);
                    if (player == null) {
                        ctx.reply("Player " + target + " not found.");
                        return;
                    }

                    if (!frozen.contains(player.uuid())) {
                        frozen.add(player.uuid());
                    } else {
                        frozen.remove(player.uuid());
                    }

                    boolean isFrozen = frozen.contains(player.uuid());
                    ctx.reply("Successfully " + (isFrozen ? "frozen" : "thawed") + " " + Utils.escapeEverything(target));
                    DiscordLog.moderation(isFrozen ? "Froze" : "Unfroze", ctx.author(), Vars.netServer.admins.getInfo(player.uuid()), ctx.args.get("reason"), null);
                    Call.infoMessage(player.con, "[cyan]You got " + (isFrozen ? "frozen" : "thawed") + " by a moderator.\n" +
                            "[lightgray]" + (ctx.args.containsKey("reason") ? "Reason: [accent]" + ctx.args.get("reason") : ""));

                }
        );

        handler.register("ban", "<player> [duration:minutes] [reason...]",
                data -> {
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD, Roles.APPRENTICE};
                    data.help = "Ban a player";
                    data.category = "Moderation";
                    data.aliases = new String[]{"b", "banish"};
                },
                ctx -> {
                    Administration.PlayerInfo info = Utils.getPlayerInfo(ctx.args.get("player"));
                    if (info == null) {
                        ctx.error("Error", "Player " + ctx.args.get("player") + " not found.");
                        return;
                    }
                    String uuid = info.id;
                    String banID = uuid.substring(0, 4);
                    String reason = ctx.args.get("reason", "");

                    Database.Player pd = Database.getPlayerData(uuid);
                    if (pd == null) {
                        pd = new Database.Player(uuid, 0);
                    }
                    long duration;
                    if (ctx.args.get("duration:minutes") != null && ctx.args.get("duration:minutes").equals("forever")) {
                        duration = 0;
                        pd.banned = true;                        
                    } else {
                        duration = ctx.args.getLong("duration:minutes", 2 * 365 * 24 * 60) * 60;
                        pd.bannedUntil = Instant.now().getEpochSecond() + duration;
                    }
                    pd.banReason = reason + "\n\n[accent]Until: " + Utils.epochToString(pd.bannedUntil) + " [accent]Ban ID: " + banID;
                    Database.setPlayerData(pd);

                    ctx.sendEmbed(new EmbedBuilder()
                            .setTitle("Banned " + info.lastName)
                            .addField("Duration", duration == 0 ? "forever" :  duration / 60 + " minutes")
                            .addField("Until", Utils.epochToString(pd.bannedUntil))
                            .addField("Reason", reason)
                            .setFooter("Ban ID: " + banID)
                    );

                    Player player = Groups.player.find(p -> p.uuid() == uuid);
                    if (player != null) {
                        player.con.kick(Packets.KickReason.banned);
                    }

                    DiscordLog.moderation("Banned", ctx.author(), Vars.netServer.admins.getInfo(player.uuid()), reason, null);
                }
        );

        handler.register("unban", "<player>",
                data -> {
                    data.help = "Unban a player";
                    data.category = "Moderation";
                    data.roles = new long [] { Roles.ADMIN, Roles.MOD, Roles.APPRENTICE };
                },
                ctx -> {
                    Player p = Utils.findPlayer(ctx.args.get("player"));
                    String uuid = ctx.args.get("player");
                    if (p != null) {
                        uuid = p.uuid();
                    }

                    var pd = Database.getPlayerData(uuid);
                    if (pd == null) {
                        ctx.error("Player not found", ctx.args.get("player") + " was not found");
                        return;
                    }

                    pd.banned = false;
                    String banReason = pd.banReason;
                    pd.banReason = "";
                    pd.bannedUntil = 0;
                    Administration.PlayerInfo info = Vars.netServer.admins.getInfo(uuid);
                    Vars.netServer.admins.unbanPlayerID(uuid);

                    Database.setPlayerData(pd);
                    DiscordLog.moderation("Unban", ctx.author(), info, null, "Previous ban reason: " + banReason);
                    ctx.success("Unbanned " + Utils.escapeEverything(info.lastName), "Previous ban reason: " + banReason);
                }
        );

        handler.register("banip", "<ip> [reason...]", 
                data -> {
                    data.help = "Ban an IP, and ban any players with that IP by UUID";
                    data.category = "Moderation";
                    data.roles = new long[] { Roles.ADMIN, Roles.MOD };
                },
                ctx -> {
                    String ip = ctx.args.get("ip");
                    Vars.netServer.admins.banPlayerIP(ip);
                    ctx.success("Banned IP", "Banned " + ip);
                    DiscordLog.moderation("Ban IP", ctx.author(), null, ctx.args.get("reason"), "IP: " + ip);
                }
        );

        handler.register("unbanip", "<ip> [reason...]", 
                data -> {
                    data.help = "Unban an IP";
                    data.category = "Moderation";
                    data.roles = new long[] { Roles.ADMIN, Roles.MOD };
                },
                ctx -> {
                    String ip = ctx.args.get("ip");
                    Vars.netServer.admins.unbanPlayerIP(ip);
                    ctx.success("Unbanned IP", "Unbanned " + ip);
                    DiscordLog.moderation("Unban IP", ctx.author(), null, ctx.args.get("reason"), "IP: " + ip);
                }
        );

        handler.register("bans", "", 
                data -> {
                    data.help = "List all bans";
                    data.category = "Moderation";
                    data.roles = new long[] { Roles.ADMIN, Roles.MOD };
                },
                ctx -> {
                    EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("Banned players")
                        .setColor(DiscordPalette.INFO);

                    var bans = Vars.netServer.admins.getBanned();
                    String s = bans.toString("\n", i -> "`" + i.id + "` | " + Utils.escapeEverything(i.lastName));
                    eb.addField("NetServer UUID bans", s.length() == 0 ? "None" : s);

                    var ipBans = Vars.netServer.admins.getBannedIPs();
                    s = ipBans.toString("\n", ip -> {
                        var info = Vars.netServer.admins.findByIP(ip);
                        if (info != null) {
                            return "`" + ip + "` | uuid `" + info.id + "` | " + Utils.escapeEverything(info.lastName);
                        } else {
                            return "`" + ip + "`";
                        }
                    });
                    eb.addField("NetServer IP bans", s.length() == 0 ? "None" : s);

                    var subnetBans = Vars.netServer.admins.subnetBans;
                    s = subnetBans.toString("\n", subnet -> {
                        return "`" + subnet + "`";
                    });
                    eb.addField("NetServer subnet bans", s.length() == 0 ? "None": s);

                    var dbBans = Database.bans();
                    if (dbBans == null) {
                        ctx.error("Internal Database Error", "Could not query database bans");
                    } else {
                        s = Seq.with(dbBans).toString("\n", p -> {
                            var info = Vars.netServer.admins.getInfo(p.uuid);
                            if (info == null) {
                                return "`" + p.uuid + "`";
                            } else {
                                return "`" + p.uuid + "` | " + Utils.escapeEverything(info.lastName);
                            }
                        });
                        eb.addField("Database bans", s.length() == 0 ? "None" :s);
                    }

                    ctx.sendEmbed(eb);
                }
        );

        handler.register("ban-subnet", "[add/remove/list] [address]",
                data -> {
                    data.help = "Ban a subnet. A subnet ban rejects all IPs that begin with the given string.";
                    data.category = "Moderation";
                    data.roles = new long[]{Roles.MOD, Roles.ADMIN, Roles.APPRENTICE};
                    data.aliases = new String[]{"subnet-ban"};
                },
                ctx -> {
                    if (!ctx.args.containsKey("add/remove/list") || ctx.args.get("add/remove/list").equals("list")) {
                        ctx.sendEmbed(DiscordPalette.INFO, "Subnet Bans",
                                Vars.netServer.admins.subnetBans.isEmpty() ?
                                        "None" :
                                        ("```\n" + Vars.netServer.admins.subnetBans.toString("\n") + "\n```")
                        );
                    } else if (ctx.args.get("add/remove/list").equals("add")) {
                        if (!ctx.args.containsKey("address")) {
                            ctx.error("Invalid Usage", "Must specify a subnet address");
                            return;
                        }
                        if (Vars.netServer.admins.subnetBans.contains(ctx.args.get("address"))) {
                            ctx.sendEmbed(DiscordPalette.WARN, "Subnet Ban Already Exists", "Subnet " + ctx.args.get("address") + " was already in the ban list");
                        }
                        Vars.netServer.admins.subnetBans.add(ctx.args.get("address"));

                        ctx.success("Added Subnet Ban", "Address: " + ctx.args.get("address"));
                        DiscordLog.moderation("Ban subnet", ctx.author(), null, null, "Subnet: " + ctx.args.get("address"));
                    } else if (ctx.args.get("add/remove/list").equals("remove")) {
                        if (!ctx.args.containsKey("address")) {
                            ctx.error("Invalid Usage", "Must specify a subnet address");
                            return;
                        }
                        Vars.netServer.admins.subnetBans.remove(ctx.args.get("address"));

                        ctx.success("Removed Subnet Ban", "Address: " + ctx.args.get("address"));
                        DiscordLog.moderation("Unban subnet", ctx.author(), null, null, "Subnet: " + ctx.args.get("address"));
                    } else {
                        ctx.error("Invalid Usage", "First argument must be add/remove/list");
                    }
                }
        );

        handler.register("unkick", "<player> [reason...]", 
                data -> {
                    data.help = "Unkick the player";
                    data.aliases = new String [] { "pardon" } ;
                    data.category = "Moderation";
                    data.roles = new long [] {Roles.ADMIN, Roles.MOD, Roles.APPRENTICE };
                }, 
                ctx -> {
                    var info = Utils.getPlayerInfo(ctx.args.get("player"));
                    if (info == null) {
                        ctx.error("No such player", ctx.args.get("player") + " is not saved in the netserver data");
                        return;
                    }                    
                    if (info.lastKicked == 0) {
                        ctx.error("Player not kicked", Utils.escapeColorCodes(info.lastName) + " is not kicked");
                        return;
                    }

                    info.lastKicked = 0;
                    ctx.success("Unkicked player", "Successfully unkicked " + Utils.escapeColorCodes(info.lastName));
                    DiscordLog.moderation("Unkick", ctx.author(), info, ctx.args.get("reason"), null);
                }
        );

        handler.register("lookup", "<player>",
                data -> {
                    data.category = "Moderation";
                    data.roles = new long []{ Roles.ADMIN, Roles.MOD };
                    data.aliases = new String[] { "l" };
                    data.help = "Lookup information about a player";
                },
                ctx -> {
                    var info = Utils.getPlayerInfo(ctx.args.get("player"));
                    if (info == null) {
                        ctx.error("No such player", ctx.args.get("player") + " is not in the database");
                        return;
                    }

                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Lookup: " + Utils.escapeEverything(info.lastName))
                            .addInlineField("UUID", info.id)
                            .addInlineField("Last IP", info.lastIP)
                            .addInlineField("Last name", info.lastName)
                            .addInlineField("Times kicked", info.timesKicked + "")
                            .addField("Names", info.names.toString(" / "))
                            .addField("IPs", info.ips.toString(" / "))
                            .addField("NetServer banned", info.banned ? "Yes" : "No");
                    
                    var pd =Database.getPlayerData(info.id);
                    if (pd != null) {
                        eb.addInlineField("Rank", Rank.all[pd.rank].name)
                            .addInlineField("Playtime", pd.playTime + " min")
                            .addInlineField("Games", pd.gamesPlayed + "")
                            .addInlineField("Buildings built", pd.buildingsBuilt + "")
                            .addField("Database banned", pd.banned ? "Forever" : (pd.bannedUntil != 0 ? "Until " + Instant.ofEpochSecond(pd.bannedUntil).toString() : "No"));
                    }

                    ctx.sendEmbed(eb);
                }
        );
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("freeze", "<player> [reason...]", "Freeze a player. To unfreeze just use this command again.", (args, player) -> {
            if (!player.admin()) {
                player.sendMessage(GameMsg.noPerms("Mod"));
                return;
            }

            Player target = Utils.findPlayer(args[0]);
            if (target == null) {
                player.sendMessage(GameMsg.error("Mod", "Player not found."));
                return;
            }

            if (!frozen.contains(target.uuid())) {
                frozen.add(target.uuid());
            } else {
                frozen.remove(target.uuid());
            }

            String reason = args.length > 1 ? args[1] : null;
            boolean isFrozen = frozen.contains(target.uuid());
            player.sendMessage(
                    GameMsg.custom("Mod", "cyan", "[cyan]Successfully " + (isFrozen ? "froze" : "thawed") + " " + Utils.escapeEverything(target)));
            Call.infoMessage(target.con, "[cyan]You got " + (isFrozen ? "frozen" : "thawed") + " by a moderator. \n"
                    + "[lightgray]" + (reason != null ? "Reason: [accent]" + reason : ""));
            DiscordLog.moderation(isFrozen ? "Froze": "Thawed", Utils.escapeEverything(player.name), Vars.netServer.admins.getInfo(target.uuid()), reason, null);
        });

        handler.<Player>register("mute", "<player> [reason...]", "Mute a player. To unmute just use this command again.", (args, player) -> {
            if (!player.admin()) {
                player.sendMessage(GameMsg.noPerms("Mod"));
                return;
            }

            Player target = Utils.findPlayer(args[0]);
            if (target == null) {
                player.sendMessage(GameMsg.error("Mod", "Player not found."));
                return;
            }

            if (!muted.contains(target.uuid())) {
                muted.add(target.uuid());
            } else {
                muted.remove(target.uuid());
            }

            String reason = args.length > 1 ? args[1] : null;
            boolean isMuted = muted.contains(target.uuid());            
            player.sendMessage(GameMsg.custom("Mod", "cyan", "Successfully " + (isMuted ? "muted" : "unmuted") + " " + Utils.escapeEverything(target)));
            Call.infoMessage(target.con, "[cyan]You got " + (isMuted ? "muted" : "unmuted") + " by a moderator.\n" +
                    "[lightgray]" + (args.length > 1 ? "Reason: [accent]" + args[1] : ""));
            DiscordLog.moderation(isMuted ? "Muted": "Unmuted", Utils.escapeEverything(player.name), Vars.netServer.admins.getInfo(target.uuid()), reason, null);
        });


        handler.<Player>register("label", "<duration> <text...>", "[admin only] Create an in-world label at the current position.", (args, player) -> {
            if (!player.admin) {
                player.sendMessage(GameMsg.noPerms("Mod"));
                return;
            }

            if (args[0].length() <= 0 || args[1].length() <= 0) {
                player.sendMessage("[scarlet]Invalid arguments provided.");
                return;
            }

            float x = player.getX();
            float y = player.getY();

            Tile targetTile = Vars.world.tileWorld(x, y);
            Call.label(args[1], Float.parseFloat(args[0]), targetTile.worldx(), targetTile.worldy());
        });

        handler.<Player>register("reset", "Set everyone's name back to the original name.", (args, player) -> {
            if (!player.admin) {
                player.sendMessage(GameMsg.noPerms("Mod"));
                return;
            }

            for (Player p : Groups.player) {
                Database.Player pd = Database.getPlayerData(p.uuid());
                if (pd == null) continue;
                p.name = Rank.all[pd.rank].tag + Vars.netServer.admins.getInfo(p.uuid()).lastName;
            }
            player.sendMessage("[cyan]Reset names!");
        });
    }
}
