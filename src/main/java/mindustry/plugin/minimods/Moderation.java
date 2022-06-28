package mindustry.plugin.minimods;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
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
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.Context;
import mindustry.plugin.discord.discordcommands.DiscordCommands;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.discord.discordcommands.RoleRestrictedCommand;
import mindustry.plugin.ioMain;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.world.Tile;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.interaction.SlashCommand;

import java.awt.*;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static mindustry.Vars.netServer;
import static mindustry.Vars.world;

/** Manages mutes and freezes */
public class Moderation implements MiniMod {
    private ObjectSet<String> frozen = new ObjectSet<>();
    private ObjectSet<String> muted = new ObjectSet<>();

    @Override
    public void registerEvents() {
        Events.on(EventType.ServerLoadEvent.class, event -> {
            netServer.admins.addChatFilter((player, message) -> {
                assert player != null;
                if (muted.contains(player.uuid())) {
                    return null;
                }
                return message;
            });
            netServer.admins.addActionFilter(action -> {
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
        handler.register("mute", "<player> [reason...]", data -> {
            data.help = "Mute or unmute a player";
            data.aliases = new String[] { "m" };
            data.category = "Moderation";
            data.roles = new long[] { Roles.ADMIN, Roles.MODERATOR };
        }, ctx -> {
            ctx.args.get("player");
        });

        handler.register("mute", "<player> [reason...]",
            data -> {
                data.roles = new long[] { Roles.ADMIN, Roles.MODERATOR };
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
                    "[lightgray]" + (ctx.args.length > 2 ? "Reason: [accent]" + ctx.message.split(" ", 2)[1] : ""));
                
            }
        );

        handler.registerCommand(new RoleRestrictedCommand("freeze") {
            {
                roles = new long[] {Roles.ADMIN, Roles.MODERATOR};
                help = "Freeze or thaw a player.";
                usage = "<player> [reason...]";
                minArguments = 1;
                category = "Moderation";
            }

            @Override
            public void run(Context ctx) {
                String target = ctx.args[1];
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
                    "[lightgray]" + (ctx.args.length > 2 ? "Reason: [accent]" + ctx.message.split(" ", 2)[1] : ""));
            }
        });
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
