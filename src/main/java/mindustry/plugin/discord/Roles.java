package mindustry.plugin.discord;

import org.javacord.api.DiscordApi;
import org.json.JSONObject;


public class Roles {
    public static long MOD;
    public static long ADMIN;
    public static long MAP_SUBMISSIONS;
    public static long APPRENTICE;
    public static long APPEAL;
    public static long DONATOR;
    public static long ACTIVE_PLAYER;
    public static long DEV;
    public static long MVP;
    /** Report investigator */
    public static long RI;

    public static void load(DiscordApi api, JSONObject data) {
        ADMIN = Long.parseLong(data.getString("admin"));
        MOD = Long.parseLong(data.getString("mod"));
        MAP_SUBMISSIONS = Long.parseLong(data.getString("map_submissions"));
        APPRENTICE = Long.parseLong(data.getString("apprentice"));
        APPEAL = Long.parseLong(data.getString("appeal"));
        DONATOR = Long.parseLong(data.getString("donator"));
        ACTIVE_PLAYER = Long.parseLong(data.getString("active_player"));
        MVP = Long.parseLong(data.getString("mvp"));
        DEV = Long.parseLong(data.getString("dev"));
        RI = Long.parseLong(data.getString("ri"));
    }
}
