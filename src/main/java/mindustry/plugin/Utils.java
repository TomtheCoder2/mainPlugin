package mindustry.plugin;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import mindustry.net.Administration;
import mindustry.plugin.database.MapData;
import mindustry.plugin.database.PlayerData;
import mindustry.plugin.discordcommands.Context;
import mindustry.world.Block;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.awt.*;
import java.lang.reflect.Field;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mindustry.Vars.maps;
import static mindustry.Vars.netServer;
import static mindustry.plugin.ioMain.getTextChannel;
import static mindustry.plugin.ioMain.log_channel_id;
import static mindustry.plugin.database.Utils.*;
//import java.sql.*;

public class Utils {
    public static String url = null;
    public static String maps_url = null;
    public static String apapi_key = null;
    public static String user = null;
    public static String password = null;
    public static int chatMessageMaxSize = 256;
    static String welcomeMessage = "";
    static String statMessage = "";
    static String reqMessage = "";
    static String rankMessage = "";
    static String ruleMessage = "";
    static String noPermissionMessage = "You don't have the required rank for this command. Learn more about ranks [pink]/info[]";
    // whether ip verification is in place (detect vpns, disallow their build rights)
    static Boolean verification = false;
    static String promotionMessage =
            """
                    [sky]%player%, you have been promoted to [sky]<%rank%>[]!
                    [#4287f5]You reached a playtime of - %playtime% minutes!
                    [#f54263]You played a total of %games% games!
                    [#9342f5]You built a total of %buildings% buildings!
                    [sky]Enjoy your time on the [white][#ff2400]P[#ff4900]H[#ff6d00]O[#ff9200]E[#ffb600]N[#ffdb00]I[#ffff00]X [white]Servers[sky]!""";
    public static HashMap<Integer, Rank> rankNames = new HashMap<>();
    public static HashMap<Integer, Requirement> rankRequirements = new HashMap<>();
    static HashMap<String, Integer> rankRoles = new HashMap<>();
    static Seq<String> bannedNames = new Seq<>();
    static Seq<String> onScreenMessages = new Seq<>();
    static Seq<Block> bannedBlocks = new Seq<>();
    static String eventIp = "";
    static int eventPort = 1001;

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


        rankRoles.put("627985513600516109", 1);
        rankRoles.put("636968410441318430", 2);
        rankRoles.put("674778262857187347", 3);
        rankRoles.put("624959361789329410", 4);

        bannedNames.add("IGGGAMES");
        bannedNames.add("CODEX");
        bannedNames.add("VALVE");
        bannedNames.add("tuttop");
        bannedNames.add("Volas Y0uKn0w1sR34Lp");
        bannedNames.add("IgruhaOrg");
        bannedNames.add("андрей");

        bannedBlocks.add(Blocks.conveyor);
        bannedBlocks.add(Blocks.titaniumConveyor);
        bannedBlocks.add(Blocks.junction);
        bannedBlocks.add(Blocks.router);

        statMessage = Core.settings.getString("statMessage");
        reqMessage = Core.settings.getString("reqMessage");
        rankMessage = Core.settings.getString("rankMessage");
        welcomeMessage = Core.settings.getString("welcomeMessage");
        ruleMessage = Core.settings.getString("ruleMessage");
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


    public static void logBanMessage(Administration.PlayerInfo info, Context ctx, String reason, String action) {
        TextChannel log_channel = getTextChannel(log_channel_id);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(action + " " + info.lastName);
        eb.addField("UUID", info.id, true);
        eb.addField("IP", info.lastIP, true);
        eb.addField("Banned by", ctx.author.getDiscriminatedName(), true);
        eb.addField("Reason", reason, true);
        log_channel.sendMessage(eb);
    }

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

    public static String rgbToString(float r, float g, float b) {
        String rs = Integer.toHexString((int) (r * 256));
        String gs = Integer.toHexString((int) (g * 256));
        String bs = Integer.toHexString((int) (b * 256));
        return rs + gs + bs;
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
}