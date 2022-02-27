package mindustry.plugin.database;

import mindustry.net.Administration;
import mindustry.plugin.data.PlayerData;

import java.sql.*;

import static arc.util.Log.debug;
import static mindustry.Vars.netServer;
import static mindustry.plugin.utils.Utils.*;

public class Utils {
    /**
     * Connect to the PostgreSQL Server
     */
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Get Data from a specific player
     *
     * @param uuid the uuid of the player
     */
    public static PlayerData getData(String uuid) {
//        debug(uuid);
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
                debug(rs.next());
            }
            conn.close();
        } catch (SQLException ex) {
            debug(ex.getMessage());
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
                        debug(ex.getMessage());
                    }
                }
                conn.close();
            } catch (SQLException ex) {
                debug(ex.getMessage());
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
//                debug(pstmt);

                affectedrows = pstmt.executeUpdate();
//                debug("affctected rows: " + affectedrows);
                conn.close();
            } catch (SQLException ex) {
                debug(ex.getMessage());
            }
        }
    }

    /**
     * get a ranking from the database
     */
    public static String ranking(int limit, String column, int offset, boolean showUUID) {
//        String SQL = "SELECT uuid, rank, playTime, buildingsBuilt, gamesPlayed, verified, banned, bannedUntil, banReason "
//                + "FROM playerdata "
//                + "WHERE uuid = ?";
        String SQL = "SELECT uuid, rank, playTime, buildingsBuilt, gamesPlayed, verified, banned, bannedUntil, banReason " +
                "FROM playerdata " +
                "ORDER BY " + column + " DESC LIMIT ? OFFSET ?";
        try {
            StringBuilder rankingList = new StringBuilder("```");
            // connect to the database
            connect();
            Connection conn = connect();
            PreparedStatement pstmt = conn.prepareStatement(SQL);

            // replace ? with the uuid
//            pstmt.setString(1, column);
            pstmt.setInt(1, limit);
            pstmt.setInt(2, offset);
//            debug(pstmt);
            // get the result
            ResultSet rs = pstmt.executeQuery();
            int c = 0; // count
            rankingList.append(String.format("%-3s", ""));
            rankingList.append(String.format("%-10s", column));
            if (showUUID) rankingList.append(String.format(" %-24sName:\n", "UUID"));
            else rankingList.append("Name:\n");
            while (rs.next()) {
                c++;
                // create a new Player to return
                PlayerData pd = new PlayerData(rs.getInt("rank"));

                // set all stats
                pd.uuid = rs.getString("uuid");
                pd.playTime = rs.getInt("playTime");
                pd.buildingsBuilt = rs.getInt("buildingsBuilt");
                pd.gamesPlayed = rs.getInt("gamesPlayed");
                rankingList.append(String.format("%-3d", c + offset));
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
                if (showUUID) rankingList.append(String.format(" %-24s: ", pd.uuid));
                if (info != null) {
                    rankingList.append(escapeEverything(info.names.get(0)));
                }
                rankingList.append("\n");
            }
            rankingList.append("```");
            conn.close();
            return rankingList.toString();
        } catch (SQLException ex) {
            debug(ex.getMessage());
        }
        return "Didnt find anything idk";
    }

    /**
     * get a ranking from the database
     */
    public static String ranking(int limit, String column, int offset) {
//        String SQL = "SELECT uuid, rank, playTime, buildingsBuilt, gamesPlayed, verified, banned, bannedUntil, banReason "
//                + "FROM playerdata "
//                + "WHERE uuid = ?";
        String SQL = "SELECT name, positiverating, negativerating, highscoretime, highscorewaves, playtime, shortestGame " +
                "FROM mapdata " +
                "ORDER BY " + column + " DESC LIMIT ? OFFSET ?";
        try {
            StringBuilder rankingList = new StringBuilder("```");
            // connect to the database
            connect();
            Connection conn = connect();
            PreparedStatement pstmt = conn.prepareStatement(SQL);

            // replace ? with the uuid
//            pstmt.setString(1, column);
            pstmt.setInt(1, limit);
            pstmt.setInt(2, offset);
//            debug(pstmt);
            // get the result
            ResultSet rs = pstmt.executeQuery();
            int c = 0; // count
            rankingList.append(String.format("%-3s", ""));
            rankingList.append(String.format("%-25s", "Name:"));
//            rankingList.append(column);
            rankingList.append("\n");
            while (rs.next()) {
                c++;
                // create a new mapdata to return
                MapData mapData = new MapData(rs.getString("name"));

                // set all stats
                mapData.negativeRating = rs.getInt("negativeRating");
                mapData.positiveRating = rs.getInt("positiveRating");

                rankingList.append(String.format("%-3d", c + offset));
                rankingList.append(String.format("%-25s", mapData.name));
                rankingList.append(String.format("%3d:%-3d", mapData.positiveRating, mapData.negativeRating));
//                switch (column) {
//                    case "negativeRating" -> {
//                        rankingList.append(String.format("%-3d", mapData.negativeRating));
//                    }
//                    case "positiveRating" -> {
//                        rankingList.append(String.format("%-3d", mapData.positiveRating));
//                    }
//                    default -> {
//                        return "Please select a valid stat";
//                    }
//                }
                rankingList.append("\n");
            }
            rankingList.append("```");
            conn.close();
            return rankingList.toString();
        } catch (SQLException ex) {
            debug(ex.getMessage());
        }
        return "Didnt find anything idk";
    }

    /**
     * get all infos about a specific map
     */
    public static MapData getMapData(String name) {
        name = escapeEverything(name).replaceAll("\\W", "");
        String SQL = "SELECT name, positiverating, negativerating, highscoretime, highscorewaves, playtime, shortestGame "
                + "FROM mapdata "
                + "WHERE name = ?";
        try {
            Connection conn = connect();// connect to the database
            PreparedStatement pstmt = conn.prepareStatement(SQL);

            // replace ? with the name
            pstmt.setString(1, name);
            // get the result
//            debug(pstmt);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                // create a new Player to return
                MapData mapData = new MapData(name);

                // set all stats
                mapData.positiveRating = rs.getInt("positiverating");
                mapData.negativeRating = rs.getInt("negativerating");
                mapData.highscoreTime = rs.getLong("highscoreTime");
                mapData.highscoreWaves = rs.getLong("highscoreWaves");
                mapData.playtime = rs.getLong("playtime");
                mapData.shortestGame = rs.getLong("shortestGame");

                // finally, return it
                return mapData;
            } else {
                debug(rs.next());
            }
            conn.close();
        } catch (SQLException ex) {
            debug(ex.getMessage());
        }
        // if theres no player return null
        return null;
    }

    /**
     * rate a map
     */
    public static void rateMap(String name, MapData mapdata) {
        name = escapeEverything(name).replaceAll("\\W", "");
        if (getMapData(name) == null) {
            // define all variables
            String SQL = "INSERT INTO mapdata(name, positiverating, negativerating, highscoretime, highscorewaves, playtime, shortestGame) "
//                    + "VALUES(" + name.replaceAll("\\W", "") + ", ?, ?, ?, ?, ?, ?)";
                    + "VALUES(?, ?, ?, ?, ?, ?, ?)";

//            long id = 0;

            try (Connection conn = connect();
                 PreparedStatement pstmt = conn.prepareStatement(SQL,
                         Statement.RETURN_GENERATED_KEYS)) {

                // set all variables
                pstmt.setString(1, name);
                pstmt.setInt(1 + 1, mapdata.positiveRating);
                pstmt.setInt(2 + 1, mapdata.negativeRating);
                pstmt.setLong(3 + 1, mapdata.highscoreTime);
                pstmt.setLong(4 + 1, mapdata.highscoreWaves);
                pstmt.setLong(5 + 1, mapdata.playtime);
                pstmt.setLong(6 + 1, mapdata.shortestGame);
//                CustomLog.debug(pstmt);

                // send the data
                int affectedRows = pstmt.executeUpdate();
//                debug(affectedRows);
//                // check the affected rows
//                if (affectedRows > 0) {
//                    // get the ID back
//                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
//                        if (rs.next()) {
//                            id = rs.getLong(1);
//                        }
//                    } catch (SQLException ex) {
//                        debug(ex.getMessage());
//                    }
//                }
                conn.close();
            } catch (SQLException ex) {
                debug(ex.getMessage());
            }
        } else {
            String SQL = "UPDATE mapdata SET " +
                    "positiverating = ?, " +
                    "negativerating = ?, " +
                    "highscoreTime = ?, " +
                    "highscoreWaves = ?, " +
                    "playtime = ?, " +
                    "shortestGame = ? " +
                    "WHERE name = ?";

            try (Connection conn = connect();
                 PreparedStatement pstmt = conn.prepareStatement(SQL)) {

//                debug(mapdata.name.replaceAll("\\W", ""));

                // set all variables
                pstmt.setInt(1, mapdata.positiveRating);
                pstmt.setInt(2, mapdata.negativeRating);
                pstmt.setLong(3, mapdata.highscoreTime);
                pstmt.setLong(4, mapdata.highscoreWaves);
                pstmt.setLong(5, mapdata.playtime);
                pstmt.setLong(6, mapdata.shortestGame);
                pstmt.setString(7, name);
//                debug(pstmt);
                pstmt.executeUpdate();
                //                debug("affctected rows: " + affectedrows);
                conn.close();
            } catch (SQLException ex) {
                debug(ex.getMessage());
            }
        }
    }
}
