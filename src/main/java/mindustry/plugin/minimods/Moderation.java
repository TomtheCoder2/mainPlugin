package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.Packets;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.*;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Query;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.channel.AutoArchiveDuration;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static mindustry.plugin.discord.DiscordLog.moderationLogColonel;


/**
 * Manages mutes, freezes, bans, and other moderation-related commands
 */
public class Moderation implements MiniMod {
    public static final ObjectSet<String> frozen = new ObjectSet<>();
    public final ObjectSet<String> muted = new ObjectSet<>();

    @Override
    public void registerEvents() {
        Events.on(EventType.ServerLoadEvent.class, event -> {
            Vars.netServer.admins.addChatFilter((player, message) -> {
                assert player != null;
                if (muted.contains(player.uuid())) {
                    player.sendMessage("[scarlet]You are muted! Ask a mod to unmute you");
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
                    Player player = Query.findPlayerEntity(target);
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

        handler.register("freeze", "<player> [reason...]",
                data -> {
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD, Roles.APPRENTICE};
                    data.help = "Freeze or thaw a player.";
                    data.category = "Moderation";
                },
                ctx -> {
                    String target = ctx.args.get("player");
                    Player player = Query.findPlayerEntity(target);
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
                    System.out.println(ctx.event.getMessageAttachments().size());
                    if (ctx.event.getMessageAttachments().size() < 1) {
                        ctx.error("Missing Attachment(s)", "Please provide a picture as evidence for the ban");
                        return;
                    }
                    Administration.PlayerInfo info = Query.findPlayerInfo(ctx.args.get("player"));
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
                            .addField("Duration", duration == 0 ? "forever" : duration / 60 + " minutes")
                            .addField("Until", Utils.epochToString(pd.bannedUntil))
                            .addField("Reason", reason)
                            .setFooter("Ban ID: " + banID)
                    );

                    Player player = Groups.player.find(p -> p.uuid().equals(uuid));
                    if (player != null) {
                        player.con.kick(Packets.KickReason.banned);
                    }

                    DiscordLog.moderation("Banned", ctx.author(), Vars.netServer.admins.getInfo(pd.uuid), reason, null);

                    moderationLogColonel("Banned", "<@" + ctx.author().getId() + ">", Vars.netServer.admins.getInfo(pd.uuid), reason, null, ctx.event.getMessage().getAttachments());
                }
        );

        handler.register("unban", "<player>",
                data -> {
                    data.help = "Unban a player";
                    data.category = "Moderation";
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD, Roles.APPRENTICE};
                },
                ctx -> {
                    var info = Query.findPlayerInfo(ctx.args.get("player"));
                    String uuid = ctx.args.get("player");
                    if (info != null) {
                        uuid = info.id;
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
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD};
                },
                ctx -> {
                    String ip = ctx.args.get("ip");
                    Vars.netServer.admins.banPlayerIP(ip);
                    Player p = null;
                    for (Player pl : Groups.player) {
                        if (Objects.equals(ip, pl.ip())) {
                            p = pl;
                        }
                    }
                    if (p != null) {
                        p.kick("You are banned on this server.");
                    }
                    ctx.success("Banned IP", "Banned " + ip);
                    DiscordLog.moderation("Ban IP", ctx.author(), null, ctx.args.get("reason"), "IP: " + ip);
                }
        );

        handler.register("unbanip", "<ip> [reason...]",
                data -> {
                    data.help = "Unban an IP";
                    data.category = "Moderation";
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD};
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
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD};
                },
                ctx -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Ban Type,UUID,IP,Name,Until\n");

                    var bans = Vars.netServer.admins.getBanned();
                    for (var ban : bans) {
                        sb.append(String.format("NS UUID,%s,%s,%s,\n", ban.id, ban.lastIP, Utils.escapeEverything(ban.lastName)));
                    }

                    var ipBans = Vars.netServer.admins.getBannedIPs();
                    for (var ip : ipBans) {
                        var info = Vars.netServer.admins.findByIP(ip);
                        sb.append(String.format("NS IP,%s,%s,%s,\n", info == null ? "" : info.id, ip, info == null ? "" : Utils.escapeEverything(info.lastName)));
                    }

                    var subnetBans = Vars.netServer.admins.subnetBans;
                    for (var subnet : subnetBans) {
                        sb.append(String.format("NS Subnet,%s,%s,%s,\n", "", subnet, ""));
                    }

                    var dbBans = Database.bans();
                    if (dbBans == null) {
                        ctx.error("Internal Database Error", "Could not query database bans");
                    } else {
                        for (var pd : dbBans) {
                            var info = Vars.netServer.admins.getInfo(pd.uuid);
                            sb.append(String.format("Database,%s,%s,%s,%s\n", pd.uuid, info == null ? "" : info.lastIP, Utils.escapeEverything(info.lastName), pd.banned ? "" : Utils.epochToString(pd.bannedUntil)));
                        }
                    }

                    ctx.sendMessage(new MessageBuilder()
                            .addAttachment(sb.toString().getBytes(), "bans.csv")
                    );
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
                    data.aliases = new String[]{"pardon"};
                    data.category = "Moderation";
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD, Roles.APPRENTICE};
                },
                ctx -> {
                    var info = Query.findPlayerInfo(ctx.args.get("player"));
                    if (info == null) {
                        ctx.error("No such player", ctx.args.get("player") + " is not saved in the netserver data");
                        return;
                    }
                    if (info.lastKicked == 0) {
                        ctx.error("Player not kicked", Utils.escapeEverything(info.lastName) + " is not kicked");
                        return;
                    }

                    var pd = Database.getPlayerData(info.id);
                    if (pd != null) {
                        pd.bannedUntil = 0;
                        // don't set "ban" because kicking does not permaban
                        Database.setPlayerData(pd);
                    }

                    info.lastKicked = 0;
                    ctx.success("Unkicked player", "Successfully unkicked " + Utils.escapeEverything(info.lastName));
                    DiscordLog.moderation("Unkick", ctx.author(), info, ctx.args.get("reason"), null);
                }
        );

        handler.register("lookup", "<player>",
                data -> {
                    data.category = "Moderation";
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD, Roles.APPRENTICE};
                    data.aliases = new String[]{"l"};
                    data.help = "Lookup information about a player (by name, IP, UUID)";
                },
                ctx -> {
                    var info = Query.findPlayerInfo(ctx.args.get("player"));
                    if (info == null) {
                        ctx.error("No such player", ctx.args.get("player") + " is not in the database");
                        return;
                    }
                    if (info.names.size == 0) {
                        ctx.error("Unknown Player", "Could not find player " + ctx.args.get("player"));
                    }
                    EmbedBuilder eb = new EmbedBuilder()
                            .setColor(DiscordPalette.INFO)
                            .setTitle("Lookup: " + Utils.escapeEverything(info.lastName));

                    eb.addField("Names", info.names.toString(" / "));

                    if (ctx.channel().getId() == Channels.ADMIN_BOT.getId() || ctx.channel().getId() == Channels.MOD_BOT.getId()) {
                        // if there are too many IPs, take last 1024 / 18 IPs
                        eb.addField("IPs", info.ips.toString(" / ").length() > 1000 ? StreamSupport.stream(info.ips.spliterator(), false).skip(info.ips.size - (1024 / 18) > 0 ? info.ips.size - (1024 / 18) : 0).collect(Collectors.joining(" / ")) : info.ips.toString(" / "))
                                .addInlineField("UUID", info.id)
                                .addInlineField("Last IP", info.lastIP);
                    }

                    var pd = Database.getPlayerData(info.id);
                    if (pd != null) {
                        eb.addField("Phash", pd.phash);
                    }

                    eb
                            .addField("Last name", info.lastName)
                            .addField("Times kicked", info.timesKicked + "")
                            .addField("NetServer banned", info.banned ? "Yes" : "No");

                    if (pd != null) {
                        eb.addInlineField("Rank", Rank.all[pd.rank].name)
                                .addInlineField("Playtime", pd.playTime + " min")
                                .addInlineField("Games", pd.gamesPlayed + "")
                                .addInlineField("Buildings built", pd.buildingsBuilt + "")
                                .addInlineField("Banned", pd.banned ? "Forever" : (pd.bannedUntil != 0 ? "Until " + Instant.ofEpochSecond(pd.bannedUntil).toString() : "No"))
                                .addInlineField("Ban Reason", pd.banReason == null || pd.banReason.equals("") ? "None" : pd.banReason);

                        if (pd.discord != 0) {
                            eb.addField("Discord", "<@" + pd.discord + ">");
                        }
                    }

                    ctx.sendEmbed(eb);
                }
        );

        handler.register("rename", "<player> <name...>",
                data -> {
                    data.category = "Moderation";
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD, Roles.APPRENTICE};
                    data.aliases = new String[]{"r"};
                },
                ctx -> {
                    Player p = Query.findPlayerEntity(ctx.args.get("player"));
                    if (p == null) {
                        ctx.error("Player not found", "Target player is not online");
                        return;
                    }

                    String oldName = Utils.escapeEverything(p.name);
                    p.name = ctx.args.get("name");

                    p.sendMessage("Your name was changed to [orange]" + p.name + "[white] by a moderator");
                    ctx.success("Renamed player", "Renamed " + oldName + " to " + Strings.stripColors(p.name));
                    DiscordLog.moderation("Rename", ctx.author(), p.getInfo(), null, "Old: " + oldName + "\nNew: " + Strings.stripColors(p.name));
                }
        );

        handler.register("bannedwords", "<add|list|update|remove> [words...]",
                data -> {
                    data.category = "Moderation";
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD, Roles.APPRENTICE};
                    data.help = "Add or list banned words";
                    data.aliases = new String[]{"bw"};
                },
                ctx -> {
                    String mode = ctx.args.get("add|list|update|remove");
                    if (Objects.equals(mode, "list")) {
                        ctx.success("Banned words", Database.bannedWords.toString("\n"));
                    } else if (Objects.equals(mode, "add")) {
                        String words = ctx.args.get("words");
                        if (words == null) {
                            ctx.error("No words", "You must specify words to add");
                            return;
                        }
                        String[] split = words.split(" ");
                        for (String s : split) {
                            if (Database.bannedWords.contains(s)) {
                                ctx.error("Word already banned", s + " is already banned");
                                return;
                            }
                        }
                        Database.bannedWords.addAll(split);
                        Database.updateBannedWordsDatabase();
                        ctx.success("Added banned words", "Added `" + words + "` to the banned words list");
                        DiscordLog.moderation("Banned words", ctx.author(), null, "Added `" + words + "`", null);
                    } else if (Objects.equals(mode, "update")) {
                        Database.updateBannedWordsDatabase();
                        Database.updateBannedWordsClient();
                        ctx.success("Updated banned words", "Updated the banned words list");
                        DiscordLog.moderation("Banned words", ctx.author(), null, "Updated", null);
                    } else if (Objects.equals(mode, "remove")) {
                        String words = ctx.args.get("words");
                        if (words == null) {
                            ctx.error("No words", "You must specify words to remove");
                            return;
                        }
                        String[] split = words.split(" ");
                        for (String s : split) {
                            if (!Database.bannedWords.contains(s)) {
                                ctx.error("Word not banned", s + " is not banned");
                                return;
                            }
                        }
                        Database.bannedWords.removeAll(new Seq<>(split));
                        Database.removeBannedWordDatabase(split);
                        ctx.success("Removed banned words", "Removed `" + words + "` from the banned words list");
                        DiscordLog.moderation("Banned words", ctx.author(), null, "Removed `" + words + "`", null);
                    } else {
                        ctx.error("Invalid mode", "Mode must be either `add`, `update` or `list`");
                    }
                }
        );

        handler.register("appeal", "",
                data -> {
                    data.category = "Moderation";
                    data.help = "Request an appeal";
                },
                ctx -> {
                    ctx.author().addRole(DiscordVars.api.getRoleById(Roles.APPEAL).get()).join();

                    var msg = new MessageBuilder()
                            .addEmbed(
                                    new EmbedBuilder()
                                            .setColor(DiscordPalette.WARN)
                                            .setTitle("Use the following format to appeal")
                                            .addField("1. Names", "All names that you've used in game")
                                            .addField("2. Screenshot", "Send a screenshot of your ban screen")
                                            .addField("3. Reason", "Explain what you did and why you want to get unbanned")
                            )
                            .setContent("<@" + ctx.author().getId() + ">")
                            .send(Channels.APPEAL)
                            .join();
                    msg.createThread("Appeal: " + ctx.author().getDisplayName(DiscordVars.server()), AutoArchiveDuration.THREE_DAYS);

                    ctx.success("Successfully requested an appeal", "Head over to <#" + Channels.APPEAL.getIdAsString() + ">");
                }
        );
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        Utils.registerRankCommand(handler, "freeze", "<player> [reason...]", Rank.APPRENTICE,
                "Freeze a player. To unfreeze just use this command again.", (args, player) -> {
                    Player target = Query.findPlayerEntity(args[0]);
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
                    DiscordLog.moderation(isFrozen ? "Froze" : "Thawed", Utils.escapeEverything(player.name), Vars.netServer.admins.getInfo(target.uuid()), reason, null);
                });

        Utils.registerRankCommand(handler, "mute", "<player> [reason...]", Rank.APPRENTICE,
                "Mute a player. To unmute just use this command again.", (args, player) -> {
                    Player target = Query.findPlayerEntity(args[0]);
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
                    DiscordLog.moderation(isMuted ? "Muted" : "Unmuted", Utils.escapeEverything(player.name), Vars.netServer.admins.getInfo(target.uuid()), reason, null);
                });

        Utils.registerRankCommand(handler, "reset", "", Rank.APPRENTICE,
                "Set everyone's name back to the original name.", (args, player) -> {
                    for (Player p : Groups.player) {
                        Database.Player pd = Database.getPlayerData(p.uuid());
                        if (pd == null) continue;
                        p.name = Utils.formatName(Rank.all[pd.rank], "[#" + player.color.toString().substring(0, 6) + "]" + Vars.netServer.admins.getInfo(pd.uuid).lastName);
                    }
                    player.sendMessage("[cyan]Reset names!");
                });
    }
}
