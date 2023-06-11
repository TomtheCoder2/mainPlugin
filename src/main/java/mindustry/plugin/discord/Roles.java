package mindustry.plugin.discord;

import arc.util.Strings;
import com.electronwill.nightconfig.core.Config;
import org.javacord.api.DiscordApi;

import java.lang.reflect.Field;


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
    /**
     * Report investigator
     */
    public static long RI;

    public static long Auto;

    public static void load(DiscordApi api, Config data) {
        for (Field f : Roles.class.getDeclaredFields()) {
            try {
                long parsed = Strings.parseLong(data.get(f.getName()), Long.MIN_VALUE);
                if (parsed == Long.MIN_VALUE) throw new IllegalArgumentException("Role id must be a number");
                f.set(null, Strings.parseLong(data.get(f.getName()), 0));
            } catch (IllegalAccessException e) {
                // Should never happen
                throw new RuntimeException(e);
            }
        }
    }
}
