package mindustry.plugin.utils;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.graphics.Pixmap;
import arc.graphics.PixmapIO;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.struct.StringMap;
import arc.util.Http;
import arc.util.Log;
import arc.util.Strings;
import arc.util.io.CounterInputStream;
import com.github.kevinsawicki.http.HttpRequest;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.SaveIO;
import mindustry.io.SaveVersion;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import mindustry.mod.Mods;
import mindustry.net.Administration;
import mindustry.plugin.data.PlayerData;
import mindustry.plugin.database.MapData;
import mindustry.plugin.discordcommands.Command;
import mindustry.plugin.discordcommands.Context;
import mindustry.plugin.ioMain;
import mindustry.type.ItemSeq;
import mindustry.type.ItemStack;
import mindustry.ui.Menus;
import mindustry.world.Block;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.emoji.KnownCustomEmoji;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.InflaterInputStream;

import static arc.util.Log.debug;
import static arc.util.Log.err;
import static mindustry.Vars.*;
import static mindustry.plugin.database.Utils.*;
import static mindustry.plugin.discordcommands.DiscordCommands.error_log_channel;
import static mindustry.plugin.ioMain.*;
//import java.sql.*;

public class Utils {
    public static String url = null;
    public static String maps_url = null;
    public static String apapi_key = null;
    public static String user = null;
    public static String password = null;
    public static int chatMessageMaxSize = 256;
    public static String welcomeMessage = "";
    public static String statMessage = "";
    public static String infoMessage = "";
    public static String reqMessage = "";
    public static String rankMessage = "";
    public static String ruleMessage = "";
    public static String noPermissionMessage = "You don't have the required rank for this command. Learn more about ranks [pink]/info[]";
    // whether ip verification is in place (detect vpns, disallow their build rights)
    public static Boolean verification = false;
    public static String promotionMessage =
            """
                    [sky]%player%, you have been promoted to [sky]<%rank%>[]!
                    [#4287f5]You reached a playtime of - %playtime% minutes!
                    [#f54263]You played a total of %games% games!
                    [#9342f5]You built a total of %buildings% buildings!
                    [sky]Enjoy your time on the [white][#ff2400]P[#ff4900]H[#ff6d00]O[#ff9200]E[#ffb600]N[#ffdb00]I[#ffff00]X [white]Servers[sky]!""";
    public static HashMap<Integer, Rank> rankNames = new HashMap<>();
    public static HashMap<Integer, Requirement> rankRequirements = new HashMap<>();
    public static HashMap<String, Integer> rankRoles = new HashMap<>();
    public static Seq<String> bannedNames = new Seq<>();
    public static Seq<String> onScreenMessages = new Seq<>();
    public static Seq<Block> bannedBlocks = new Seq<>();
    public static String eventIp = "";
    public static int eventPort = 1001;

    public static void init() {
//        "\uE816";
        // set all ranks
        rankNames.put(0, new Rank("[#7d7d7d][]", "Civilian", "The new combatants at the front", new Color(0xffffff)));
        // first try:
//        rankNames.put(1, new Rank("[accent]<[white]\uE802[accent]>[]", "private"));
//        rankNames.put(2, new Rank("[accent]<[white]\uE813[accent]>[]", "general"));
//        rankNames.put(3, new Rank("[accent]<[white]\uE824[accent]>[]", "sargent"));
//        rankNames.put(4, new Rank("[accent]<[white]\uE815[accent]>[]", "corporal"));
//        rankNames.put(5, new Rank("[accent]<[white]\uE819[accent]>[]", "pro"));
//        rankNames.put(6, new Rank("[accent]<[white]\uE809[accent]>[]", "contributor"));
//        rankNames.put(7, new Rank("[accent]<[white]\uE817[accent]>[]", "moderator"));
//        rankNames.put(8, new Rank("[accent]<[white][accent]>[]", "admin"));
        // second try:
//        rankNames.put(1, new Rank("[accent]<[white]\uE800[accent]>[] ", "newbie"));
//        rankNames.put(2, new Rank("[accent]<[white]\uE826[accent]>[] ", "active"));
//        rankNames.put(3, new Rank("[accent]<[white]\uE813[accent]>[] ", "veteran"));
//        rankNames.put(4, new Rank("[accent]<[white]\uE809[accent]>[] ", "map_creator:"));
//        rankNames.put(5, new Rank("[accent]<[white]\uE88E[accent]>[] ", "moderator_jr:"));
//        rankNames.put(6, new Rank("[accent]<[white]\uE82C[accent]>[] ", "moderator"));
        // third try:
        // ranks in normal form:
        /**
         * Private => Soldier
         * General => Corporal
         * Corporal => Sargeant
         * Sargeant => Major
         * Pro player => Lieutenant
         * Contributor => Captain
         * Mod Jr => Colonel
         * Mod => General
         * Admin => Marshal
         * */
        // icons:
        /**
         * -Soldier  (uE865)
         * -Corporal (uE861)
         * -Sargeant  (uE806)
         * -Major ()
         * -Lieutenant ()
         * -Captain (uE811)
         * -Colonel (uE864)
         * -General (uE817)
         * -Marshall (uE814)
         * */
        rankNames.put(1, new Rank("[accent]|[white]\uE865[accent]|[]", "Soldier", new Color(0xC0C3C4)));
        rankNames.put(2, new Rank("[accent]|[white]\uE861[accent]|[]", "Corporal", new Color(0x969A9D)));
        rankNames.put(3, new Rank("[accent]|[white]\uE861[accent]|[]", "Brigadier", new Color(0x84888B)));
        rankNames.put(4, new Rank("[accent]|[white]\uE826[accent]|[]", "Sargeant", new Color(0x717578)));
        rankNames.put(5, new Rank("[accent]|[white]\uE806[accent]|[]", "Major", new Color(0x515456)));
        rankNames.put(6, new Rank("[accent]|[white]\uE810[accent]|[]", "Lieutenant", "Be decorated by General/Marshal", new Color(0x708374)));
        rankNames.put(7, new Rank("[accent]|[white]\uE811[accent]|[]", "Captain", "Create maps or code for the server", new Color(0x456F43)));
        rankNames.put(8, new Rank("[accent]|[white]\uE864[accent]|[]", "Colonel", "Apply at our discord server (Junior Mod)", new Color(0x405B32)));
        rankNames.put(9, new Rank("[accent]|[white]\uE817[accent]|[]", "General", "Be decorated from Colonel", new Color(0x0A6216))); // mod
        rankNames.put(10, new Rank("[accent]|[white]\uE814[accent]|[]", "Marshal", "Be admin", new Color(0xffcc00))); // admin


        rankRequirements.put(1, new Requirement(300, 3000 * 2, 5));
        rankRequirements.put(2, new Requirement(600, 6000 * 2, 10));
        rankRequirements.put(3, new Requirement(1200, 12000 * 2, 20));
        rankRequirements.put(4, new Requirement(2400, 24000 * 2, 40));
        rankRequirements.put(5, new Requirement(4800, 48000 * 2, 80));


        rankRoles.put("897568732749115403", 1);
        rankRoles.put("881618465117581352", 2);
        rankRoles.put("906958402100535296", 3);
        rankRoles.put("897565215670042645", 4);
        rankRoles.put("900369018110738442", 5);
        rankRoles.put("900369102978310206", 6);

        bannedNames.add("IGGGAMES");
        bannedNames.add("CODEX");
        bannedNames.add("VALVE");
        bannedNames.add("tuttop");
        bannedNames.add("Volas Y0uKn0w1sR34Lp");
        bannedNames.add("IgruhaOrg");
        bannedNames.add("андрей");
        bannedNames.add("THIS IS MY KINGDOM CUM, THIS IS MY CUM");
        bannedNames.add("卐");
        bannedNames.add("cum");

        bannedBlocks.add(Blocks.conveyor);
        bannedBlocks.add(Blocks.titaniumConveyor);
        bannedBlocks.add(Blocks.junction);
        bannedBlocks.add(Blocks.router);

        statMessage = Core.settings.getString("statMessage");
        reqMessage = Core.settings.getString("reqMessage");
        rankMessage = Core.settings.getString("rankMessage");
        welcomeMessage = Core.settings.getString("welcomeMessage");
        ruleMessage = Core.settings.getString("ruleMessage");
        infoMessage = Core.settings.getString("infoMessage");
    }

    /**
     * Get a list of all ranks for the help page
     */
    public static String listRanks() {
        StringBuilder list = new StringBuilder();
        list.append("```java\n");
        for (var entry : rankNames.entrySet()) {
            list.append(entry.getKey()).append(": ").append(entry.getValue().name).append("\n");
        }
        list.append("```");
        return list.toString();
    }

    /**
     * list all ranks for the /ranks command
     */
    public static String inGameListRanks() {
        StringBuilder list = new StringBuilder("[accent]List of all ranks:\n");
        for (var entry : rankNames.entrySet()) {
            list.append(entry.getValue().tag).append(" [#").append(Integer.toHexString(rankNames.get(entry.getKey()).color.getRGB()).substring(2)).append("]").append(entry.getValue().name).append("\n");
        }
        list.append("\n[green]Type [sky]/req [green]to see the requirements for the ranks");
        return list.toString();
    }

    /**
     * show the requirements for the ranks
     */
    public static String listRequirements() {
        StringBuilder list = new StringBuilder("[accent]List of all requirements:\n");
        for (var entry : rankNames.entrySet()) {
            list.append("[#").append(Integer.toHexString(rankNames.get(entry.getKey()).color.getRGB()).substring(2)).append("]").append(entry.getValue().name).append(" ");
            if (entry.getValue().description != null) {
                list.append(" : [orange]").append(entry.getValue().description).append("\n");
            } else {
                list
                        .append(": [red]")
                        .append((rankRequirements.get(entry.getKey()).playtime >= 1000 ? rankRequirements.get(entry.getKey()).playtime / 1000 + "k" : rankRequirements.get(entry.getKey()).playtime))
                        .append(" mins[white]/ [orange]")
                        .append(rankRequirements.get(entry.getKey()).gamesPlayed)
                        .append(" games[white]/ [yellow]")
                        .append(rankRequirements.get(entry.getKey()).buildingsBuilt / 1000).append("k built\n");
            }
        }
        return list.toString();
    }


    /**
     * replace all color codes, ` and @
     *
     * @param string the original string
     */
    public static String escapeCharacters(String string) {
        return escapeColorCodes(string.replaceAll("`", "").replaceAll("@", ""));
    }

    /**
     * Remove all color codes
     *
     * @param string the input string (for example a name or a message)
     */
    public static String escapeColorCodes(String string) {
        return Strings.stripColors(string);
    }

    /**
     * remove everything (rank symbol colors etc.)
     *
     * @param string the player name (in most cases)
     */
    public static String escapeEverything(String string) {
        return escapeColorCodes(string
//                .replaceAll(" ", "")
                        .replaceAll("\\|(.)\\|", "")
                        .replaceAll("\\[accent\\]", "")
                        .replaceAll("\\|(.)\\|", "")
        ).replaceAll("\\|(.)\\|", "");
    }

    /**
     * check if a string is an ip
     *
     * @note ik there are functions for that, but I like to do it with regex
     */
    public static boolean isValidIPAddress(String ip) {

        // Regex for digit from 0 to 255.
        String zeroTo255
                = "(\\d{1,2}|(0|1)\\"
                + "d{2}|2[0-4]\\d|25[0-5])";

        // Regex for a digit from 0 to 255 and
        // followed by a dot, repeat 4 times.
        // this is the regex to validate an IP address.
        String regex
                = zeroTo255 + "\\."
                + zeroTo255 + "\\."
                + zeroTo255 + "\\."
                + zeroTo255;

        // Compile the ReGex
        Pattern p = Pattern.compile(regex);

        // If the IP address is empty
        // return false
        if (ip == null) {
            return false;
        }

        // Pattern class contains matcher() method
        // to find matching between given IP address
        // and regular expression.
        Matcher m = p.matcher(ip);

        // Return if the IP address
        // matched the ReGex
        return m.matches();
    }


    /**
     * remove everything (rank symbol colors etc.)
     *
     * @param player the player
     */
    public static String escapeEverything(Player player) {
        return escapeEverything(player.name);
    }

    /**
     * Get a map by name
     *
     * @param query the map name
     */
    public static Map getMapBySelector(String query) {
        Map found = null;
        try {
            // try by number
            found = maps.customMaps().get(Integer.parseInt(query));
        } catch (Exception e) {
            // try by name
            for (Map m : maps.customMaps()) {
                if (m.name().replaceAll(" ", "").toLowerCase().contains(query.toLowerCase().replaceAll(" ", ""))) {
                    found = m;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Find a player by name
     *
     * @param identifier the name, id, uuid, con or con.address
     */
    public static Player findPlayer(String identifier) {
        Player found = null;
        for (Player player : Groups.player) {
            if (player == null) return null; // how does that even happen wtf
            if (player.uuid() == null) return null;
            if (player.con == null) return null;
            if (player.con.address == null) return null;

            if (player.con.address.equals(identifier.replaceAll(" ", "")) ||
                    String.valueOf(player.id).equals(identifier.replaceAll(" ", "")) ||
                    player.uuid().equals(identifier.replaceAll(" ", "")) ||
                    escapeEverything(player).toLowerCase().replaceAll(" ", "").startsWith(identifier.toLowerCase().replaceAll(" ", ""))) {
                found = player;
            }
        }
        return found;
    }

    /**
     * get player info by uuid or name
     */
    public static Administration.PlayerInfo getPlayerInfo(String target) {
        Administration.PlayerInfo info;
        Player player = findPlayer(target);
        if (player != null) {
            info = netServer.admins.getInfo(player.uuid());
            System.out.println("Found " + player.name);
        } else {
            info = netServer.admins.getInfoOptional(target);
        }
        return info;
    }

    public static Mods.LoadedMod getMod(String name) {
        return mods.list().find(p -> escapeColorCodes(p.meta.name).equalsIgnoreCase(name) || escapeColorCodes(p.meta.displayName).equalsIgnoreCase(name));
    }

    public static boolean isInt(String str) {
        try {
            @SuppressWarnings("unused")
            int x = Integer.parseInt(str);
            return true; //String is an Integer
        } catch (NumberFormatException e) {
            return false; //String is not an Integer
        }

    }

    /**
     * Change the current map
     *
     * @param found map
     */
    public static void changeMap(Map found) {
        Class<Maps> mapsClass = Maps.class;
        Field mapsField;
        try {
            mapsField = mapsClass.getDeclaredField("maps");
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException("Could not find field 'maps' of class 'mindustry.maps.Maps'");
        }
        mapsField.setAccessible(true);
        Field mapsListField = mapsField;

        Seq<Map> mapsList;
        try {
            mapsList = (Seq<Map>) mapsListField.get(maps);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("unreachable");
        }

        Seq<Map> tempMapsList = mapsList.removeAll(map -> !map.custom || map != found);

        try {
            mapsListField.set(maps, tempMapsList);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("unreachable");
        }

        Events.fire(new EventType.GameOverEvent(Team.crux));

        try {
            mapsListField.set(maps, mapsList);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("unreachable");
        }
        maps.reload();
    }


    public static void sendSchem(Schematic schem, Context ctx) {
        ItemSeq req = schem.requirements();
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(0x00ffff))
                .setAuthor(ctx.author)
                .setTitle(schem.name());
        StringBuilder sb = new StringBuilder("");
        for (ItemStack item : req) {
            Collection<KnownCustomEmoji> emojis = api.getCustomEmojisByNameIgnoreCase(item.item.name.replaceAll("-", ""));
//            eb.addField(emoijs.iterator().next().getMentionTag(), String.valueOf(item.amount), true);
            sb.append(emojis.iterator().next().getMentionTag()).append(item.amount).append("    ");
        }
        eb.setDescription(schem.description());
        eb.addField("**Requirements:**", sb.toString());
        // power emojis
        String powerPos = api.getCustomEmojisByNameIgnoreCase("power_pos").iterator().next().getMentionTag();
        String powerNeg = api.getCustomEmojisByNameIgnoreCase("power_neg").iterator().next().getMentionTag();
        eb.addField("**Power:**", powerPos + "+" + schem.powerProduction() + "    " +
                powerNeg + "-" + schem.powerConsumption() + "     \n" +
                powerPos + "-" + powerNeg + (schem.powerProduction() - schem.powerConsumption()));

        // preview schem
        BufferedImage visualSchem;
        File imageFile;
        Fi schemFile;
        try {
            visualSchem = contentHandler.previewSchematic(schem);
            imageFile = new File("temp/" + "image_" + schem.name().replaceAll("[^a-zA-Z0-9\\.\\-]", "_") + ".png");
            ImageIO.write(visualSchem, "png", imageFile);
            // crate the .msch file
            schemFile = new Fi("temp/" + "file_" + schem.name().replaceAll("[^a-zA-Z0-9\\.\\-]", "_") + ".msch");
            Schematics.write(schem, schemFile);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        eb.setImage("attachment://" + imageFile.getName());
        MessageBuilder mb = new MessageBuilder();
        mb.addEmbed(eb);
        mb.addFile(imageFile);
        mb.addAttachment(schemFile.file());
        mb.send(ctx.channel);
    }


    public static boolean checkIfSchem(MessageCreateEvent event) {
        // check if it's a schem encoded in base64
        String message = event.getMessageContent();
        if (event.getMessageContent().startsWith("bXNjaA")) {
            try {
                debug("send schem");
                sendSchem(contentHandler.parseSchematic(message), new Context(event, null, null));
                event.deleteMessage();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // ========= check if it's a schem file ============
        // download all files
        Seq<MessageAttachment> ml = new Seq<>();
        Seq<MessageAttachment> txtData = new Seq<>();
        for (MessageAttachment ma : event.getMessageAttachments()) {
            if ((ma.getFileName().split("\\.", 2)[1].trim().equals("msch")) && !event.getMessageAuthor().isBotUser()) { // check if its a .msch file
                ml.add(ma);
            }
            if ((ma.getFileName().split("\\.", 2)[1].trim().equals("txt")) && !event.getMessageAuthor().isBotUser()) { // check if its a .msch file
                txtData.add(ma);
            }
        }

        if (ml.size > 0) {
            CompletableFuture<byte[]> cf = ml.get(0).downloadAsByteArray();
            try {
                byte[] data = cf.get();
                Schematic schem = Schematics.read(new ByteArrayInputStream(data));
                sendSchem(schem, new Context(event, null, null));
                return true;
            } catch (Exception e) {
                assert error_log_channel != null;
                error_log_channel.sendMessage(new EmbedBuilder().setTitle(e.getMessage()).setColor(new Color(0xff0000)));
                e.printStackTrace();
            }
        }

        if (txtData.size > 0) {
            CompletableFuture<byte[]> cf = txtData.get(0).downloadAsByteArray();
            try {
                byte[] data = cf.get();
                String base64Encoded = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)))
                        .lines().parallel().collect(Collectors.joining("\n"));
                Schematic schem = contentHandler.parseSchematic(base64Encoded);
                sendSchem(schem, new Context(event, null, null));
                event.deleteMessage();
                return true;
            } catch (Exception e) {
                assert error_log_channel != null;
                error_log_channel.sendMessage(new EmbedBuilder().setTitle(e.getMessage()).setColor(new Color(0xff0000)));
                e.printStackTrace();
            }
        }
        return false;
    }


    /**
     * Convert a long to formatted time.
     *
     * @param epoch the time in long.
     * @return formatted time
     */
    public static String epochToString(long epoch) {
        Date date = new Date(epoch * 1000L);
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
        return format.format(date) + " UTC";
    }


    public static String getKeyByValue(HashMap<String, Integer> map, Integer value) {
        for (java.util.Map.Entry<String, Integer> entry : map.entrySet()) {
            if (value.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Replace %player% with player name, %discord% with the discord link etc
     *
     * @param message the message to replace
     * @param player  for the stats
     */
    public static String formatMessage(Player player, String message) {
        try {
            message = message.replaceAll("%player%", escapeCharacters(player.name));
//            message = message.replaceAll("%map%", world.getMap().name());
//            message = message.replaceAll("%wave%", String.valueOf(state.wave));
            PlayerData pd = getData(player.uuid());
            if (pd != null) {
                message = message.replaceAll("%playtime%", String.valueOf(pd.playTime));
                message = message.replaceAll("%games%", String.valueOf(pd.gamesPlayed));
                message = message.replaceAll("%buildings%", String.valueOf(pd.buildingsBuilt));
                message = message.replaceAll("%rank%", rankNames.get(pd.rank).tag + " " + escapeColorCodes(rankNames.get(pd.rank).name));
//                if(pd.discordLink.length() > 0){
//                    User discordUser = api.getUserById(pd.discordLink).get(2, TimeUnit.SECONDS);
//                    if(discordUser != null) {
//                        message = message.replaceAll("%discord%", discordUser.getDiscriminatedName());
//                    }
//                } else{
                message = message.replaceAll("%discord%", "unlinked");
//                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        ;
        return message;
    }

    /**
     * log when a colonel+ bans a player
     */
    public static void logBanMessage(Administration.PlayerInfo info, Context ctx, String reason, String action) {
        TextChannel log_channel = getTextChannel(log_channel_id);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(action + " " + escapeEverything(info.lastName));
        eb.addField("UUID", info.id, true);
        eb.addField("IP", info.lastIP, true);
        eb.addField(action + " by", "<@" + ctx.author.getIdAsString() + ">", true);
        eb.addField("Reason", reason, true);
        log_channel.sendMessage(eb);
    }

    public static void tooFewArguments(Context ctx, Command command) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Too few arguments!")
                .setDescription("Usage: " + ioMain.prefix + command.name + " " + command.usage)
                .setColor(Pals.error);
        ctx.channel.sendMessage(eb);
    }

    /**
     * send the player not found message for discord commands
     */
    public static void playerNotFound(String name, EmbedBuilder eb, Context ctx) {
        eb.setTitle("Command terminated");
        eb.setDescription("Player `" + escapeEverything(name) + "` not found.");
        eb.setColor(Pals.error);
        ctx.channel.sendMessage(eb);
    }

    public static String hsvToRgb(double hue, float saturation, float value) {

        int h = (int) (hue * 6);
        float f = (float) (hue * 6 - h);
        float p = value * (1 - saturation);
        float q = value * (1 - f * saturation);
        float t = value * (1 - (1 - f) * saturation);

        return switch (h) {
            case 0 -> rgbToString(value, t, p);
            case 1 -> rgbToString(q, value, p);
            case 2 -> rgbToString(p, value, t);
            case 3 -> rgbToString(p, q, value);
            case 4 -> rgbToString(t, p, value);
            case 5 -> rgbToString(value, p, q);
            default -> throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + hue + ", " + saturation + ", " + value);
        };
    }

    /**
     * Get a png from map (InputStream)
     *
     * @param imageFileName the name of the image where you want to save it
     */
    public static void mapToPng(InputStream stream, String imageFileName) throws IOException {
        debug("start function mapToPng");
        Http.post(maps_url + "/map").content(stream, stream.available()).block(res -> {
            debug(res.getStatus());
            debug("received data (mapToPng)");
            var pix = new Pixmap(res.getResultAsStream().readAllBytes());
            PixmapIO.writePng(new Fi("temp/" + "image_" + imageFileName), pix); // Write to a file
            debug("image height: @", pix.height);
            debug("image width: @", pix.height);
        });
    }

    /**
     * Send a map to the mindServ to get a png
     *
     * @param map        map File
     * @param outputFile where the png should be saved
     * @return whether it was successfully
     */
    public static boolean getMapImage(File map, File outputFile) {
        try {
            HttpRequest req = HttpRequest.post(maps_url + "/map");
            req.contentType("application/octet-stream");
            req.send(map);

            if (req.ok()) {
                req.receive(outputFile);
                return true;
            }
            Log.warn("@:@", req.code(), req.body());
            return false;
        } catch (HttpRequest.HttpRequestException e) {
            Log.err("Content Server is not running.\n", e);
            return false;
        } catch (Exception e) {
            Log.err(e);
            return false;
        }
    }

    public static void attachMapPng(Map found, EmbedBuilder embed, Context ctx) throws IOException {
        Fi mapFile = found.file;
        attachMapPng(mapFile, embed, ctx);
    }

    public static void attachMapPng(Fi mapFile, EmbedBuilder embed, Context ctx) throws IOException {
        attachMapPng(mapFile, embed, ctx.channel);
    }

    public static void attachMapPng(Fi mapFile, EmbedBuilder embed, TextChannel channel) throws IOException {
        InputStream stream = mapFile.readByteStream();
        String imageFileName = mapFile.name().replaceAll("[^a-zA-Z0-9\\.\\-]", "_").replaceAll(".msav", ".png");
        Log.debug("File size of map: @", stream.readAllBytes().length);
        File imageFile = new File("temp/" + "image_" + imageFileName);
        MessageBuilder mb = new MessageBuilder();
        if (getMapImage(mapFile.file(), imageFile)) {
            debug("received image!");
            embed.setImage("attachment://" + "image_" + imageFileName);
            mb.addEmbed(embed);
            mb.addFile(imageFile);
            mb.addAttachment(mapFile.file());
            mb.send(channel).join();
            imageFile.delete();
        } else {
            mb.addEmbed(embed);
            mb.addAttachment(mapFile.file());
            mb.send(channel).join();
        }
    }

    /**
     * get meta data from a map saved as a file converted to an inputStream
     */
    public static StringMap getMeta(InputStream is) throws IOException {
        InputStream ifs = new InflaterInputStream(is);
        CounterInputStream counter = new CounterInputStream(ifs);
        DataInputStream stream = new DataInputStream(counter);

        SaveIO.readHeader(stream);
        int version = stream.readInt();
        SaveVersion ver = SaveIO.getSaveWriter(version);
        StringMap[] metaOut = {null};
        ver.region("meta", stream, counter, in -> metaOut[0] = ver.readStringMap(in));

        return metaOut[0];
    }

    public static String rgbToString(float r, float g, float b) {
        String rs = Integer.toHexString((int) (r * 256));
        String gs = Integer.toHexString((int) (g * 256));
        String bs = Integer.toHexString((int) (b * 256));
        return rs + gs + bs;
    }

    /**
     * copy a file to another
     */
    public static void copy(String path, Fi to) {
        try {
            final InputStream in = Utils.class.getClassLoader().getResourceAsStream(path);
            final OutputStream out = to.write();

            int data;
            while ((data = in.read()) != -1) {
                out.write(data);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


//    public static PlayerData getData(String uuid) {
//        try(Jedis jedis = ioMain.pool.getResource()) {
//            String json = jedis.get(uuid);
//            if(json == null) return null;
//
//            try {
//                return gson.fromJson(json, PlayerData.class);
//            } catch(Exception e){
//                e.printStackTrace();
//                return null;
//            }
//        }
//    }
//
//    public static void setData(String uuid, PlayerData pd) {
//        try(Jedis jedis = ioMain.pool.getResource()) {
//            try {
//                String json = gson.toJson(pd);
//                jedis.set(uuid, json);
//            } catch(Exception e){
//                e.printStackTrace();
//            }
//        }
//    }

    /**
     * get a channel by id
     */
    public static TextChannel getTextChannel(String id) {
        Optional<Channel> dc = api.getChannelById(id);
        if (dc.isEmpty()) {
            err("[ERR!] discordplugin: channel not found! " + id);
            return null;
        }
        Optional<TextChannel> dtc = dc.get().asTextChannel();
        if (dtc.isEmpty()) {
            err("[ERR!] discordplugin: textchannel not found! " + id);
            return null;
        }
        return dtc.get();
    }

    /**
     * log a list of connections in the discord log channel
     *
     * @param connection whether they joined or left
     */
    public static void logConnections(TextChannel log_channel, List<String> leftPlayers, String connection) {
        if (leftPlayers.size() > 0) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("Player " + connection + " Log");
            StringBuilder desc = new StringBuilder();
            for (String uuid : leftPlayers) {
//                if (player == null) continue;
                try {
                    Administration.PlayerInfo info = getPlayerInfo(uuid);
                    desc.append(String.format("`%s` : `%s `:%s\n", uuid, info.lastIP, escapeEverything(info.lastName)));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (Objects.equals(connection, "leave")) {
                eb.setColor(new Color(0xff0000));
            } else {
                eb.setColor(new Color(0x00ff00));
            }
            eb.setDescription(desc.toString());
            assert log_channel != null;
            log_channel.sendMessage(eb);
        }
        leftPlayers.clear();
    }

    /**
     * WIP
     */
    public static void execute(String command) throws IOException {
        Process process = Runtime.getRuntime().exec(command);
//        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//        String line = "";
////        StringBuilder output = new StringBuilder();
//        while ((line = reader.readLine()) != null) {
//            System.out.println(line);
////            output.append(line);
//        }
    }

    /**
     * WIP
     */
    public static void restartApplication() throws IOException, URISyntaxException {
        final String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        final File currentJar = new File(Core.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        /* is it a jar file? */
        if (!currentJar.getName().endsWith(".jar"))
            return;

        /* Build command: java -jar application.jar */
        final ArrayList<String> command = new ArrayList<String>();
//        command.add("screen -d -r v7 -X stuff $'");
//        command.add("sleep");
//        command.add("0\n");
//        command.add("screen");
//        command.add("-d");
//        command.add("-r");
//        command.add("v7");
//        command.add("-X");
//        command.add("stuff");
//        command.add("java -jar server-release.jar\n");
        command.add(javaBin);
        command.add("-jar");
        command.add(currentJar.getPath());
//        command.add("'");

        final ProcessBuilder builder = new ProcessBuilder(command);
        System.out.println(builder.command());
        builder.start();
        System.exit(0);
    }

    /**
     * create a rate menu for all players
     */
    public static void rateMenu() {
        String mapName = state.map.name();
        int id = Menus.registerMenu((player, selection) -> {
            if (selection == 0) {
                ratePositive(mapName, player);
            } else if (selection == 1) {
                rateNegative(mapName, player);
            }
        });
        Call.menu(id,
                "Rate this map! [pink]" + mapName,
                "Do you like this map? Vote [green]yes [white]or [scarlet]no:",
                new String[][]{
                        new String[]{"[green]Yes", "[scarlet]No"},
                        new String[]{"Close"}
                }
        );
    }

    /**
     * Create a menu to rate the current map for a player
     */
    public static void rateMenu(Player p) {
        String mapName = state.map.name();
        int id = Menus.registerMenu((player, selection) -> {
            if (selection == 0) {
                ratePositive(mapName, player);
            } else if (selection == 1) {
                rateNegative(mapName, player);
            }
        });
        Call.menu(p.con, id,
                "Rate this map! [pink]" + mapName,
                "Do you like this map? Vote [green]yes [white]or [scarlet]no:",
                new String[][]{
                        new String[]{"[green]Yes", "[scarlet]No"},
                        new String[]{"Close"}
                }
        );
    }

    /**
     * Rate a map positive
     */
    public static void rateNegative(String mapName, Player player) {
        MapData voteMapData = getMapData(mapName);
        if (voteMapData != null) {
            voteMapData.negativeRating++;
        } else {
            voteMapData = new MapData(mapName);
            voteMapData.negativeRating = 1;
        }
        rateMap(mapName, voteMapData);
        player.sendMessage("Successfully gave a [red]negative [white]feedback for " + mapName + "[white]!");
    }

    /**
     * Rate a map positive
     */
    public static void ratePositive(String mapName, Player player) {
        MapData voteMapData = getMapData(mapName);
        if (voteMapData != null) {
            voteMapData.positiveRating++;
        } else {
            voteMapData = new MapData(mapName);
            voteMapData.positiveRating = 1;
        }
        rateMap(mapName, voteMapData);
        player.sendMessage("Successfully gave a [green]positive [white]feedback for " + mapName + "[white]!");
    }

    /**
     * Send message without response handling
     *
     * @param user    User to dm
     * @param content message
     */
    public void sendMessage(User user, String content) {
        user.openPrivateChannel().join().sendMessage(content);
    }

    /**
     * create and save Ranks
     */
    public static class Rank {
        public String tag = "";
        public String name = "";
        public String description = null;
        public Color color = null;

        /**
         * Create a new rank
         *
         * @param t name tag (gets displayed before the player names starts, for example: <*>Nautilus
         * @param n name of the rank (for example: Moderator)
         */
        Rank(String t, String n, String desc, Color col) {
            this.tag = t;
            this.name = n;
            this.description = desc;
            this.color = col;
        }

        Rank(String t, String n, Color col) {
            this.tag = t;
            this.name = n;
            this.color = col;
        }

    }

    /**
     * Requirements for ranks
     */
    public static class Requirement {
        public int buildingsBuilt;
        public int gamesPlayed;
        public int playtime;

        public Requirement(int inputPlaytime, int inputBuildingsBuilt, int inputGamesPlayed) {
            this.playtime = inputPlaytime;
            this.buildingsBuilt = inputBuildingsBuilt;
            this.gamesPlayed = inputGamesPlayed;
        }
    }


//        public static getCore(Team team){
//        Tile[][] tiles = world.getTiles();
//        for (int x = 0; x < tiles.length; ++x) {
//            for(int y = 0; y < tiles[0].length; ++y) {
//                if (tiles[x][y] != null && tiles[x][y].entity != null) {
//                    TileEntity ent = tiles[x][y].ent();
//                    if (ent instanceof CoreBlock.CoreEntity) {
//                        if(ent.getTeam() == team){
//                            return (CoreBlock.CoreBuild) ent;
//                        }
//                    }
//                }
//            }
//        }
//        return null;
//    }

    // colors for errors, info, warning etc messages
    public static class Pals {
        public static Color warning = (Color.getHSBColor(5, 85, 95));
        public static Color info = (Color.getHSBColor(45, 85, 95));
        public static Color error = (Color.getHSBColor(3, 78, 91));
        public static Color success = (Color.getHSBColor(108, 80, 100));
    }

    public static class Categories {
        public static final String moderation = "Moderation";
        public static final String management = "Management";
        public static final String mapReviewer = "Map Reviewer";
    }

    public static class uMap {
        public String name, author, description;
        public ObjectMap<String, String> tags = new ObjectMap<>();
        public BufferedImage image;
        public BufferedImage terrain;
    }
}
