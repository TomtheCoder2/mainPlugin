package mindustry.plugin.mindustrycommands;

import arc.util.CommandHandler;
import arc.util.Strings;
import arc.util.Timekeeper;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.plugin.MiniMod;
import mindustry.plugin.data.PersistentPlayerData;
import mindustry.plugin.database.Database;
import mindustry.plugin.ioMain;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.plugin.utils.VoteSession;
import mindustry.world.Tile;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;

import java.awt.*;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static mindustry.Vars.netServer;
import static mindustry.Vars.world;

public class Moderation implements MiniMod {
/*    @Override
    public void registerServerCommands(CommandHandler handler) {
                handler.register("vote", "<y/n/c>", "Vote for current votekick", arg -> {
            if (currentlyKicking[0] == null) {
                info("[scarlet]Nobody is being voted on.");
            } else {
                if (arg[0].equalsIgnoreCase("c")) {
                    currentlyKicking[0].map[0] = null;
                    currentlyKicking[0].task.cancel();
                    Call.sendMessage("[scarlet]Server []canceled the kick.");
                }

                int sign = switch (arg[0].toLowerCase()) {
                    case "y", "yes" -> 1;
                    case "n", "no" -> -1;
                    default -> 0;
                };

                if (sign == 0) {
                    info("[scarlet]Vote either 'y' (yes) or 'n' (no).");
                    return;
                }

                currentlyKicking[0].vote(sign);
            }
        });
    }*/

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("votekick", "[player...]", "votekick a player.", (args, player) -> {
//               CustomLog.debug("vk @.", args[0]);
            if (!Administration.Config.enableVotekick.bool()) {
                player.sendMessage("[scarlet]Vote-kick is disabled on this server.");
                return;
            }

            if (Groups.player.size() < 3) {
                player.sendMessage("[scarlet]At least 3 players are needed to start a votekick.");
                return;
            }

            if (player.isLocal()) {
                player.sendMessage("[scarlet]Just kick them yourself if you're the host.");
                return;
            }

            if (ioMain.currentlyKicking[0] != null) {
                player.sendMessage("[scarlet]A vote is already in progress.");
                return;
            }

            if (args.length == 0) {
                StringBuilder builder = new StringBuilder();
                builder.append("[orange]Players to kick: \n");

                Groups.player.each(p -> !p.admin && p.con != null && p != player, p -> {
                    builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id()).append(")\n");
                });
                player.sendMessage(builder.toString());
            } else {
                Player found = Utils.findPlayer(args[0]);
                if (found == null) {
                    if (args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))) {
                        int id = Strings.parseInt(args[0].substring(1));
                        found = Groups.player.find(p -> p.id() == id);
                    } else {
                        found = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
                    }
                }

                if (found != null) {
                    if (found == player) {
                        player.sendMessage("[scarlet]You can't vote to kick yourself.");
                    } else if (found.admin) {
                        player.sendMessage("[scarlet]Did you really expect to be able to kick an admin?");
                    } else if (found.isLocal()) {
                        player.sendMessage("[scarlet]Local players cannot be kicked.");
                    } else if (found.team() != player.team()) {
                        player.sendMessage("[scarlet]Only players on your team can be kicked.");
                    } else if (Objects.equals(found.uuid(), "VA8X0BlqyTsAAAAAFkLMBg==")) {
                        player.sendMessage("[scarlet]Did you really expect to be able to kick [cyan]Nautilus[scarlet]?");
                    } else {
                        Timekeeper vtime = ioMain.cooldowns.get(player.uuid(), () -> new Timekeeper(ioMain.voteCooldown));

                        if (!vtime.get()) {
                            player.sendMessage("[scarlet]You must wait " + ioMain.voteCooldown / 60 + " minutes between votekicks.");
                            return;
                        }

                        VoteSession session = new VoteSession(ioMain.currentlyKicking, found);
                        session.vote(player, 1);

                        // freeze the player
                        PersistentPlayerData tdata = ioMain.playerDataGroup.get(found.uuid());
                        if (tdata != null) {
                            tdata.frozen = !tdata.frozen;
                            player.sendMessage("[cyan]Successfully " + (tdata.frozen ? "froze" : "thawed") + " " + Utils.escapeEverything(found));
                            found.sendMessage("[cyan]You got " + (tdata.frozen ? "frozen" : "thawed") + " during the votekick!");
                        }

                        vtime.reset();
                        ioMain.currentlyKicking[0] = session;
                    }
                } else {
                    player.sendMessage("[scarlet]No player [orange]'" + args[0] + "'[scarlet] found.");
                }
            }
        });

        handler.<Player>register("vote", "<y/n/c>", "Vote to kick the current player. Or cancel the current kick.", (arg, player) -> {
            if (ioMain.currentlyKicking[0] == null) {
                player.sendMessage("[scarlet]Nobody is being voted on.");
            } else {
                if (arg[0].equalsIgnoreCase("c")) {
                    if (ioMain.currentlyKicking[0].startedVk == player || player.admin) {
                        ioMain.currentlyKicking[0].cancel(player);
                    } else {
                        player.sendMessage("[scarlet]This command is restricted to the player who started the votekick and admins");
                    }
                    return;
                }

                if (player.isLocal()) {
                    player.sendMessage("[scarlet]Local players can't vote. Kick the player yourself instead.");
                    return;
                }

                //hosts can vote all they want
                if ((ioMain.currentlyKicking[0].voted.contains(player.uuid()) || ioMain.currentlyKicking[0].voted.contains(netServer.admins.getInfo(player.uuid()).lastIP))) {
                    player.sendMessage("[scarlet]You've already voted. Sit down.");
                    return;
                }

                if (ioMain.currentlyKicking[0].target == player) {
                    player.sendMessage("[scarlet]You can't vote on your own trial.");
                    return;
                }

                if (ioMain.currentlyKicking[0].target.team() != player.team()) {
                    player.sendMessage("[scarlet]You can't vote for other teams.");
                    return;
                }

                int sign = switch (arg[0].toLowerCase()) {
                    case "y", "yes" -> 1;
                    case "n", "no" -> -1;
                    default -> 0;
                };

                if (sign == 0) {
                    player.sendMessage("[scarlet]Vote either 'y' (yes) or 'n' (no).");
                    return;
                }

                ioMain.currentlyKicking[0].vote(player, sign);
            }
        });

        handler.<Player>register("redeem", "<key>", "Verify the redeem command (Discord)", (arg, player) -> {
            try {
                PersistentPlayerData tdata = (ioMain.playerDataGroup.getOrDefault(player.uuid(), null));
                if (tdata.redeemKey != -1) {
                    if (Integer.parseInt(arg[0]) == tdata.redeemKey) {
                        StringBuilder roleList = new StringBuilder();
                        Database.Player pd = Database.getPlayerData(player.uuid());
                        for (var entry: Rank.roles) {
                            long roleID = entry.key;
                            assert pd != null;
                            if (entry.value <= pd.rank) {
                                System.out.println("add role: " + ioMain.api.getRoleById(roleID).get());
                                roleList.append("<@").append(ioMain.api.getRoleById(roleID).get().getIdAsString()).append(">\n");
                                ioMain.api.getUserById(tdata.redeem).get().addRole(ioMain.api.getRoleById(roleID).get());
                            }
                        }
                        System.out.println(roleList);
                        Utils.getTextChannel(ioMain.log_channel_id).sendMessage(new EmbedBuilder().setTitle("Updated roles!").addField("Discord Name", ioMain.api.getUserById(tdata.redeem).get().getName(), true).addField("In Game Name", tdata.origName, true).addField("In Game UUID", player.uuid(), true).addField("Added roles", roleList.toString(), true));
                        player.sendMessage("Successfully redeem to account: [green]" + ioMain.api.getUserById(tdata.redeem).get().getName());
                        tdata.task.cancel();
                    } else {
                        player.sendMessage("[scarlet]Wrong code!");
                    }

                    tdata.redeemKey = -1;
                } else {
                    player.sendMessage("Please use the redeem command on the discord server first");
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                player.sendMessage("[scarlet]There was an error: " + e.getMessage());
            }
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
