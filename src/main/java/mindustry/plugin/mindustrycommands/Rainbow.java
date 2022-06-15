package mindustry.plugin.mindustrycommands;

import arc.graphics.Color;
import arc.struct.ObjectMap;
import arc.util.CommandHandler;
import mindustry.plugin.MiniMod;
import mindustry.plugin.data.PlayerData;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Utils;
import mindustry.gen.Groups;
import mindustry.gen.Player;

public class Rainbow implements MiniMod {

    private static class RainbowData {
        public int hue;
        public int speed; // d_hue

        // Defaults
        public RainbowData() {
            hue = (int)(Math.random() * 360);
            speed = 5;
        }
    }

    // uuid => data
    private ObjectMap<String, RainbowData> data = new ObjectMap();

    public void registerEvents() {
        RainbowThread thread = new RainbowThread(Thread.currentThread());
        thread.setDaemon(false);
        thread.start();
    }

    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("rainbow", "[speed]", "Give your username a rainbow animation", (args, player) -> {
            // check rank
            PlayerData dbData = mindustry.plugin.database.Utils.getData(player.uuid());
            int rank = -1;
            if (dbData != null) {
                rank = dbData.rank;
            }
            if (rank < 0) {
                player.sendMessage(GameMsg.noPerms("Rainbow"));
                return;
            }

            RainbowData rainbowData = data.get(player.uuid());
            if (rainbowData == null) { // toggle on
                rainbowData = new RainbowData();

                if (args.length != 0) {
                    try {
                        rainbowData.speed = Integer.parseInt(args[0]);
                    } catch(NumberFormatException e) {
                        player.sendMessage(GameMsg.error("Rainbow", "Speed must be a number."));
                    }
                }                

                data.put(player.uuid(), rainbowData);
                player.sendMessage(GameMsg.custom("Rainbow", "sky", "Effect toggled on with speed [orange]" + rainbowData.speed + "[sky]."));
            } else {  // toggle off
                data.remove(player.uuid());
                player.sendMessage(GameMsg.custom("Rainbow", "sky", "Effect toggled off."));
            }
        });
    }

    private class RainbowThread extends Thread {
        private Thread mainThread;

        public RainbowThread(Thread mainThread) {
            this.mainThread = mainThread;
        }

        public void run() {
            while (mainThread.isAlive()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                for (Player player : Groups.player) {
                    RainbowData rainbowData = data.get(player.uuid());
                    if (rainbowData == null) {
                        continue;
                    }

                    var playerData = mindustry.plugin.database.Utils.getData(player.uuid());
                    int rank = 0;
                    if (playerData != null) {
                        rank = playerData.rank;
                    }
                    
                    // update rainbow (SINGULAR)
                    rainbowData.hue += rainbowData.speed;
                    rainbowData.hue = rainbowData.hue % 360;
                    String hex = "#" + Color.HSVtoRGB(rainbowData.hue / 360f, 1f, 1f).toString().substring(0, -2);
                    if (rank < mindustry.plugin.utils.ranks.Utils.rankNames.size() && rank >= 0) { // this should never be false
                        player.name = "[" + hex + "]"
                            + Utils.escapeColorCodes(mindustry.plugin.utils.ranks.Utils.rankNames.get(rank).tag)
                            + "[" + hex + "]"
                            + Utils.escapeEverything(player.name);
                    }
                }
            }
        }
    }
}