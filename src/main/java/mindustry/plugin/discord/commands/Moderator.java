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
//        }
//    }
//}
