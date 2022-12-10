package mindustry.plugin.utils;

import arc.graphics.Color;

public class SubRank {
    public static final SubRank[] all = new SubRank[]{
            new SubRank("\UE808", "Default", Color.valueOf("ffffff"), "Default sub-rank"),
            new SubRank("\uE88E","Watcher", new Color(0x00e500ff), "Obtainable by reporting 20 griefers")
            new Subrank("\UE868","Bug_chaser", new color("DE0000"),"Obtainable by reporting 3-5 bugs with good quality bug reports")
    };

    public String name;
    public String tag;
    public String description;
    public Color color;
    public SubRank(String tag, String name, Color color, String desc) {
        this.tag = tag;
        this.name = name;
        this.color = color;
        this.description = desc;
    }
}
