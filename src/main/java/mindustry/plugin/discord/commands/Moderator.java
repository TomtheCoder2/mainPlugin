//package mindustry.plugin.discord.commands;
//
//import arc.Core;
//import arc.Events;
//import arc.struct.Seq;
//import arc.util.Strings;
//import arc.util.Structs;
//import arc.util.Timer;
//import com.google.gson.Gson;
//import com.google.gson.GsonBuilder;
//import com.google.gson.JsonElement;
//import com.google.gson.JsonObject;
//import mindustry.content.Blocks;
//import mindustry.content.Bullets;
//import mindustry.content.UnitTypes;
//import mindustry.core.GameState;
//import mindustry.entities.bullet.BulletType;
//import mindustry.game.EventType.GameOverEvent;
//import mindustry.game.Team;
//import mindustry.gen.Call;
//import mindustry.gen.Groups;
//import mindustry.gen.Player;
//import mindustry.gen.Unit;
//import mindustry.maps.Map;
//import mindustry.net.Administration;
//import mindustry.net.Packets;
//import mindustry.plugin.data.PersistentPlayerData;
//import mindustry.plugin.database.Database;
//import mindustry.plugin.discord.discordcommands.Command;
//import mindustry.plugin.discord.discordcommands.Context;
//import mindustry.plugin.discord.discordcommands.DiscordCommands;
//import mindustry.plugin.discord.discordcommands.RoleRestrictedCommand;
//import mindustry.plugin.ioMain;
//import mindustry.plugin.requests.GetMap;
//import mindustry.plugin.utils.Utils;
//import mindustry.plugin.utils.Rank;
//import mindustry.type.Item;
//import mindustry.type.UnitType;
//import mindustry.world.Block;
//import mindustry.world.Tile;
//import org.javacord.api.entity.channel.Channel;
//import org.javacord.api.entity.channel.TextChannel;
//import org.javacord.api.entity.message.MessageBuilder;
//import org.javacord.api.entity.message.embed.EmbedBuilder;
//import org.json.JSONObject;
//
//import java.awt.*;
//import java.lang.reflect.Field;
//import java.math.BigDecimal;
//import java.time.Instant;
//import java.util.Arrays;
//import java.util.Collection;
//import java.util.HashMap;
//import java.util.Locale;
//import java.util.concurrent.CompletableFuture;
//import java.util.stream.IntStream;
//
//import static arc.util.Log.*;
//import static mindustry.Vars.*;
//import static mindustry.plugin.discord.DiscordLog.logAction;
//import static mindustry.plugin.ioMain.*;
//import static mindustry.plugin.requests.IPLookup.readJsonFromUrl;
//import static mindustry.plugin.utils.LogAction.*;
//import static mindustry.plugin.utils.Utils.Categories.management;
//import static mindustry.plugin.utils.Utils.Categories.moderation;
//import static mindustry.plugin.utils.Utils.*;
//import static mindustry.plugin.utils.ranks.Utils.listRanks;
//
//public class Moderator {
//    private final JSONObject data;
//    public GetMap map = new GetMap();
//    private Timer.Task testTask = null;
//    private boolean runningTask = false;
//    private int maxTps, avgTps, iterations;
//    private int minTps = Integer.MAX_VALUE;
//
//    public Moderator(JSONObject data) {
//        this.data = data;
//    }
//
//    public void scanTPS() {
//        if (runningTask) {
//            int tps = Core.graphics.getFramesPerSecond();
//            maxTps = Math.max(maxTps, tps);
//            minTps = Math.min(minTps, tps);
//            avgTps = (iterations * avgTps + tps) / (iterations + 1);
//            iterations++;
//        }
//        Core.app.post(this::scanTPS);
//    }
//
//    public void registerCommands(DiscordCommands handler) {
//        if (data.has("moderator_roleid")) {
//            long banRole = Strings.parseLong(data.getString("moderator_roleid"), 0);
////            handler.registerCommand(new RoleRestrictedCommand("admin") {
////                {
////                    help = "Toggle the admin status on a player.";
////                    roles = new long[] { banRole };
////                    usage = "<add|remove> <playerid|ip|name|teamid>";
////                    category = moderation;
////                    minArguments = 1;
////                }
////
////                public void run(Context ctx) {
////                    EmbedBuilder eb = new EmbedBuilder();
////
////
////                    if(!(ctx.args[1].equals("add") || ctx.args[1].equals("remove"))){
////                        err("Second parameter must be either 'add' or 'remove'.");
////                        return;
////                    }
////
////                    boolean add = ctx.args[1].equals("add");
////
////                    Administration.PlayerInfo target;
////                    Player playert = findPlayer(ctx.args[2]);
////                    if(playert != null){
////                        target = playert.getInfo();
////                    }else{
////                        target = netServer.admins.getInfoOptional(ctx.args[2]);
////                        playert = Groups.player.find(p -> p.getInfo() == target);
////                    }
////
////                    if(target != null){
////                        if(add){
//// netServer.admins.adminPlayer(target.id, target.adminUsid);
////                        }else{
//// netServer.admins.unAdminPlayer(target.id);
////                        }
////                        if(playert != null) playert.admin = add;
////                        eb.setTitle("Changed admin status of player: "+ escapeEverything(target.lastName));
////                        eb.setColor(Pals.success);
////                        info("Changed admin status of player: @", target.lastName);
////                    }else{
////                        eb.setTitle("Nobody with that name or ID could be found. If adding an admin by name, make sure they're online; otherwise, use their UUID.");
////                        eb.setColor(Pals.error);
////                        err("Nobody with that name or ID could be found. If adding an admin by name, make sure they're online; otherwise, use their UUID.");
////                    }
////                    ctx.sendMessage(eb);
////                }
////            });
//
//
//            handler.registerCommand(new RoleRestrictedCommand("pardon") {
//
//                {
//                    help = "Unvotekickban the specified player";
//                    usage = "<uuid>";
//                    roles = new long[] { banRole };
//                    category = moderation;
//                    minArguments = 1;
//                    hidden = true;
//                }
//
//                public void run(Context ctx) {
//                    EmbedBuilder eb = new EmbedBuilder();
//                    String target = ctx.args[1];
//                    Administration.PlayerInfo info = netServer.admins.getInfo(target);
//
//                    if (info != null) {
//                        info.lastKicked = 0;
//                        eb.setTitle("Command executed.");
//                        eb.setDescription("Pardoned `" + target + "` successfully.");
//                    } else {
//                        eb.setTitle("Command terminated.");
//                        eb.setColor(Pals.error);
//                        eb.setDescription("That ID isn't votekicked!");
//                    }
//                    ctx.sendMessage(eb);
//                }
//            });
//
//            handler.registerCommand(new RoleRestrictedCommand("convert") {
//                {
//                    help = "Change the provided player into a specific unit.";
//                    roles = new long[] { banRole };
//                    usage = "<playerid|ip|all|name|teamid> <unit> [team]";
//                    category = management;
//                    minArguments = 2;
//                }
//
//                public void run(Context ctx) {
//                    String target = ctx.args[1];
//                    String targetMech = ctx.args[2];
////                    Mech desiredMech = Mechs.alpha;
//                    UnitType desiredUnit;
//                    if (target.length() > 0 && targetMech.length() > 0) {
//                        try {
//                            Field field = UnitTypes.class.getDeclaredField(targetMech);
//                            desiredUnit = (UnitType) field.get(null);
//                        } catch (NoSuchFieldException | IllegalAccessException ignored) {
//                            StringBuilder sb = new StringBuilder("All available units: \n");
//                            for (Field b : UnitTypes.class.getDeclaredFields()) {
//                                sb.append("`").append(b.getName()).append("`,");
//                            }
//                            EmbedBuilder eb = new EmbedBuilder()
//                                    .setTitle("Can't find Unit " + targetMech + "!")
//                                    .setColor(new Color(0xff0000))
//                                    .setDescription(sb.toString());
//                            ctx.sendMessage(eb);
//                            return;
//                        }
//
//                        EmbedBuilder eb = new EmbedBuilder();
//
//                        if (target.equals("all")) {
//                            for (Player p : Groups.player) {
//                                Unit oldUnit = p.unit();
//                                p.unit(desiredUnit.spawn(p.team(), p.x, p.y));
//                                oldUnit.kill();
//                            }
//                            eb.setTitle("Command executed successfully.");
//                            eb.setDescription("Changed everyone's unit into " + desiredUnit.name);
//                            ctx.sendMessage(eb);
//                            return;
//                        } else if (target.matches("[0-9]+") && target.length() == 1) {
//                            for (Player p : Groups.player) {
//                                if (p.team().id == Byte.parseByte(target)) {
//                                    Unit oldUnit = p.unit();
//                                    p.unit(desiredUnit.spawn(p.team(), p.x, p.y));
//                                    oldUnit.kill();
//                                }
//                            }
//                            eb.setTitle("Command executed successfully.");
//                            eb.setDescription("Changed everyone's unit into " + desiredUnit.name);
//                            ctx.sendMessage(eb);
//                            return;
//                        }
//                        Player player = findPlayer(target);
//                        if (player != null) {
//                            Unit oldUnit = player.unit();
//                            player.unit(desiredUnit.spawn(player.team(), player.x, player.y));
//                            oldUnit.kill();
//                            eb.setTitle("Command executed successfully.");
//                            eb.setDescription("Changed " + escapeEverything(player.name) + "s unit into " + desiredUnit.name);
//                            ctx.sendMessage(eb);
//                        }
//                    }
//                }
//            });
//
//            handler.registerCommand(new RoleRestrictedCommand("team") {
//                {
//                    help = "Change the provided player's team into the provided one.";
//                    roles = new long[] { banRole };
//                    usage = "<playerid|ip|all|name|teamid> <team>";
//                    category = management;
//                    minArguments = 2;
//                }
//
//                public void run(Context ctx) {
//                    String target = ctx.args[1];
//                    String targetTeam = ctx.args[2];
//                    Team desiredTeam = Team.crux;
//                    EmbedBuilder eb = new EmbedBuilder();
//
//                    if (target.length() > 0 && targetTeam.length() > 0) {
//                        try {
//                            Field field = Team.class.getDeclaredField(targetTeam);
//                            desiredTeam = (Team) field.get(null);
//                        } catch (NoSuchFieldException | IllegalAccessException ignored) {
//                            if (isInt(targetTeam)) {
//                                desiredTeam = Team.get(Integer.parseInt(ctx.args[2]));
//                            } else {
//                                eb.setTitle("Command terminated");
//                                eb.setColor(Pals.error);
//                                eb.setDescription("Please select a valid team");
//                                ctx.sendMessage(eb);
//                                return;
//                            }
//                        }
//
//                        if (target.equals("all")) {
//                            for (Player p : Groups.player) {
//                                p.team(desiredTeam);
////     p.spawner = getCore(p.getTeam());
//                            }
//                            eb.setTitle("Command executed successfully.");
//                            eb.setDescription("Changed everyone's team to " + desiredTeam.name);
//                            ctx.sendMessage(eb);
//                            return;
//                        } else if (target.matches("[0-9]+") && target.length() == 1) {
//                            for (Player p : Groups.player) {
//                                if (p.team().id == Byte.parseByte(target)) {
//                                    p.team(desiredTeam);
////         p.spawner = getCore(p.getTeam());
//                                }
//                            }
//                            eb.setTitle("Command executed successfully.");
//                            eb.setDescription("Changed everyone's team to " + desiredTeam.name);
//                            ctx.sendMessage(eb);
//                            return;
//                        }
//                        Player player = findPlayer(target);
//                        if (player != null) {
//                            player.team(desiredTeam);
//// player.spawner = getCore(player.getTeam());
//                            eb.setTitle("Command executed successfully.");
//                            eb.setDescription("Changed " + escapeEverything(player.name) + "s team to " + desiredTeam.name);
//                            ctx.sendMessage(eb);
//                        } else {
//                            eb.setTitle("Command terminated");
//                            eb.setColor(Pals.error);
//                            eb.setDescription("Player " + escapeEverything(target) + " could not be found or is offline.");
//                            ctx.sendMessage(eb);
//                        }
//                    }
//                }
//            });
//
////            handler.registerCommand(new RoleRestrictedCommand("changeteamid") {
////                {
////                    help = "Change the provided player's team into a generated int.";
////                    roles = new long[] { banRole };
////                    usage = "<playerid|ip|all|name> <team>";
////                    category = management;
////                    minArguments = 2;
////                }
////
////                public void run(Context ctx) {
////                    String target = ctx.args[1];
////                    int targetTeam = Integer.parseInt(ctx.args[2]);
////                    if (target.length() > 0 && targetTeam > 0) {
////                        EmbedBuilder eb = new EmbedBuilder();
////
////                        if (target.equals("all")) {
//// for (Player p : Groups.player) {
////     p.team(Team.get(targetTeam));
//////     p.spawner = getCore(p.getTeam());
//// }
//// eb.setTitle("Command executed successfully.");
//// eb.setDescription("Changed everyone's team to " + targetTeam);
//// ctx.sendMessage(eb);
//// return;
////                        } else if (target.matches("[0-9]+") && target.length() == 1) {
//// for (Player p : Groups.player) {
////     if (p.team().id == Byte.parseByte(target)) {
////         p.team(Team.get(targetTeam));
//////         p.spawner = getCore(p.getTeam());
////     }
//// }
//// eb.setTitle("Command executed successfully.");
//// eb.setDescription("Changed everyone's team to " + targetTeam);
//// ctx.sendMessage(eb);
//// return;
////                        }
////                        Player player = findPlayer(target);
////                        if (player != null) {
//// player.team(Team.get(targetTeam));
//// eb.setTitle("Command executed successfully.");
//// eb.setDescription("Changed " + escapeEverything(player.name) + "s team to " + targetTeam);
//// ctx.sendMessage(eb);
////                        }
////                    }
////                }
////            });
//
//            handler.registerCommand(new RoleRestrictedCommand("rename") {
//                {
//                    help = "Rename the provided player";
//                    roles = new long[] { banRole };
//                    usage = "<playerid|ip|name> <name>";
//                    category = management;
//                    minArguments = 2;
//                    aliases.add("r");
//                }
//
//                public void run(Context ctx) {
//                    EmbedBuilder eb = new EmbedBuilder();
//                    String target = ctx.args[1];
//                    String name = ctx.message.substring(target.length() + 1);
//                    if (target.length() > 0 && name.length() > 0) {
//                        Player player = findPlayer(target);
//                        if (player != null) {
//                            player.name = name;
//// PersistentPlayerData tdata = ioMain.playerDataGroup.get(player.uuid);
//// if (tdata != null) tdata.origName = name;
//                            eb.setTitle("Command executed successfully");
//                            eb.setDescription("Changed name to " + escapeEverything(player.name));
//                            ctx.sendMessage(eb);
//                            Call.infoMessage(player.con, "[scarlet]Your name was changed by a moderator.");
//                        }
//                    }
//                }
//
//            });
//
//            handler.registerCommand(new RoleRestrictedCommand("motd") {
//                {
//                    help = "Change / set a welcome message";
//                    roles = new long[] { banRole };
//                    usage = "<newmessage>";
//                    category = management;
//                    minArguments = 1;
//                }
//
//                public void run(Context ctx) {
//                    EmbedBuilder eb = new EmbedBuilder();
//                    eb.setTitle("Command executed successfully");
//                    String message = ctx.message;
//                    if (message.length() > 0 && !message.equals("disable")) {
//                        welcomeMessage = message;
//                        Core.settings.put("welcomeMessage", message);
//                        Core.settings.autosave();
//                        eb.setDescription("Changed welcome message.");
//                        ctx.sendMessage(eb);
//                    } else {
//                        eb.setDescription("Disabled welcome message.");
//                        ctx.sendMessage(eb);
//                    }
//                }
//
//            });
//
//            handler.registerCommand(new RoleRestrictedCommand("edit") {
//                {
//                    help = "Change / set a message";
//                    roles = new long[] { banRole };
//                    usage = "<stats|rule|info> <new message>";
//                    category = management;
//                    minArguments = 2;
//                }
//
//                public void run(Context ctx) {
//                    String target = ctx.args[1].toLowerCase();
//                    switch (target) {
//                        case "stats", "s" -> {
//                            EmbedBuilder eb = new EmbedBuilder();
//                            eb.setTitle("Command executed successfully");
//                            String message = ctx.message.split(" ", 2)[1];
//                            if (message.length() > 0) {
//                                System.out.println("new stat message: " + message);
//                                statMessage = message;
//                                Core.settings.put("statMessage", message);
//                                Core.settings.autosave();
//                                eb.setDescription("Changed stat message.");
//                            } else {
//                                eb.setTitle("Command terminated");
//                                eb.setDescription("No message provided.");
//                            }
//                            ctx.sendMessage(eb);
//                        }
//                        case "rule", "r" -> {
//                            EmbedBuilder eb = new EmbedBuilder();
//                            eb.setTitle("Command executed successfully");
//                            String message = ctx.message.split(" ", 2)[1];
//                            if (message != null) {
//                                ruleMessage = message;
//                                Core.settings.put("ruleMessage", message);
//                                Core.settings.autosave();
//                                eb.setDescription("Changed rules.");
//                                eb.setColor(Pals.success);
//                            } else {
//                                eb.setTitle("Command terminated");
//                                eb.setDescription("No message provided.");
//                                eb.setColor(Pals.error);
//                            }
//                            ctx.sendMessage(eb);
//                        }
//                        case "info", "i" -> {
//                            EmbedBuilder eb = new EmbedBuilder();
//                            eb.setTitle("Command executed successfully");
//                            String message = ctx.message.split(" ", 2)[1];
//                            if (message.length() > 0) {
//                                System.out.println("new info message: " + message);
//                                infoMessage = message;
//                                Core.settings.put("infoMessage", message);
//                                Core.settings.autosave();
//                                eb.setDescription("Changed info message.");
//                            } else {
//                                eb.setTitle("Command terminated");
//                                eb.setDescription("No message provided.");
//                            }
//                            ctx.sendMessage(eb);
//                        }
//                        default -> {
//                            EmbedBuilder eb = new EmbedBuilder();
//                            eb.setTitle("Please select a message to edit!");
//                            eb.setColor(Pals.error);
//                            ctx.sendMessage(eb);
//                        }
//                    }
//                }
//            });
//
//            // idk why these commands are still here
//
////            handler.registerCommand(new RoleRestrictedCommand("statmessage") {
////                {
////                    help = "Change / set a stat message";
////                    roles = new long[] { banRole };
////                    usage = "<newmessage>";
////                    category = management;
////                }
////
////                public void run(Context ctx) {
////                    EmbedBuilder eb = new EmbedBuilder();
////                    eb.setTitle("Command executed successfully");
////                    String message = ctx.message;
////                    if (message.length() > 0) {
////                        statMessage = message;
////                        Core.settings.put("statMessage", message);
////                        Core.settings.autosave();
////                        eb.setDescription("Changed stat message.");
////                    } else {
////                        eb.setTitle("Command terminated");
////                        eb.setDescription("No message provided.");
////                    }
////                    ctx.sendMessage(eb);
////                }
////
////            });
//
////            handler.registerCommand(new RoleRestrictedCommand("reqMessage") {
////                {
////                    help = "Change / set a requirement Message";
////                    roles = new long[] { banRole };
////                    usage = "<newmessage>";
////                    category = management;
////                }
////
////                public void run(Context ctx) {
////                    EmbedBuilder eb = new EmbedBuilder();
////                    eb.setTitle("Command executed successfully");
////                    String message = ctx.message;
////                    if (message.length() > 0) {
////                        reqMessage = message;
////                        Core.settings.put("reqMessage", message);
////                        Core.settings.autosave();
////                        eb.setDescription("Changed reqMessage.");
////                    } else {
////                        eb.setTitle("Command terminated");
////                        eb.setDescription("No message provided.");
////                    }
////                    ctx.sendMessage(eb);
////                }
////
////            });
////
////            handler.registerCommand(new RoleRestrictedCommand("rankMessage") {
////                {
////                    help = "Change / set a rank Message";
////                    roles = new long[] { banRole };
////                    usage = "<newmessage>";
////                    category = management;
////                }
////
////                public void run(Context ctx) {
////                    EmbedBuilder eb = new EmbedBuilder();
////                    eb.setTitle("Command executed successfully");
////                    String message = ctx.message;
////                    if (message.length() > 0) {
////                        rankMessage = message;
////                        Core.settings.put("rankMessage", message);
////                        Core.settings.autosave();
////                        eb.setDescription("Changed rankMessage.");
////                    } else {
////                        eb.setTitle("Command terminated");
////                        eb.setDescription("No message provided.");
////                    }
////                    ctx.sendMessage(eb);
////                }
////
////            });
//
////            handler.registerCommand(new RoleRestrictedCommand("rulemessage") {
////                {
////                    help = "Change server rules. Use approriate prefix";
////                    roles = new long[] { banRole };
////                    category = management;
////                }
////
////                public void run(Context ctx) {
////                    EmbedBuilder eb = new EmbedBuilder();
////                    eb.setTitle("Command executed successfully");
////                    String message = ctx.message;
////                    if (message != null) {
////                        ruleMessage = message;
////                        Core.settings.put("ruleMessage", message);
////                        Core.settings.autosave();
////                        eb.setDescription("Changed rules.");
////                        eb.setColor(Pals.success);
////                    } else {
////                        eb.setTitle("Command terminated");
////                        eb.setDescription("No message provided.");
////                        eb.setColor(Pals.error);
////                    }
////                    ctx.sendMessage(eb);
////                }
////
////            });
//
//
//            handler.registerCommand(new RoleRestrictedCommand("spawn") {
//                {
//                    help = "Spawn x units at the location of the specified player";
//                    roles = new long[] { banRole };
//                    category = management;
//                    usage = "<playerid|ip|name> <unit> <amount>";
//                    minArguments = 3;
//                }
//
//                public void run(Context ctx) {
//                    EmbedBuilder eb = new EmbedBuilder();
//                    String target = ctx.args[1];
//                    String targetUnit = ctx.args[2];
//                    int amount = Integer.parseInt(ctx.args[3]);
//                    UnitType desiredUnit = UnitTypes.dagger;
//                    if (target.length() > 0 && targetUnit.length() > 0 && amount > 0 && amount < 1000) {
//                        try {
//                            Field field = UnitTypes.class.getDeclaredField(targetUnit);
//                            desiredUnit = (UnitType) field.get(null);
//                        } catch (NoSuchFieldException | IllegalAccessException ignored) {
//                        }
//
//                        Player player = findPlayer(target);
//                        if (player != null) {
//                            UnitType finalDesiredUnit = desiredUnit;
//                            IntStream.range(0, amount).forEach(i -> {
//                                Unit unit = finalDesiredUnit.create(player.team());
//                                unit.set(player.getX(), player.getY());
//                                unit.add();
//                            });
//                            eb.setTitle("Command executed successfully.");
//                            eb.setDescription("Spawned " + amount + " " + targetUnit + " near " + Utils.escapeEverything(player.name) + ".");
//                            ctx.sendMessage(eb);
//                        }
//                    } else {
//                        eb.setTitle("Command terminated");
//                        eb.setDescription("Invalid arguments provided.");
//                        eb.setColor(Pals.error);
//                        ctx.sendMessage(eb);
//                    }
//                }
//            });
//
//            handler.registerCommand(new RoleRestrictedCommand("killunits") {
//                {
//                    help = "Kills all units of the team of the specified player";
//                    roles = new long[] { banRole };
//                    category = management;
//                    usage = "<playerid|ip|name> <unit>";
//                    minArguments = 2;
//                }
//
//                public void run(Context ctx) {
//                    EmbedBuilder eb = new EmbedBuilder();
//                    String target = ctx.args[1];
//                    String targetUnit = ctx.args[2];
//                    UnitType desiredUnit = UnitTypes.dagger;
//                    if (target.length() > 0 && targetUnit.length() > 0) {
//                        try {
//                            Field field = UnitTypes.class.getDeclaredField(targetUnit);
//                            desiredUnit = (UnitType) field.get(null);
//                        } catch (NoSuchFieldException | IllegalAccessException ignored) {
//                        }
//
//                        Player player = findPlayer(target);
//                        if (player != null) {
//                            int amount = 0;
//                            for (Unit unit : Groups.unit) {
//                                if (unit.team == player.team()) {
//                                    if (unit.type == desiredUnit) {
//                                        unit.kill();
//                                        amount++;
//                                    }
//                                }
//                            }
//                            eb.setTitle("Command executed successfully.");
//                            eb.setDescription("Killed " + amount + " " + targetUnit + "s on team " + player.team());
//                            ctx.sendMessage(eb);
//                        }
//                    } else {
//                        eb.setTitle("Command terminated");
//                        eb.setDescription("Invalid arguments provided.");
//                        eb.setColor(Pals.error);
//                        ctx.sendMessage(eb);
//                    }
//                }
//            });
//
//            handler.registerCommand(new RoleRestrictedCommand("setblock") {
//                {
//                    help = "Create a block at the player's current location and on the player's current team.";
//                    roles = new long[] { banRole };
//                    usage = "<playerid|ip|name> <block> [rotation]";
//                    category = management;
//                    minArguments = 2;
//                    aliases.add("sb");
//                }
//
//                public void run(Context ctx) {
//                    String target = ctx.args[1];
//                    String targetBlock = ctx.args[2];
//                    int targetRotation = 0;
//                    if (ctx.args.length >= 4) {
//                        if (ctx.args[3] != null && !ctx.args[3].equals("")) {
//                            targetRotation = Integer.parseInt(ctx.args[3]);
//                        }
//                    }
//                    Block desiredBlock;
//
//                    try {
//                        Field field = Blocks.class.getDeclaredField(targetBlock);
//                        desiredBlock = (Block) field.get(null);
//                    } catch (NoSuchFieldException | IllegalAccessException ignored) {
//                        StringBuilder sb = new StringBuilder("All available blocks: \n");
//                        for (Field b : Blocks.class.getDeclaredFields()) {
//                            sb.append("`").append(b.getName()).append("`,");
//                        }
//                        EmbedBuilder eb = new EmbedBuilder()
//                                .setTitle("Can't find Block " + targetBlock + "!")
//                                .setColor(new Color(0xff0000))
//                                .setDescription(sb.toString());
//                        ctx.sendMessage(eb);
//                        return;
//                    }
//
//                    EmbedBuilder eb = new EmbedBuilder();
//                    Player player = findPlayer(target);
//
//                    if (player != null) {
//                        float x = player.getX();
//                        float y = player.getY();
//                        Tile tile = world.tileWorld(x, y);
//                        try {
//                            tile.setNet(desiredBlock, player.team(), targetRotation);
//                        } catch (Exception e) {
//                            eb.setTitle("There was an error trying to execute this command!");
//                            eb.setDescription("Error: " + e);
//                            eb.setColor(Pals.error);
//                            ctx.sendMessage(eb);
//                            return;
//                        }
//                        eb.setTitle("Command executed successfully.");
//                        eb.setDescription("Spawned " + desiredBlock.name + " on " + Utils.escapeEverything(player.name) + "'s position.");
//                    } else {
//                        eb.setTitle("Command terminated");
//                        eb.setDescription("Invalid arguments provided.");
//                        eb.setColor(Pals.error);
//                    }
//                    ctx.sendMessage(eb);
//                }
//            });
//
////            handler.registerCommand(new RoleRestrictedCommand("weaponmod") { // OH NO
////                {
////                    help = "<playerid|ip|name|all(oh no)> <bullet-type> <lifetime-modifier> <velocity-modifier> Mod the current weapon of a player.";
////                    roles = new long[] { banRole };
////                }
////
////                public void run(Context ctx) {
////                    EmbedBuilder eb = new EmbedBuilder();
////
////                    String target = ctx.args[1];
////                    String targetBullet = ctx.args[2];
////                    float targetL = Float.parseFloat(ctx.args[3]);
////                    float targetV = Float.parseFloat(ctx.args[4]);
////                    BulletType desiredBullet = null;
////
////                    if (target.length() > 0 && targetBullet.length() > 0) {
////                        try {
//// Field field = Bullets.class.getDeclaredField(targetBullet);
//// desiredBullet = (BulletType) field.get(null);
////                        } catch (NoSuchFieldException | IllegalAccessException ignored) {
////                        }
////
////                        if (target.equals("all")) {
//// for (Player p : Groups.player) {
////     if (player != null) { // what???    ...    how does this happen
////         try {
////             if (desiredBullet == null) {
////                 BulletType finalDesiredBullet = desiredBullet;
////                 Arrays.stream(player.unit().mounts).forEach(u -> u.bullet.type = finalDesiredBullet);
////                 Arrays.stream(player.unit().mounts).
////                         player.bt = null;
////             } else {
////                 player.bt = desiredBullet;
////                 player.sclLifetime = targetL;
////                 player.sclVelocity = targetV;
////             }
////         } catch (Exception ignored) {
////         }
////     }
//// }
//// eb.setTitle("Command executed");
//// eb.setDescription("Changed everyone's weapon mod. sorry. i dont know how to explain the rest");
//// ctx.sendMessage(eb);
////                        }
////
////                        Player player = findPlayer(target);
////                        if (player != null) {
//// if (desiredBullet == null) {
////     player.bt = null;
////     eb.setTitle("Command executed");
////     eb.setDescription("Reverted " + escapeCharacters(player.name) + "'s weapon to default.");
////     ctx.sendMessage(eb);
//// } else {
////     player.bt = desiredBullet;
////     player.sclLifetime = targetL;
////     player.sclVelocity = targetV;
////     eb.setTitle("Command executed");
////     eb.setDescription("Modded " + escapeCharacters(player.name) + "'s weapon to " + targetBullet + " with " + targetL + "x lifetime modifier and " + targetV + "x velocity modifier.");
////     ctx.sendMessage(eb);
//// }
////                        }
////                    } else {
////                        eb.setTitle("Command terminated");
////                        eb.setDescription("Invalid arguments provided.");
////                        eb.setColor(Pals.error);
////                        ctx.sendMessage(eb);
////                    }
////                }
////            });
//
//
//            handler.registerCommand(new RoleRestrictedCommand("js") {
//                {
//                    help = "Run a js command!";
//                    usage = "<code>";
//                    roles = new long[] { banRole };
//                    category = management;
//                    hidden = true;
//                    minArguments = 1;
//                }
//
//                public void run(Context ctx) {
//                    Core.app.post(() -> {
//                        EmbedBuilder eb = new EmbedBuilder();
//                        eb.setTitle("Command executed successfully!");
//                        System.out.println(ctx.message);
//                        eb.setDescription("Output: " + mods.getScripts().runConsole(ctx.message));
//                        ctx.sendMessage(eb);
//                    });
//                }
//            });
//
//            handler.registerCommand(new RoleRestrictedCommand("ip") {
//                {
//                    help = "Ip tools";
//                    usage = "<check|ban|unban> <ip> [reason]";
//                    category = moderation;
//                    roles = new long[] { banRole };
//                    minArguments = 2;
//                }
//
//                @Override
//                public void run(Context ctx) {
//                    String op = ctx.args[1];
//                    String targetIp = ctx.args[2];
//                    String reason = null;
//                    if (ctx.args.length > 3) {
//                        reason = ctx.message.split(" ", 3)[2];
//                    }
//                    EmbedBuilder eb = new EmbedBuilder();
//
//                    switch (op) {
//                        case "check", "c" -> {
//                            Seq<String> bans = netServer.admins.getBannedIPs();
//                            eb.setTitle(targetIp + (bans.contains(targetIp) ? "is" : "is not") + " banned");
//                        }
//                        case "ban", "b" -> {
//                            netServer.admins.banPlayerIP(targetIp);
//                            eb.setTitle("Banned " + targetIp)
//                                    .setColor(new Color(0xff0000));
//                            for (Player player : Groups.player) {
//                                if (netServer.admins.isIDBanned(player.uuid()) || netServer.admins.isIPBanned(player.uuid())) {
//                                    Call.sendMessage("[scarlet]" + player.name + " has been banned.");
//                                    player.con.kick(Packets.KickReason.banned);
//                                }
//                            }
//                            logAction(ipBan, ctx, reason, targetIp);
//                        }
//                        case "unban", "u", "ub" -> {
//                            if (netServer.admins.unbanPlayerIP(targetIp) || netServer.admins.unbanPlayerID(targetIp)) {
//                                eb.setTitle("Unbanned Ip " + targetIp)
//                                        .setColor(new Color(0x00ff00));
//                                info("Unbanned player: @", targetIp);
//                                logAction(ipUnban, ctx, reason, targetIp);
//                            } else {
//                                err("That IP is not banned!");
//                                eb.setTitle("That IP/ID is not banned!")
//                                        .setColor(new Color(0xff0000));
//                            }
//                        }
//                    }
//                    ctx.sendMessage(eb);
//                }
//            });
//
//            handler.registerCommand(new RoleRestrictedCommand("setstats") {
//                {
//                    help = "Change the player's statistics to the provided one.";
//                    usage = "<playerid|ip|name> <rank> <playTime> <buildingsBuilt> <gamesPlayed>";
//                    roles = new long[] { banRole };
//                    category = management;
//                    minArguments = 5;
//                }
//
//                public void run(Context ctx) {
//                    CompletableFuture.runAsync(() -> {
//                        EmbedBuilder eb = new EmbedBuilder();
//                        String target = ctx.args[1];
//                        int targetRank = Integer.parseInt(ctx.args[2]);
//                        int playTime = Integer.parseInt(ctx.args[3]);
//                        int buildingsBuilt = Integer.parseInt(ctx.args[4]);
//                        int gamesPlayed = Integer.parseInt(ctx.args[5]);
//                        if (target.length() > 0 && targetRank > -1) {
//// Player player = findPlayer(target);
//// if (player == null) {
////     eb.setTitle("Command terminated");
////     eb.setDescription("Player not found.");
////     eb.setColor(Pals.error);
////     ctx.sendMessage(eb);
////     return;
//// }
//
//                            Administration.PlayerInfo info = null;
//                            Player player = findPlayer(target);
//                            if (player != null) {
//                                info = netServer.admins.getInfo(player.uuid());
//                            } else {
//                                info = netServer.admins.getInfoOptional(target);
//                            }
//                            if (info == null) {
//                                playerNotFound(target, eb, ctx);
//                                return;
//                            }
//                            Database.Player pd = Database.getPlayerData(info.id);
//                            if (pd != null) {
//                                pd.buildingsBuilt = buildingsBuilt;
//                                pd.gamesPlayed = gamesPlayed;
//                                pd.playTime = playTime;
//                                pd.rank = targetRank;
//                                Database.setPlayerData(pd);
//                                eb.setTitle("Command executed successfully");
//                                eb.setDescription(String.format("Set stats of %s to:\nPlaytime: %d\nBuildings built: %d\nGames played: %d", escapeEverything(player.name), playTime, buildingsBuilt, gamesPlayed));
////     eb.setDescription("Promoted " + escapeCharacters(player.name) + " to " + targetRank);
//                                ctx.sendMessage(eb);
////     player.con.kick("Your rank was modified, please rejoin.", 0);
//                            } else {
//                                playerNotFound(target, eb, ctx);
//                                return;
//                            }
//
//                            if (targetRank == 6)
//                                netServer.admins.adminPlayer(player.uuid(), player.usid());
//                        }
//                    });
//                }
//
//            });
//
//        }
//    }
//}
