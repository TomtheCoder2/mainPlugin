
package mindustry.plugin.utils;

/** Utility package for formatting in-game messages. */
public class GameMsg {
    public static String noPerms(String category) {
        return "[scarlet]<[blue]" + category + "[scarlet]>: You don't have the required rank for this command. Learn more about ranks with [pink]/info[scarlet]";
    }

    public static String error(String category, String msg) {
        return "[scarlet]<[blue]" + category + "[scarlet]>: " + msg;
    }   

    public static String info(String category, String msg) {
        return "[lightgray]<[blue]" + category + "[lightgray]>: " + msg; 
    }

    public static String success(String category, String msg) {
        return "[green]<[blue]" + category + "[green]>: " + msg; 
    }

    public static String custom(String category, String color, String msg) {
        return "[" + color + "]<[blue]" + category + "[" + color + "]>: " + msg;
    }
}