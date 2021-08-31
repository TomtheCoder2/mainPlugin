package mindustry.plugin;

import arc.math.Mathf;
import mindustry.Vars;
//import mindustry.entities.type.Player;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.javacord.api.DiscordApi;
import org.json.JSONObject;

import mindustry.plugin.discordcommands.DiscordCommands;

import java.util.HashMap;
import java.util.Map;

import static mindustry.Vars.netServer;
//import static mindustry.Vars.playerGroup;
//import static mindustry.plugin.Utils.*;

public class BotThread extends Thread {
    public DiscordApi api;
    private Thread mt;
    private JSONObject data;
    public DiscordCommands commandHandler = new DiscordCommands();

    public BotThread(DiscordApi api, Thread mt, JSONObject data) {
        this.api = api; //new DiscordApiBuilder().setToken(data.get(0)).login().join();
        this.mt = mt;
        this.data = data;

        // register commands
        this.api.addMessageCreateListener(commandHandler);
        new ComCommands().registerCommands(commandHandler);
        new ServerCommands(data).registerCommands(commandHandler);
        //new MessageCreatedListeners(data).registerListeners(commandHandler);
    }

    public void run(){
        while (this.mt.isAlive()){
            try {
                Thread.sleep(60 * 1000);

                for (Player p : Groups.player) {

//                    PlayerData pd = getData(p.uuid);
//                    if (pd == null) return;
//
//                    // update buildings built
//                    PersistentPlayerData tdata = (ioMain.playerDataGroup.getOrDefault(p.uuid, null));
//                    if (tdata != null){
//                        if (tdata.bbIncrementor > 0){
//                            pd.buildingsBuilt = pd.buildingsBuilt + tdata.bbIncrementor;
//                            tdata.bbIncrementor = 0;
//                        }
//                    }
//
//
//                    pd.playTime++;
//                    if(pd.rank <= 0 && pd.playTime >= activeRequirements.playtime && pd.buildingsBuilt >= activeRequirements.buildingsBuilt && pd.gamesPlayed >= activeRequirements.gamesPlayed){
//                        Call.onInfoMessage(p.con, Utils.formatMessage(p, promotionMessage));
//                        if (pd.rank < 1) pd.rank = 1;
//                    }
//                    setData(p.uuid, pd);
//                    ioMain.playerDataGroup.put(p.uuid, tdata); // update tdata with the new stuff
                }
                if(Mathf.chance(0.01f)){
                    api.updateActivity("( ͡° ͜ʖ ͡°)");
                    System.out.println("( ͡° ͜ʖ ͡°)");
                } else {
                    api.updateActivity("with " + Groups.player.size() + (netServer.admins.getPlayerLimit() == 0 ? "" : "/" + netServer.admins.getPlayerLimit()) + " players");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        api.disconnect();
    }
}