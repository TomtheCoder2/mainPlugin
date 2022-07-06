package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timekeeper;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.Packets;
import mindustry.plugin.MiniMod;
import mindustry.plugin.data.PersistentPlayerData;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordLog;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.Context;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.ioMain;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.LogAction;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.world.Tile;

import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;

import java.awt.*;
import java.time.Instant;


/** Manages mutes, freezes, bans, and other moderation-related commands */
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
                data.roles = new long[] { Roles.ADMIN, Roles.MOD, Roles.APPRENTICE };
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
                data.roles = new long[] { Roles.ADMIN, Roles.MOD, Roles.APPRENTICE };
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
                data.roles = new long[] { Roles.ADMIN, Roles.MOD, Roles.APPRENTICE };
                data.help = "Ban a player";
                data.category = "Moderation";
                data.aliases = new String[] { "b", "banish" };
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
                    .setFooter("Ban ID: " +banID)
                );

                Player player = Groups.player.find(p -> p.uuid() == uuid);
                if (player != null) {
                    player.con.kick(Packets.KickReason.banned);
                }

                DiscordLog.logAction(LogAction.ban, info, ctx, reason);
            }
        );

        handler.register("alert", "<player> <message...>", 
            data -> {
                data.help = "Alerts a player(s) using on-screen messages.";
                data.roles = new long[] { Roles.MOD, Roles.ADMIN, Roles.APPRENTICE };
                data.category = "Moderation";
                data.aliases = new String[] { "a" };
            },
            ctx -> {
                String target = ctx.args.get("player").toLowerCase();
                if (target.equals("all")) {
                    Call.infoMessage(ctx.args.get("message"));

                    ctx.success("Alerted", "Alerted " + Groups.player.size() + " players.");
                    return;
                }

                Player p = Utils.findPlayer(target);
                if (p == null) {
                    ctx.error("Error", "Player '" + target + "' not found");
                    return;
                }

                Call.infoMessage(p.con, ctx.args.get("message"));
                ctx.success("Alerted", "Alerted " + Utils.escapeEverything(p) + ".");
            }
        );
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("inspector", "Toggle inspector.", (args, player) -> {
            PersistentPlayerData pd = (ioMain.playerDataGroup.getOrDefault(player.uuid(), null));
            pd.inspector = !pd.inspector;
            player.sendMessage((pd.inspector ? "Enabled" : "Disabled") + " the inspector.");
        });

        handler.<Player>register("freeze", "<player> [reason...]", "Freeze a player. To unfreeze just use this command again.", (args, player) -> {
            if (!player.admin()) {
                player.sendMessage(Utils.noPermissionMessage);
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
                player.sendMessage(Utils.noPermissionMessage);
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
                                    .addField("name", Utils.escapeColorCodes(found.name)).addField("reason", args[1]).setColor(Color.RED).setFooter("Reported by " + player.name)).send(Channels.GR_REPORT);
                            Channels.GR_REPORT.sendMessage("<@&882340213551140935>");
                        } else {
                            Role ro = ioMain.discRoles.get("861523420076179457");
                            new MessageBuilder().setEmbed(new EmbedBuilder().setTitle("Potential griefer online")
//                                                .setDescription("<@&861523420076179457>")
                                    .addField("name", Utils.escapeColorCodes(found.name)).setColor(Color.RED).setFooter("Reported by " + player.name)).send(Channels.GR_REPORT);
                            Channels.GR_REPORT.sendMessage("<@&882340213551140935>");
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

                Tile targetTile = Vars.world.tileWorld(x, y);
                Call.label(args[1], Float.parseFloat(args[0]), targetTile.worldx(), targetTile.worldy());
            } else {
                player.sendMessage(Utils.noPermissionMessage);
            }
        });

        handler.<Player>register("reset", "Set everyone's name back to the original name.", (args, player) -> {
            if (player.admin) {
                for (Player p : Groups.player) {
                    Database.Player pd = Database.getPlayerData(p.uuid());
                    if (pd == null) continue;
                    p.name = Rank.all[pd.rank].tag + Vars.netServer.admins.getInfo(p.uuid()).lastName;
                }
                player.sendMessage("[cyan]Reset names!");
            } else {
                player.sendMessage(Utils.noPermissionMessage);
            }
        });
    }
}
