package mindustry.plugin.utils.ranks;

import java.awt.*;

/**
 * create and save Ranks
 */
public class Rank {
    public String tag = "";
    public String name = "";
    public String description = null;
    public Color color;

    /**
     * Create a new rank
     *
     * @param t name tag (gets displayed before the player names starts, for example: <*>Nautilus)
     * @param n name of the rank (for example: Moderator)
     * @param desc description for the rank
     * @param col the color of the rank
     */
    public Rank(String t, String n, String desc, Color col) {
        this.tag = t;
        this.name = n;
        this.description = desc;
        this.color = col;
    }

    /**
     * Create a new rank
     *
     * @param t name tag (gets displayed before the player names starts, for example: <*>Nautilus)
     * @param n name of the rank (for example: Moderator)
     * @param col the color of the rank
     */
    public Rank(String t, String n, Color col) {
        this.tag = t;
        this.name = n;
        this.color = col;
    }

}
