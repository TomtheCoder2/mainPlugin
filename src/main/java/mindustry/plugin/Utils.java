package mindustry.plugin;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Strings;
import com.google.gson.JsonArray;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import mindustry.net.Administration;
import mindustry.plugin.discordcommands.Command;
import mindustry.plugin.discordcommands.Context;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.awt.*;
import java.lang.reflect.Field;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;
import java.util.TimeZone;

import static mindustry.Vars.maps;
import static mindustry.Vars.netServer;
import static mindustry.plugin.ioMain.getTextChannel;
//import java.sql.*;

public class Utils {
    static String url = null;
    static String user = null;
    static String password = null;
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

        rankNames.put(1, new Rank("[accent]|[white]\uE865[accent]|[]", "Soldier", new Color(0xC0C3C4))); // private
        rankNames.put(2, new Rank("[accent]|[white]\uE861[accent]|[]", "Corporal", new Color(0x969A9D))); // general
        rankNames.put(3, new Rank("[accent]|[white]\uE826[accent]|[]", "Sargeant", new Color(0x717578))); // corporal
        rankNames.put(4, new Rank("[accent]|[white]\uE806[accent]|[]", "Major", new Color(0x515456))); // sargeant
        rankNames.put(5, new Rank("[accent]|[white]\uE810[accent]|[]", "Lieutenant", "Be decorated by General/Marshal", new Color(0x708374))); // pro player
        rankNames.put(6, new Rank("[accent]|[white]\uE811[accent]|[]", "Captain", "Create maps or code for the server", new Color(0x456F43))); // contributer
        rankNames.put(7, new Rank("[accent]|[white]\uE864[accent]|[]", "Colonel", "Apply at our discord server (Junior Mod)", new Color(0x405B32))); // mod jr
        rankNames.put(8, new Rank("[accent]|[white]\uE817[accent]|[]", "General", "Be decorated from Colonel", new Color(0x0A6216))); // mod
        rankNames.put(9, new Rank("[accent]|[white]\uE814[accent]|[]", "Marshal", "Be admin", new Color(0xffcc00))); // admin


        rankRequirements.put(1, new Requirement(1000, 3000, 5));
        rankRequirements.put(2, new Requirement(2500, 5000, 15));
        rankRequirements.put(3, new Requirement(10000, 15000, 25));
        rankRequirements.put(4, new Requirement(20000, 35000, 50));


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
                        .append(rankRequirements.get(entry.getKey()).playtime / 1000)
                        .append("k mins[white]/ [orange]")
                        .append(rankRequirements.get(entry.getKey()).gamesPlayed)
                        .append(" games[white]/ [yellow]")
                        .append(rankRequirements.get(entry.getKey()).buildingsBuilt / 1000).append(" built\n");
            }
        }
        return list.toString();
    }

    /**
     * Connect to the PostgreSQL Server
     */
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
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
                .replaceAll(" ", "")
                .replaceAll("<.*?>", "")
                .replaceAll("\\|(.*)\\|", "")
                .replaceAll("\\[accent\\]", ""));
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
        TextChannel log_channel = getTextChannel("882342315438526525");
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(action + " " + info.lastName);
        eb.addField("UUID", info.id, true);
        eb.addField("IP", info.lastIP, true);
        eb.addField("Banned by", ctx.author.getDiscriminatedName(), true);
        eb.addField("Reason", reason, true);
        log_channel.sendMessage(eb);
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
     * Get Data from a specific player
     *
     * @param uuid the uuid of the player
     */
    public static PlayerData getData(String uuid) {
//        System.out.println(uuid);
        // search for the uuid
        String SQL = "SELECT uuid, rank, playTime, buildingsBuilt, gamesPlayed, verified, banned, bannedUntil, banReason "
                + "FROM playerdata "
                + "WHERE uuid = ?";
        try {
            // connect to the database
            connect();
            Connection conn = connect();
            PreparedStatement pstmt = conn.prepareStatement(SQL);

            // replace ? with the uuid
            pstmt.setString(1, uuid);
            // get the result
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                // create a new Player to return
                PlayerData pd = new PlayerData(rs.getInt("rank"));

                // set all stats
                pd.uuid = rs.getString("uuid");
                pd.playTime = rs.getInt("playTime");
                pd.buildingsBuilt = rs.getInt("buildingsBuilt");
                pd.gamesPlayed = rs.getInt("gamesPlayed");
                pd.verified = rs.getBoolean("verified");
                pd.banned = rs.getBoolean("banned");
                pd.bannedUntil = rs.getLong("bannedUntil");
                pd.banReason = rs.getString("banReason");
                // finally return it
                return pd;
            } else {
                System.out.println(rs.next());
            }
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
        // if theres no player return null
        return null;
    }

    /**
     * Set Data for a specific player
     *
     * @param uuid uuid of the player
     * @param pd   player Data
     */
    public static void setData(String uuid, PlayerData pd) {
        if (getData(uuid) == null) {
            // define all variables
            String SQL = "INSERT INTO playerdata(uuid, rank, playTime, buildingsBuilt, gamesPlayed, verified, banned, bannedUntil, banReason) "
                    + "VALUES(?, ?,?, ?, ?, ?, ?, ?, ?)";

            long id = 0;

            try (Connection conn = connect();
                 PreparedStatement pstmt = conn.prepareStatement(SQL,
                         Statement.RETURN_GENERATED_KEYS)) {

                // set all variables
                pstmt.setString(1, uuid);
                pstmt.setInt(2, pd.rank);
                pstmt.setInt(3, pd.playTime);
                pstmt.setInt(4, pd.buildingsBuilt);
                pstmt.setInt(5, pd.gamesPlayed);
                pstmt.setBoolean(6, pd.verified);
                pstmt.setBoolean(7, pd.banned);
                pstmt.setLong(8, pd.bannedUntil);
                pstmt.setString(9, pd.banReason);

                // send the data
                int affectedRows = pstmt.executeUpdate();
                // check the affected rows
                if (affectedRows > 0) {
                    // get the ID back
                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            id = rs.getLong(1);
                        }
                    } catch (SQLException ex) {
                        System.out.println(ex.getMessage());
                    }
                }
            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        } else {
            String SQL = "UPDATE playerdata "
                    + "SET rank = ?, "
                    + "playTime = ?, "
                    + "buildingsBuilt = ?, "
                    + "gamesPlayed = ?, "
                    + "verified = ?, "
                    + "banned = ?, "
                    + "bannedUntil = ?, "
                    + "banReason = ? "
                    + "WHERE uuid = ?";

            int affectedrows = 0;

            try (Connection conn = connect();
                 PreparedStatement pstmt = conn.prepareStatement(SQL)) {

                // set all variables
                pstmt.setString(9, uuid);
                pstmt.setInt(1, pd.rank);
                pstmt.setInt(2, pd.playTime);
                pstmt.setInt(3, pd.buildingsBuilt);
                pstmt.setInt(4, pd.gamesPlayed);
                pstmt.setBoolean(5, pd.verified);
                pstmt.setBoolean(6, pd.banned);
                pstmt.setLong(7, pd.bannedUntil);
                pstmt.setString(8, pd.banReason);
//                System.out.println(pstmt);

                affectedrows = pstmt.executeUpdate();
//                System.out.println("affctected rows: " + affectedrows);

            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

    /**
     * get a ranking from the database
     */
    public static String ranking(int limit, String column) {
//        String SQL = "SELECT uuid, rank, playTime, buildingsBuilt, gamesPlayed, verified, banned, bannedUntil, banReason "
//                + "FROM playerdata "
//                + "WHERE uuid = ?";
        String SQL = "SELECT uuid, rank, playTime, buildingsBuilt, gamesPlayed, verified, banned, bannedUntil, banReason " +
                "FROM playerdata " +
                "ORDER BY " + column + " DESC LIMIT ?";
        try {
            StringBuilder rankingList = new StringBuilder("```");
            // connect to the database
            connect();
            Connection conn = connect();
            PreparedStatement pstmt = conn.prepareStatement(SQL);

            // replace ? with the uuid
//            pstmt.setString(1, column);
            pstmt.setInt(1, limit);
            System.out.println(pstmt);
            // get the result
            ResultSet rs = pstmt.executeQuery();
            int c = 0; // count
            while (rs.next()) {
                c++;
                // create a new Player to return
                PlayerData pd = new PlayerData(rs.getInt("rank"));

                // set all stats
                pd.uuid = rs.getString("uuid");
                pd.playTime = rs.getInt("playTime");
                pd.buildingsBuilt = rs.getInt("buildingsBuilt");
                pd.gamesPlayed = rs.getInt("gamesPlayed");
                rankingList.append(String.format("%-3d", c));
                Administration.PlayerInfo info = netServer.admins.getInfoOptional(pd.uuid);
                switch (column) {
                    case "playTime" -> {
                        rankingList.append(String.format("%-10d", pd.playTime));
                    }
                    case "buildingsBuilt" -> {
                        rankingList.append(String.format("%-10d", pd.buildingsBuilt));
                    }
                    case "gamesPlayed" -> {
                        rankingList.append(String.format("%-10d", pd.gamesPlayed));
                    }
                    default -> {
                        return "Please select a valid stat";
                    }
                }
                rankingList.append(" ").append(String.format("%-24s: ", pd.uuid));
                if (info != null) {
                    rankingList.append(escapeEverything(info.names.get(0)));
                }
                rankingList.append("\n");
            }
            rankingList.append("```");
            return rankingList.toString();
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
        return "Didnt find anything idk";
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