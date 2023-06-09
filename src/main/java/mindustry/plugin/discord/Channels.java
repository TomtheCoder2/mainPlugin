package mindustry.plugin.discord;

import arc.util.Log;
import com.electronwill.nightconfig.core.Config;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;

public class Channels {
    /**
     * Channel for live chat
     */
    public static ArrayList<TextChannel> CHAT = new ArrayList<>();

    public static TextChannel WARNINGS;
    public static TextChannel APPEAL;
    public static TextChannel BUG_REPORT;
    public static TextChannel GR_REPORT;

    public static TextChannel MAP_SUBMISSIONS;
    public static TextChannel MAP_RATING;

    public static TextChannel LOG;
    public static TextChannel ERROR_LOG;
    public static TextChannel COLONEL_LOG;

    public static ArrayList<TextChannel> BOT = new ArrayList<>();
    public static TextChannel MOD_BOT;
    public static TextChannel APPRENTICE_BOT;
    public static TextChannel ADMIN_BOT;
    public static TextChannel LIVE_LOG;

    /**
     * Retrieves a text channel. Panics if it does not exist.
     */
    private static TextChannel getChannel(DiscordApi api, String id) {
        return api.getTextChannelById(id).get();
    }

    public static void load(DiscordApi api, @NotNull Config obj) {
        for (String channelId : obj.<ArrayList<String>>get("CHAT")) {
            try {
                CHAT.add(getChannel(api, channelId));
            } catch (Exception e) {
                Log.err("Error loading channel: @", channelId);
                throw e;
            }
        }
        for (String channelId: obj.<ArrayList<String>>get("BOT")) {
            try {
                BOT.add(getChannel(api, channelId));
            } catch (Exception e) {
                Log.err("Error loading channel: @", channelId);
                throw e;
            }
        }
        for (Field f : Channels.class.getDeclaredFields()) {
            if (f.getType() == TextChannel.class) {
                String channelId = obj.get(f.getName());
                try {
                    f.set(null, getChannel(api, channelId));
                } catch (IllegalAccessException e) {
                    // Should never happen
                    throw new RuntimeException(e);
                } catch (Exception e) {
                    Log.err("Error loading channel: @", channelId);
                }
            }
        }
    }
}
