package mindustry.plugin.minimods;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Calendar;

import org.javacord.api.entity.message.embed.EmbedBuilder;

import arc.fx.filters.MotionBlurFilter;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Timer;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Query;
import mindustry.plugin.utils.Utils;

/** Automatically logs the amount of time that mods spend on the server each day */
public class ModLog implements MiniMod {
    @Override
    public void registerEvents() {
        Timer.schedule(() -> {
            for (Player p : Groups.player) {
                var pd = Database.getPlayerData(p.uuid());
                if (pd != null && pd.rank >= 9) {
                    int year = Calendar.getInstance().get(Calendar.YEAR);
                    int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
                    int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
                    ModDatabase.addMinutes(pd.uuid, (short)year, (short)month, (short)day, 1);
                }
            }
        }, 60f, 60f);
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("modlog", "<mod> <year> [month]", 
            data -> {
                data.help = "Query mod log entries";
                data.category = "Moderation";
                data.roles = new long[] { Roles.MOD, Roles.APPRENTICE, Roles.ADMIN };
            },
            ctx -> {
                var info = Query.findPlayerInfo(ctx.args.get("mod"));
                if (info == null) {
                    ctx.error("No such player", "That player is not in player info");
                    return;
                }

                ModDatabase.Entry[] entries;
                if (ctx.args.containsKey("month")) {
                    entries = ModDatabase.queryEntries(info.id, (short)ctx.args.getInt("year"), (short)ctx.args.getInt("month"));
                } else {
                    entries = ModDatabase.queryEntries(info.id, (short)ctx.args.getInt("year"));
                }

                StringBuilder sb = new StringBuilder();
                long total = 0;
                for (var entry : entries) {
                    sb.append(String.format("`%04d-%02d-%02d` %s\n", entry.year, entry.month, entry.day, formatMinutes(entry.minutes)));
                    total += entry.minutes;
                }

                ctx.sendEmbed(new EmbedBuilder()
                    .setColor(DiscordPalette.INFO)
                    .setTitle("Mod Log: " + Utils.escapeEverything(info.lastName))
                    .setDescription(sb.toString())
                    .addField("Total", formatMinutes(total))
                    .addField("Average", formatMinutes(total / entries.length))
                );
            }
        );
    }

    private static String formatMinutes(long minutes) {
        long hours = minutes / 60;
        minutes = minutes % 60;
        String time = "";
        if (hours > 0) {
            time += hours + "h";
        }
        time += minutes + "m";
        return time;
    }
}

class ModDatabase {
    public static void addMinutes(String uuid, short year, short month, short day, long minutes) {
        try {
            ResultSet rs;

            // Figure out whether an entry already exists
            {
                String findSQL = "SELECT minutes FROM modlog WHERE uuid = ? AND year = ? AND month = ? AND day = ?";
                var pstmt = Database.conn.prepareStatement(findSQL);
                pstmt.setString(1, uuid);
                pstmt.setShort(2, year);
                pstmt.setShort(3, month);
                pstmt.setShort(4, day);
                pstmt.setLong(5, minutes);
                rs = pstmt.executeQuery();
            }

            if (rs.next()) {
                long existingMinutes = rs.getLong("minutes");
                String sql = "UPDATE modlog SET minutes = ? WHERE uuid = ? AND year = ? AND month = ? AND day = ?";
                var pstmt = Database.conn.prepareStatement(sql);
                pstmt.setLong(1, minutes + existingMinutes);
                pstmt.setString(2, uuid);
                pstmt.setShort(3, year);
                pstmt.setShort(4, month);
                pstmt.setShort(5, day);
                pstmt.executeUpdate();
            } else {
                String sql = "INSERT INTO modlog VALUES (?, ?, ?, ?, ?)";
                var pstmt = Database.conn.prepareStatement(sql);
                pstmt.setString(1, uuid);
                pstmt.setShort(2, year);
                pstmt.setShort(3, month);
                pstmt.setShort(4, day);
                pstmt.setLong(5, minutes);
                pstmt.executeUpdate();                
            }
        } catch(SQLException e) {
            Log.err(e);
        }
    }

    public static class Entry {
        String uuid;
        short year;
        short month;
        short day;
        long minutes;

        public static Entry fromSQL(ResultSet rs) throws SQLException {
            Entry entry = new Entry();
            entry.uuid = rs.getString("uuid");
            entry.year = rs.getShort("year");
            entry.month = rs.getShort("month");
            entry.day = rs.getShort("day");
            entry.minutes = rs.getLong("minutes");
            return entry;
        }
    }

    public static Entry[] queryEntries(String uuid, short year, short month) {
        try {
            String sql = "SELECT * FROM modlog WHERE uuid = ? AND year = ? AND month = ?";
            var pstmt = Database.conn.prepareStatement(sql);
            pstmt.setString(1, uuid);
            pstmt.setShort(2, year);
            pstmt.setShort(3, month);
            var rs =pstmt.executeQuery();

            Seq<Entry> entries = new Seq<>();
            while (rs.next()) {
                entries.add(Entry.fromSQL(rs));            
            }

            return entries.toArray(Entry.class);
        } catch(SQLException e) {
            Log.err(e);
            return null;
        }
    }

    public static Entry[] queryEntries(String uuid, short year) {
        try {
            String sql = "SELECT * FROM modlog WHERE uuid = ? AND year = ?";
            var pstmt = Database.conn.prepareStatement(sql);
            pstmt.setString(1, uuid);
            pstmt.setShort(2, year);
            var rs =pstmt.executeQuery();

            Seq<Entry> entries = new Seq<>();
            while (rs.next()) {
                entries.add(Entry.fromSQL(rs));            
            }

            return entries.toArray(Entry.class);
        } catch(SQLException e) {
            Log.err(e);
            return null;
        }
    }
}
