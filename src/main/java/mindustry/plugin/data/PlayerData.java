package mindustry.plugin.data;

import mindustry.world.Tile;

public class PlayerData implements Cloneable {
    public String uuid;
    public int rank;
    public int playTime = 0;
    public int buildingsBuilt = 0;
    public int gamesPlayed = 0;
    public boolean verified = false;
    public boolean banned = false;
    public long bannedUntil = 0;
    public String banReason = "";

    public String discordLink = "";

    public PlayerData(Integer rank) {
        this.rank = rank;
    }

    public void reprocess() {
        if (banReason == null) this.banReason = "";
        if (discordLink == null) this.discordLink = "";
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}