package mindustry.plugin.data;
//import arc.struct.Array;
//import mindustry.entities.type.BaseUnit;
//import mindustry.entities.type.Player;
import arc.util.Timer;
import mindustry.entities.bullet.BulletType;
import mindustry.plugin.utils.Ratelimit;
import mindustry.world.Tile;

import java.io.Serializable;

public class PersistentPlayerData implements Serializable {
    public String origName;
//    public Array<BaseUnit> draugPets = new Array<>();
    public int bbIncrementor = 0;
//    public boolean spawnedLichPet;
//    public boolean spawnedPowerGen;
    public boolean rainbowColor = false;
    public int hue;
    public boolean doRainbow;
    public boolean muted = false;
    public boolean frozen = false;
    public boolean votedMap = false;
    public String redeem = null;
    public int redeemKey = -1;
    public Timer.Task task;

    public BulletType bt;
    public float sclVelocity;
    public float sclLifetime;
    public float sclDamage;

    public String tag;
    public Tile tapTile;
    public boolean inspector;
    public boolean snowBall = false;


    // 50 configures per 1000 ms
    public Ratelimit configureRatelimit = new Ratelimit(50, 1000);
    // 10 rotates per 1000 ms
    public Ratelimit rotateRatelimit = new Ratelimit(10, 1000);

    public PersistentPlayerData() {}

    public void setHue(int i) {
        this.hue = i;
    }
}