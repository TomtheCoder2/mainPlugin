package mindustry.plugin.database;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.plugin.utils.Utils;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static mindustry.Vars.netServer;
import static mindustry.plugin.utils.Utils.escapeEverything;

public final class Database {
    /**
     * SQL connection. Should never be null after initialization.
     */
    public static Connection conn;
    public static Seq<String> bannedWords = new Seq<>();
    private static String playerTable;
    private static String authURL;
    private static String authUser;
    private static String authPassword;

    /**
     * Connect to the PostgreSQL Server
     */
    public static void connect(String url, String user, String password, String playerTable) throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (Exception e) {
            e.printStackTrace();
        }
        conn = DriverManager.getConnection(url, user, password);
        Database.playerTable = playerTable;

        authURL = url;
        authUser = user;
        authPassword = password;
    }

    /**
     * Reconnect, if the SQL connection was closed
     */
    private static void reconnect() {
        try {
            if (conn.isClosed()) {
                conn = DriverManager.getConnection(authURL, authUser, authPassword);
            }
        } catch (SQLException e) {
            Log.err("Error reconnnecting");
            e.printStackTrace();
        }
    }

    /**
     * Resets all phashes.
     *
     * @return the number of rows affected.
     */
    public static int resetPhashes() {
        reconnect();

        System.out.println("reset all hashes...");
        // search for the uuid
        String sql = "SELECT * "
                + "FROM " + playerTable + " ";
        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);

            ResultSet rs = pstmt.executeQuery();
            int n = 0;
            while (rs.next()) {
                Player pd = Player.fromSQL(rs);
                System.out.println("processing: " + pd.uuid);
                pd.phash = Utils.calculatePhash(pd.uuid);
                setPlayerData(pd);
                n += 1;
            }
            rs.close();
            return n;
        } catch (SQLException ex) {
//            //Log.debug(ex.getMessage());
            ex.printStackTrace();
            return 0;
        }
    }

    /**
     * Update bannedWords seq
     */
    public static void updateBannedWordsClient() {
        reconnect();
        bannedWords.clear();
        String sql = "SELECT * "
                + "FROM banned_words ";
        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                bannedWords.add(rs.getString("word"));
            }
            rs.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * update the database with local banned words
     */
    public static void updateBannedWordsDatabase() {
        reconnect();
        // insert all banned words
        // but with only one statement
        StringBuilder sql = new StringBuilder("INSERT INTO banned_words (word) VALUES ");
        for (String word : bannedWords) {
            sql.append("('").append(word).append("'), ");
        }
        sql = new StringBuilder(sql.substring(0, sql.length() - 2));
        sql.append(" ON CONFLICT DO NOTHING");
        try {
            PreparedStatement pstmt = conn.prepareStatement(sql.toString());
            pstmt.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public static void removeBannedWordDatabase(String[] words) {
        reconnect();
        StringBuilder sql = new StringBuilder("DELETE FROM banned_words WHERE word IN (");
        for (String word : words) {
            sql.append("'").append(word).append("', ");
        }
        sql = new StringBuilder(sql.substring(0, sql.length() - 2));
        sql.append(")");
        try {
            PreparedStatement pstmt = conn.prepareStatement(sql.toString());
            pstmt.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Retrieves data for a given mindustry player, or null if not found for the given UUID.
     *
     * @param uuid the mindustry UUID of the player
     */
    public static Player getPlayerData(String uuid) {
        reconnect();

        // search for the uuid
        String sql = "SELECT * "
                + "FROM " + playerTable + " "
                + "WHERE uuid = ?";
        try {
            //Log.debug("get player data of @, conn: @", uuid, conn.isClosed());
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, uuid);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Player pd = Player.fromSQL(rs);
                rs.close();
                return pd;
            }
            rs.close();
        } catch (SQLException ex) {
            //Log.debug(ex.getMessage());
        }

        return null;
    }

    /**
     * Retrieves data for a given mindustry player, or null if not found.
     *
     * @param phash the phash of the player
     */
    public static Player getPlayerDataByPhash(String phash) {
        reconnect();

        // search for the uuid
        String sql = "SELECT * "
                + "FROM " + playerTable + " "
                + "WHERE hid = ?";
        try {
            //Log.debug("get player data of @, conn: @", uuid, conn.isClosed());
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, phash);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Player pd = Player.fromSQL(rs);
                rs.close();
                return pd;
            }
            rs.close();
        } catch (SQLException ex) {
            //Log.debug(ex.getMessage());
        }

        return null;
    }

    /**
     * Retrieves the player data for a given discord user, or null if not found.
     *
     * @param id the discord ID of the player
     */
    public static Player getDiscordData(long id) {
        reconnect();

        String sql = "SELECT * "
                + "FROM " + playerTable + " "
                + "WHERE discordLink = ?";
        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setLong(1, id);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Player pd = Player.fromSQL(rs);
                rs.close();
                return pd;
            }
            rs.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        return null;
    }

    /**
     * Retrieves all players that are banned in any way
     */
    public static Player[] bans() {
        reconnect();

        String sql = "SELECT * FROM playerdata WHERE banned = true OR bannedUntil > " + Instant.now().getEpochSecond();
        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery();
            Seq<Player> players = new Seq<>();
            while (rs.next()) {
                players.add(Player.fromSQL(rs));
            }
            return players.toArray(Player.class);
        } catch (SQLException e) {
            //Log.err(e.getMessage());
            return null;
        }
    }

    // Save a name change
    public static void saveName(String uuid, String name) {
        try {
            reconnect();
            String sql = "INSERT INTO names (uuid, name) VALUES (?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, uuid);
            pstmt.setString(2, escapeEverything(name));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Save a list of names
    public static void saveNames(String uuid, String[] names) {
        try {
            reconnect();
            String sql = "INSERT INTO names (uuid, name) VALUES (?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            for (String name : names) {
                pstmt.setString(1, uuid);
                pstmt.setString(2, escapeEverything(name));
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Save a list of names for multiple players
    public static void saveNames(ObjectMap<String, Seq<String>> names) {
        try {
            reconnect();
            String sql = "INSERT INTO names (uuid, name) VALUES (?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            for (String uuid : names.keys()) {
                for (String name : names.get(uuid)) {
                    pstmt.setString(1, uuid);
                    pstmt.setString(2, escapeEverything(name));
                    pstmt.addBatch();
                }
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Get all names of a player
    public static Seq<String> getNames(String uuid) {
        try {
            reconnect();
            String sql = "SELECT name FROM names WHERE uuid = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, uuid);
            ResultSet rs = pstmt.executeQuery();
            Seq<String> names = new Seq<>();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
            return names;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Get all uuids of a name
    public static Seq<String> getUUIDs(String name) {
        try {
            reconnect();
            String sql = "SELECT uuid FROM names WHERE name ~* ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            Seq<String> uuids = new Seq<>();
            while (rs.next()) {
                uuids.add(rs.getString("uuid"));
            }
            return uuids;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Set player data
     *
     * @param pd player Data
     */
    public static void setPlayerData(Player pd) {
        reconnect();

        if (getPlayerData(pd.uuid) == null) {
            // define all variables
            String sql = "INSERT INTO " + playerTable + "(uuid, rank, playTime, buildingsBuilt, gamesPlayed, verified, banned, bannedUntil, banReason, discordLink, hid, subranks) "
                    + "VALUES(?, ?,?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                // set all variables
                pstmt.setString(1, pd.uuid);
                pstmt.setInt(2, pd.rank);
                pstmt.setInt(3, pd.playTime);
                pstmt.setInt(4, pd.buildingsBuilt);
                pstmt.setInt(5, pd.gamesPlayed);
                pstmt.setBoolean(6, pd.verified);
                pstmt.setBoolean(7, pd.banned);
                pstmt.setLong(8, pd.bannedUntil);
                pstmt.setString(9, pd.banReason);
                pstmt.setLong(10, pd.discord);
                pstmt.setString(11, pd.phash);
                pstmt.setArray(12, conn.createArrayOf("integer", pd.subranks.toArray()));

                // send the data
                int affectedRows = pstmt.executeUpdate();
                //Log.info("player insert affected rows: " + affectedRows);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } else {
            String sql = "UPDATE " + playerTable + " "
                    + "SET rank = ?, "
                    + "playTime = ?, "
                    + "buildingsBuilt = ?, "
                    + "gamesPlayed = ?, "
                    + "verified = ?, "
                    + "banned = ?, "
                    + "bannedUntil = ?, "
                    + "banReason = ?, "
                    + "discordLink = ?, "
                    + "hid = ?, "
                    + "subranks = ? "
                    + "WHERE uuid = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                // set all variables
                pstmt.setInt(1, pd.rank);
                pstmt.setInt(2, pd.playTime);
                pstmt.setInt(3, pd.buildingsBuilt);
                pstmt.setInt(4, pd.gamesPlayed);
                pstmt.setBoolean(5, pd.verified);
                pstmt.setBoolean(6, pd.banned);
                pstmt.setLong(7, pd.bannedUntil);
                pstmt.setString(8, pd.banReason);
                pstmt.setLong(9, pd.discord);
                pstmt.setString(10, pd.phash);
                pstmt.setArray(11, conn.createArrayOf("INTEGER", pd.subranks.toArray()));
                pstmt.setString(12, pd.uuid);

                int affectedrows = pstmt.executeUpdate();
                //Log.info("player update affected rows: " + affectedrows);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Return a player ranking from the database.
     *
     * @param column Either "playTime", "buildingsBuilt", or "gamesPlayed"
     * @return the ranking, or null if no players are found
     */
    public static PlayerRank[] rankPlayers(int limit, String column, int offset) {
        reconnect();

        if (!column.matches("[A-Za-z0-9]+")) return null;
        String sql = "SELECT uuid, " + column + " " +
                "FROM " + playerTable + " " +
                "ORDER BY " + column + " DESC LIMIT ? OFFSET ?";
        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, limit);
            pstmt.setInt(2, offset);

            ResultSet rs = pstmt.executeQuery();
            Seq<PlayerRank> rankings = new Seq<>();
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                int stat = rs.getInt(column);
                rankings.add(new PlayerRank(uuid, stat));
            }
            //Log.info("ranking: " + rankings.size);
            return rankings.toArray(PlayerRank.class);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

        return null;
    }

    /**
     * Rank all maps based on {@link Database.Map#positiveRating positiveRating} - {@link Database.Map#negativeRating negativeRating}
     */
    public static Map[] rankMapRatings(int limit, int offset) {
        reconnect();

        String sql = "SELECT * FROM mapdata ORDER BY positiverating DESC LIMIT ? OFFSET ?";
        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, limit);
            pstmt.setInt(2, offset);

            ResultSet rs = pstmt.executeQuery();
            Seq<Map> maps = new Seq<>();
            while (rs.next()) {
                Map map = Map.fromSQL(rs);
                maps.add(map);
            }
            maps.sort(m -> (float) m.positiveRating / (float) m.negativeRating);
            return maps.toArray(Map.class);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Retrieve a ranking of maps.
     *
     * @param column Either "positiveRating" or "negativeRating"
     * @return the ranking, or null if none are found
     */
    public static MapRank[] rankMaps(int limit, String column, int offset) {
        reconnect();

        if (!column.matches("[A-Za-z0-9]+")) return null;
        String sql = "SELECT name, " + column + " " +
                "FROM mapdata " +
                "ORDER BY " + column + " DESC LIMIT ? OFFSET ?";
        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, limit);
            pstmt.setInt(2, offset);

            ResultSet rs = pstmt.executeQuery();
            Seq<MapRank> ranking = new Seq<>();
            while (rs.next()) {
                String name = rs.getString("name");
                int stat = rs.getInt(column);
                ranking.add(new MapRank(name, stat));
            }
            return ranking.toArray(MapRank.class);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Retrieves map data for a given map
     */
    public static Map getMapData(String name) {
        reconnect();

        name = escapeEverything(name).replaceAll("\\W", "");
        String sql = "SELECT name, positiverating, negativerating, highscoretime, highscorewaves, playtime, shortestGame "
                + "FROM mapdata "
                + "WHERE name = ?";
        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, name);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Map.fromSQL(rs);
            } else {
                return null;
            }
        } catch (SQLException ex) {
            //Log.debug(ex.getMessage());
        }
        return null;
    }

    /**
     * Sets map data for a given map
     */
    public static void setMapData(Map md) {
        reconnect();

        String name = escapeEverything(md.name).replaceAll("\\W", "_");
        //Log.info("setting map data for " + name);
        if (getMapData(name) == null) {
            String sql = "INSERT INTO mapdata(name, positiverating, negativerating, highscoretime, highscorewaves, playtime, shortestGame) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try {
                PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setString(1, name);
                pstmt.setInt(1 + 1, md.positiveRating);
                pstmt.setInt(2 + 1, md.negativeRating);
                pstmt.setLong(3 + 1, md.highscoreTime);
                pstmt.setLong(4 + 1, md.highscoreWaves);
                pstmt.setLong(5 + 1, md.playTime);
                pstmt.setLong(6 + 1, md.shortestGame);
                int rows = pstmt.executeUpdate();
                //Log.info("map insert rows: " + rows);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } else {
            String sql = "UPDATE mapdata SET " +
                    "positiverating = ?, " +
                    "negativerating = ?, " +
                    "highscoreTime = ?, " +
                    "highscoreWaves = ?, " +
                    "playtime = ?, " +
                    "shortestGame = ? " +
                    "WHERE name = ?";

            try {
                PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setInt(1, md.positiveRating);
                pstmt.setInt(2, md.negativeRating);
                pstmt.setLong(3, md.highscoreTime);
                pstmt.setLong(4, md.highscoreWaves);
                pstmt.setLong(5, md.playTime);
                pstmt.setLong(6, md.shortestGame);
                pstmt.setString(7, name);
                int rows = pstmt.executeUpdate();
                //Log.info("map update rows: " + rows);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    // go through all players and save all their names
    public static int saveAllNames() {
        AtomicInteger n = new AtomicInteger();
        // start new thread
        new Thread(() -> {
            reconnect();

            System.out.println("save all names...");
            // search for the uuid
            String sql = "SELECT * "
                    + "FROM " + playerTable + " ORDER BY uuid DESC;";
            try {
                PreparedStatement pstmt = conn.prepareStatement(sql);

                ResultSet rs = pstmt.executeQuery();

                ObjectMap<String, Seq<String>> data = new ObjectMap<>();
                while (rs.next()) {
                    Player pd = Player.fromSQL(rs);
//                System.out.println("processing: " + pd.uuid);
                    var info = netServer.admins.getInfo(pd.uuid);
                    if (info == null) {
                        System.out.println("no info found for " + pd.uuid);
                        continue;
                    }

                    StringBuilder sb = new StringBuilder(pd.uuid + ": names: ");
                    for (var name : info.names) {
                        if (name != null && !name.equals("")) {
                            sb.append(name).append(", ");
//                        saveName(pd.uuid, name);
                            data.put(pd.uuid, info.names);
                        }
                    }
//                System.out.println(sb);
                    if (n.get() % 100 == 0) {
                        System.out.println("processed " + n + " players");
                    }
                    if (n.get() % 1000 == 0) {
                        System.out.println("saving names...");
                        saveNames(data);
                        data.clear();
                    }
                    n.getAndIncrement();
                }
                System.out.println("saving names...");
                saveNames(data);
                rs.close();
                // and then delete all duplicates with this command
                sql = "DELETE FROM\n" +
                        "    names a\n" +
                        "    USING names b\n" +
                        "WHERE\n" +
                        "        a.id > b.id\n" +
                        "  AND a.uuid = b.uuid AND a.name = b.name;";
                pstmt = conn.prepareStatement(sql);
                pstmt.executeUpdate();

            } catch (SQLException ex) {
//            //Log.debug(ex.getMessage());
                ex.printStackTrace();
            }
        }).start();
        return n.get();
    }

    public static class Player implements Cloneable {
        public String uuid;

        /**
         * The Phash of the player. This should always be equal to {@code Utils.calculatePhash(uuid)}
         */
        public String phash;
        public int rank;
        public ArrayList<Integer> subranks;

        /**
         * For VPN-checking
         */
        public boolean verified = false;

        // stats
        /**
         * Represents play time, in minutes
         */
        public int playTime = 0;
        public int buildingsBuilt = 0;
        public int gamesPlayed = 0;

        // banned
        public boolean banned = false;
        /**
         * In seconds. `Instant.now().getEpochSecond()`
         */
        public long bannedUntil = 0;
        public String banReason = "";

        /**
         * The ID of the corresponding discord user
         */
        public long discord;

        public Player(String uuid, int rank) {
            this.uuid = uuid;
            this.rank = rank;
            this.phash = Utils.calculatePhash(uuid);
            this.subranks = new ArrayList<>();
        }

        public Player(String uuid, int rank, String phash) {
            this.uuid = uuid;
            this.rank = rank;
            this.phash = phash;
            this.subranks = new ArrayList<>();
        }

        public static Player fromSQL(ResultSet rs) throws SQLException {
            String uuid = rs.getString("uuid");
            int rank = rs.getInt("rank");
            String phash = rs.getString("hid");
            Player pd = new Player(uuid, rank, phash);

            pd.verified = rs.getBoolean("verified");

            pd.playTime = rs.getInt("playTime");
            pd.buildingsBuilt = rs.getInt("buildingsBuilt");
            pd.gamesPlayed = rs.getInt("gamesPlayed");

            pd.banned = rs.getBoolean("banned");
            pd.bannedUntil = rs.getLong("bannedUntil");
            pd.banReason = rs.getString("banReason");

            if (rs.getBytes("discordLink") != null && rs.getBytes("discordLink").length != 0) {
                pd.discord = rs.getLong("discordLink");
            }

            // retrieve subranks
            pd.subranks = rs.getArray("subranks") == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList((Integer[]) rs.getArray("subranks").getArray()));

            return pd;
        }

        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    /**
     * Value returned by `Database::rankPlayers`
     */
    public static class PlayerRank {
        public String uuid;
        /**
         * playTime, buildingsBuilt, or gamesPlayed
         */
        public int stat;

        public PlayerRank(String uuid, int stat) {
            this.uuid = uuid;
            this.stat = stat;
        }
    }

    /**
     * Value returned by {@link #rankMaps(int, String, int) rankMaps()}
     */
    public static class MapRank {
        /**
         * Map name
         */
        public String name;
        /**
         * Either positiveRating or negativeRating
         */
        public int stat;

        public MapRank(String name, int stat) {
            this.name = name;
            this.stat = stat;
        }
    }

    /**
     * Information relating to maps stored in the database
     */
    public static class Map implements Cloneable {
        /**
         * name varchar,
         * positiveRating int,
         * negativeRating int,
         * highscore     bigint,
         * playtime      bigint
         */

        /**
         * Name of the map
         */
        public String name;
        public int positiveRating = 0;
        public int negativeRating = 0;
        public long highscoreTime = 0;
        public long highscoreWaves = 0;
        public long shortestGame = 0;
        public long playTime = 0;

        public Map(String name) {
            this.name = name;
        }

        public static Map fromSQL(ResultSet rs) throws SQLException {
            Map md = new Map(rs.getString("name"));
            md.positiveRating = rs.getInt("positiverating");
            md.negativeRating = rs.getInt("negativerating");
            md.highscoreTime = rs.getLong("highscoreTime");
            md.highscoreWaves = rs.getLong("highscoreWaves");
            md.playTime = rs.getLong("playtime");
            md.shortestGame = rs.getLong("shortestGame");
            return md;
        }

        public int overallRating() {
            return positiveRating - negativeRating;
        }

        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }
}
