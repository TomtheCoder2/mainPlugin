
package mindustry.plugin.utils;

/** Utility package for formatting in-game messages. */
public class MindustryMsg {
    public static String error(String category, String msg) {
        return "[scarlet]<[blue]" + category + "[scarlet]>:" + msg;
    }   

    public static String info(String category, String msg) {
        return "[lightgray]<[blue]" + category + "[lightgray]>: " + msg; 
    }

    public static String success(String category, String msg) {
        return "[green]<[blue]" + category + "[green]>: " + msg; 
    }
}