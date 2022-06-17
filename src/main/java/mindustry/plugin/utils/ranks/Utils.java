package mindustry.plugin.utils.ranks;

import mindustry.plugin.utils.Rank;

public class Utils {
    /**
     * Get a list of all ranks for the help page
     */
    public static String listRanks() {
        StringBuilder list = new StringBuilder();
        list.append("```\n");
        for (int i =0 ; i < Rank.all.length; i++) {
            Rank rank = Rank.all[i];
            list.append(i).append(": ").append(rank.name).append("\n");
        }
        list.append("```");
        return list.toString();
    }
}
