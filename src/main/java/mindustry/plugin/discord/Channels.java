package mindustry.plugin.discord;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.json.JSONObject;

public class Channels {
    /** Channel for live chat */
    public static TextChannel CHAT;


    public static TextChannel WARNINGS;
    public static TextChannel APPEAL;
    public static TextChannel BUG_REPORT;
    public static TextChannel GR_REPORT;

    public static TextChannel MAP_SUBMISSIONS;
    public static TextChannel MAP_RATING;

    public static TextChannel LOG;
    public static TextChannel ERROR_LOG;

    public static TextChannel BOT;
    public static TextChannel STAFF_BOT;
    public static TextChannel ADMIN_BOT;
    
    /** Retrieves a text channel. Panics if it does not exist. */
    private static TextChannel getChannel(DiscordApi api, String id) {
        return api.getTextChannelById(id).get();        
    }

    public static void load(DiscordApi api, JSONObject obj) {
        CHAT = getChannel(api, obj.getString("chat"));
        WARNINGS = getChannel(api, obj.getString("warnings"));
        APPEAL = getChannel(api, obj.getString("appeal"));
        BUG_REPORT = getChannel(api, obj.getString("bug_report"));
        GR_REPORT = getChannel(api, obj.getString("gr_report"));
        MAP_SUBMISSIONS = getChannel(api, obj.getString("map_submissions"));
        MAP_RATING = getChannel(api, obj.getString("map_rating"));

        LOG = getChannel(api, obj.getString("log"));
        ERROR_LOG = getChannel(api, obj.getString("error_log"));
        
        BOT = getChannel(api, obj.getString("bot"));
        STAFF_BOT = getChannel(api, obj.getString("staff_bot"));
        ADMIN_BOT = getChannel(api, obj.getString("admin_bot"));
    }
}
