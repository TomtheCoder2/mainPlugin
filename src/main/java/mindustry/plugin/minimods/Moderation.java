package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.ObjectSet;
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


/**
 * Manages mutes, freezes, bans, and other moderation-related commands
 */
public class Moderation implements MiniMod {
    private ObjectSet<String> frozen = new ObjectSet<>();
    private ObjectSet<String> muted = new ObjectSet<>();

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
                        ctx.reply("Player " + target + " not found.");
                        return;
                    }

                    if (!muted.contains(player.uuid())) {
                        muted.add(player.uuid());
                    } else {
                        muted.remove(player.uuid());
                    }
                    boolean isMuted = muted.contains(player.uuid());
                    ctx.reply("Successfully " + (isMuted ? "muted" : "unmuted") + " " + Utils.escapeEverything(target));
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
                    long duration = ctx.args.getLong("duration:minutes", 2 * 365 * 24 * 60) * 60;
                    pd.bannedUntil = Instant.now().getEpochSecond() + duration;
                    pd.banReason = reason + "\n\n[accent]Until: " + Utils.epochToString(pd.bannedUntil) + " [accent]Ban ID: " + banID;
                    Database.setPlayerData(pd);

                    ctx.sendEmbed(new EmbedBuilder()
                            .setTitle("Banned " + info.lastName)
                            .addField("Duration", duration / 60 + " minutes")
                            .addField("Until", Utils.epochToString(pd.bannedUntil))
                            .addField("Reason", reason)
                            .setFooter("Ban ID: " + banID)
                    );

                    Player player = Groups.player.find(p -> p.uuid() == uuid);
                    if (player != null) {
                        player.con.kick(Packets.KickReason.banned);
                    }

                    DiscordLog.logAction(LogAction.ban, info, ctx, reason);
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
                    } else if (ctx.args.get("add/remove/list").equals("remove")) {
                        if (!ctx.args.containsKey("address")) {
                            ctx.error("Invalid Usage", "Must specify a subnet address");
                            return;
                        }
                        Vars.netServer.admins.subnetBans.remove(ctx.args.get("address"));

                        ctx.success("Removed Subnet Ban", "Address: " + ctx.args.get("address"));
                    } else {
                        ctx.error("Invalid Usage", "First argument must be add/remove/list");
                    }
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
            boolean isFrozen = frozen.contains(target.uuid());
            player.sendMessage(
                    GameMsg.custom("Mod", "cyan", "[cyan]Successfully " + (isFrozen ? "froze" : "thawed") + " " + Utils.escapeEverything(target)));
            Call.infoMessage(target.con, "[cyan]You got " + (isFrozen ? "frozen" : "thawed") + " by a moderator. \n"
                    + "[lightgray]" + (args.length > 1 ? "Reason: [accent]" + args[1] : ""));
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
            boolean isMuted = muted.contains(target.uuid());
            player.sendMessage(GameMsg.custom("Mod", "cyan", "Successfully " + (isMuted ? "muted" : "unmuted") + " " + Utils.escapeEverything(target)));
            Call.infoMessage(target.con, "[cyan]You got " + (isMuted ? "muted" : "unmuted") + " by a moderator.\n" +
                    "[lightgray]" + (args.length > 1 ? "Reason: [accent]" + args[1] : ""));
        });

        Cooldowns.instance.set("gr", 5 * 60);
        handler.<Player>register("gr", "[player] [reason...]", "Report a griefer by id (use '/gr' to get a list of ids)", (args, player) -> {
            if (!Cooldowns.instance.canRun("gr", player.uuid())) {
                GameMsg.ratelimit("Mod", "gr");
            }
            Cooldowns.instance.run("gr", player.uuid());

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

                if (found == null) {
                    player.sendMessage("[scarlet]No player[orange] '" + args[0] + "'[scarlet] found.");
                }

                if (found.admin()) {
                    player.sendMessage("[scarlet]Did you really expect to be able to report an admin?");
                } else if (found.team() != player.team()) {
                    player.sendMessage("[scarlet]Only players on your team can be reported.");
                } else {
                    //send message
                    if (args.length > 1) {
                        new MessageBuilder()
                                .setEmbed(new EmbedBuilder().setTitle("Potential griefer online")
                                        .addField("name", Utils.escapeColorCodes(found.name)).addField("reason", args[1]).setColor(Color.RED).setFooter("Reported by " + player.name))
                                .setContent("<@&" + Roles.MOD + ">")
                                .send(Channels.GR_REPORT);
                    } else {
                        new MessageBuilder()
                                .setEmbed(new EmbedBuilder().setTitle("Potential griefer online")
                                        .addField("name", Utils.escapeColorCodes(found.name)).setColor(Color.RED).setFooter("Reported by " + player.name))
                                .setContent("<@&" + Roles.MOD + ">")
                                .send(Channels.GR_REPORT);
                        Channels.GR_REPORT.sendMessage("<@&" + +Roles.MOD + ">");
                    }
                    Call.sendMessage(found.name + "[sky] is reported to discord.");
                }
            }
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
