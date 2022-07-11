package mindustry.plugin.database;

import arc.struct.Seq;
import mindustry.plugin.utils.Utils;
//import mindustry.plugin.utils.Utils;

import java.sql.*;

import static arc.util.Log.debug;

public final class Database {
    /**
     * SQL connection. Should never be null after initialization.
     */
    private static Connection conn;

    /**
     * Connect to the PostgreSQL Server
     */
    public static void connect(String url, String user, String password) throws SQLException {
        conn = DriverManager.getConnection(url, user, password);
    }

    /**
     * Retrieves data for a given mindustry player, or null if not found.
     *
     * @param uuid the mindustry UUID of the player
     */
    public static Player getPlayerData(String uuid) {
        // search for the uuid
        String sql = "SELECT uuid, rank, playTime, buildingsBuilt, gamesPlayed, verified, banned, bannedUntil, banReason, discordLink"
                + "FROM playerdata "
                + "WHERE uuid = ?";
        try {
            debug("get player data of @, conn: @", uuid, conn.isClosed());
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
            debug(ex.getMessage());
        }

        return null;
    }

    /**
     * Retrieves the player data for a given discord user, or null if not found.
     *
     * @param id the discord ID of the player
     */
    public static Player getDiscordData(long id) {
        String sql = "SELECT uuid, rank, playTime, buildingsBuilt, gamesPlayed, verified, banned, bannedUntil, banReason, discordLink"
                + "FROM playerdata "
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
            debug(ex.getMessage());
        }

        return null;
    }

    /**
     * Set player data
     *
     * @param pd player Data
     */
    public static void setPlayerData(Player pd) {
        if (getPlayerData(pd.uuid) == null) {
            // define all variables
            String sql = "INSERT INTO playerdata(uuid, rank, playTime, buildingsBuilt, gamesPlayed, verified, banned, bannedUntil, banReason, discordLink) "
                    + "VALUES(?, ?,?, ?, ?, ?, ?, ?, ?, ?)";

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

                // send the data
                int affectedRows = pstmt.executeUpdate();
//                debug("affected rows: " + affectedrows);
            } catch (SQLException ex) {
                debug(ex.getMessage());
            }
        } else {
            String sql = "UPDATE playerdata "
                    + "SET rank = ?, "
                    + "playTime = ?, "
                    + "buildingsBuilt = ?, "
                    + "gamesPlayed = ?, "
                    + "verified = ?, "
                    + "banned = ?, "
                    + "bannedUntil = ?, "
                    + "banReason = ?, "
                    + "discordLink = ?, "
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
                pstmt.setString(10, pd.uuid);

                int affectedrows = pstmt.executeUpdate();
//                debug("affected rows: " + affectedrows);
            } catch (SQLException ex) {
                debug(ex.getMessage());
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
        String sql = "SELECT uuid, rank, ? " +
                "FROM playerdata " +
                "ORDER BY ? DESC LIMIT ? OFFSET ?";
        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, column);
            pstmt.setString(2, column);
            pstmt.setInt(3, limit);
            pstmt.setInt(4, offset);

            ResultSet rs = pstmt.executeQuery();
            Seq<PlayerRank> rankings = new Seq<>();
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                int stat = rs.getInt(column);
                rankings.add(new PlayerRank(uuid, stat));
            }
            return rankings.toArray(PlayerRank.class);
        } catch (SQLException ex) {
            debug(ex.getMessage());
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
        String sql = "SELECT name, ? " +
                "FROM mapdata " +
                "ORDER BY ? DESC LIMIT ? OFFSET ?";
        try {
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, column);
            pstmt.setString(2, column);
            pstmt.setInt(3, limit);
            pstmt.setInt(4, offset);

            ResultSet rs = pstmt.executeQuery();
            Seq<MapRank> ranking = new Seq<>();
            while (rs.next()) {
                String name = rs.getString("name");
                int stat = rs.getInt(column);
                ranking.add(new MapRank(name, stat));
            }
            return ranking.toArray(MapRank.class);
        } catch (SQLException ex) {
            debug(ex.getMessage());
        }
        return null;
    }

    /**
     * Retrieves map data for a given map
     */
    public static Map getMapData(String name) {
        name = Utils.escapeEverything(name).replaceAll("\\W", "");
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
            debug(ex.getMessage());
        }
        return null;
    }

    /**
     * Sets map data for a given map
     */
    public static void setMapData(Map md) {
        String name = Utils.escapeEverything(md.name).replaceAll("\\W", "");

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
                pstmt.executeUpdate();
            } catch (SQLException ex) {
                debug(ex.getMessage());
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
                pstmt.executeUpdate();
            } catch (SQLException ex) {
                debug(ex.getMessage());
            }
        }
    }

    public static class Player implements Cloneable {
        public String uuid;
        public int rank;

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
        }

        public static Player fromSQL(ResultSet rs) throws SQLException {
            String uuid = rs.getString("uuid");
            int rank = rs.getInt("rank");
            Player pd = new Player(uuid, rank);

            pd.verified = rs.getBoolean("verified");

            pd.playTime = rs.getInt("playTime");
            pd.buildingsBuilt = rs.getInt("buildingsBuilt");
            pd.gamesPlayed = rs.getInt("gamesPlayed");

            pd.banned = rs.getBoolean("banned");
            pd.bannedUntil = rs.getLong("bannedUntil");
            pd.banReason = rs.getString("banReason");

            pd.discord = rs.getLong("discordLink");

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
     * Value returned by `Database::rankMaps`
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

        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }
}
