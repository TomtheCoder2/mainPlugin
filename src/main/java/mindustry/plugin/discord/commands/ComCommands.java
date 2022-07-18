//package mindustry.plugin.discord.commands;
//
//import arc.Core;
//import arc.files.Fi;
//import arc.struct.LongSeq;
//import arc.util.Timer;
//import mindustry.Vars;
//import mindustry.content.Items;
//import mindustry.core.GameState;
//import mindustry.game.Team;
//import mindustry.game.Teams.TeamData;
//import mindustry.gen.Call;
//import mindustry.gen.Groups;
//import mindustry.gen.Player;
//import mindustry.io.SaveIO;
//import mindustry.maps.Map;
//import mindustry.mod.Mods;
//import mindustry.plugin.data.PersistentPlayerData;
//import mindustry.plugin.database.Database;
//import mindustry.plugin.discord.discordcommands.Command;
//import mindustry.plugin.discord.discordcommands.Context;
//import mindustry.plugin.discord.discordcommands.DiscordCommands;
//import mindustry.plugin.ioMain;
//import mindustry.plugin.requests.GetMap;
//import mindustry.plugin.utils.Rank;
//import mindustry.world.modules.ItemModule;
//import org.javacord.api.entity.message.MessageBuilder;
//import org.javacord.api.entity.message.embed.EmbedBuilder;
//import org.javacord.api.entity.permission.Role;
//
//import java.awt.*;
//import java.lang.reflect.Field;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.Locale;
//import java.util.Objects;
//
//import static arc.util.Log.debug;
//import static arc.util.Log.info;
//import static mindustry.Vars.saveExtension;
//import static mindustry.Vars.state;
//import static mindustry.plugin.ioMain.*;
//import static mindustry.plugin.utils.Utils.*;
//
//public class ComCommands {
////    public static ContentHandler contentHandler = new ContentHandler();
//
//    GetMap map = new GetMap();
//
//    public void registerCommands(DiscordCommands handler) {
//        handler.registerCommand(new Command("chat") {
//            {
//                help = "Sends a message to in-game chat.";
//                usage = "<message>";
//                minArguments = 1;
//                hidden = true;
//            }
//
//            public void run(Context ctx) {
//                if (ctx.event.isPrivateMessage()) return;
//
//                EmbedBuilder eb = new EmbedBuilder();
//                ctx.message = escapeCharacters(ctx.message);
//                if (ctx.message.length() < chatMessageMaxSize) {
//                    Call.sendMessage("[sky]" + ctx.author.getName() + " @discord >[] " + ctx.message);
//                    eb.setTitle("Command executed");
//                    eb.setDescription("Your message was sent successfully..");
//                    ctx.sendMessage(eb);
//                } else {
//                    ctx.reply("Message too big.");
//                }
//            }
//        });
//    }
//}