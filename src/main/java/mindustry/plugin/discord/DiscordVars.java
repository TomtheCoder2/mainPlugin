package mindustry.plugin.discord;

import org.javacord.api.DiscordApi;
import org.javacord.api.entity.emoji.Emoji;
import org.javacord.api.entity.server.Server;

public class DiscordVars {
    public static DiscordApi api;
    public static String prefix;
    public static String invite;

    /** Should never be null */
    public static Server server() {
        return api.getServers().iterator().next();
    }

    public static Emoji emoji(String name) {
        var emojis = server().getCustomEmojisByName(name); 
        if (emojis.size() != 0) {
            return emojis.iterator().next();
        }
        return null;
    }
}
