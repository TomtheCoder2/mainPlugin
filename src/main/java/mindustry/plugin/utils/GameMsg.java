package mindustry.plugin.utils;

/**
 * Utility package for formatting in-game messages.
 */
public class GameMsg {
    public static String ERROR = "scarlet";
    public static String SUCCESS = "green";
    public static String INFO = "accent";
    /** Color to display commands in */
    public static String CMD = "cyan";
    public static String ratelimit(String category, String command) {
        return "[scarlet]<[blue]" + category + "[scarlet]>: You've exceeded the rate limit for [" + CMD + "]/" + command + "[scarlet]. Wait a while before using it again.";
    }

    public static String noPerms(String category) {
        if (category == null) {
            return "[scarlet]You don't have the required rank for this command. Learn more about ranks with [" + CMD + "]/info[scarlet].";
        }
        return "[scarlet]<[blue]" + category + "[scarlet]>: You don't have the required rank for this command. Learn more about ranks with [" + CMD + "]/info[scarlet]";
    }

    public static String error(String category, String msg) {
        return "[scarlet]<[blue]" + category + "[scarlet]>: " + msg;
    }

    public static String info(String category, String msg) {
        return "[accent]<[blue]" + category + "[accent]>: " + msg;
    }

    public static String success(String category, String msg) {
        return "[green]<[blue]" + category + "[green]>: " + msg;
    }

    public static String custom(String category, String color, String msg) {
        return "[" + color + "]<[blue]" + category + "[" + color + "]>: " + msg;
    }
}