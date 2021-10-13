package mindustry.plugin;

import arc.math.Mathf;
//import mindustry.entities.type.Player;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.JSONObject;
import static mindustry.plugin.ioMain.*;

import mindustry.plugin.discordcommands.DiscordCommands;

import java.util.List;

import static mindustry.Vars.netServer;
//import static mindustry.Vars.playerGroup;
import static mindustry.plugin.Utils.*;

public class BotThread extends Thread {
    public DiscordApi api;
    private Thread mt;
    private JSONObject data;
    public DiscordCommands commandHandler = new DiscordCommands();
    /**
     * start the bot thread
     * @param api the discordApi to operate with
     * @param mt the main Thread
     * @param data the data from settings.json
     * */
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
        TextChannel log_channel = getTextChannel("882342315438526525");
        while (this.mt.isAlive()){
            try {
                Thread.sleep(60 * 1000);

//                System.out.println(joinedPlayer);

                for (Player p : Groups.player) {

                    if (joinedPlayer.size() > 0) {
                        EmbedBuilder eb = new EmbedBuilder();
                        eb.setTitle("Player Join Log");
                        StringBuilder desc = new StringBuilder();
                        for (Player player : joinedPlayer) {
                            desc.append(String.format("`%s` : `%d `:%s\n", player.uuid(), player.id, escapeEverything(player.name)));
                        }
                        eb.setDescription(String.valueOf(desc));
                        assert log_channel != null;
                        log_channel.sendMessage(eb);
                    }
                    joinedPlayer.clear();
                    PlayerData pd = getData(p.uuid());
                    if (pd == null) return;
//
                    // update buildings built
                    PersistentPlayerData tdata = (ioMain.playerDataGroup.getOrDefault(p.uuid(), null));
                    if (tdata != null){
                        if (tdata.bbIncrementor > 0){
                            pd.buildingsBuilt = pd.buildingsBuilt + tdata.bbIncrementor;
                            tdata.bbIncrementor = 0;
                        }
                    }
//
//
                    pd.playTime++;
                    // check if someone gets promoted
                    if(pd.rank <= 0 && pd.playTime >= newbieRequirements.playtime &&
                            pd.buildingsBuilt >= newbieRequirements.buildingsBuilt &&
                            pd.gamesPlayed >= newbieRequirements.gamesPlayed){
                        Call.infoMessage(p.con, Utils.formatMessage(p, newbieRequirements.promotionMessage));
                        if (pd.rank < 1) pd.rank = 1;
                    }
                    if(pd.rank <= 1 && pd.playTime >= activeRequirements.playtime &&
                            pd.buildingsBuilt >= activeRequirements.buildingsBuilt &&
                            pd.gamesPlayed >= activeRequirements.gamesPlayed){
                        Call.infoMessage(p.con, Utils.formatMessage(p, activeRequirements.promotionMessage));
                        if (pd.rank < 2) pd.rank = 2;
                    }
                    if(pd.rank <= 2 && pd.playTime >= veteranRequirements.playtime &&
                            pd.buildingsBuilt >= veteranRequirements.buildingsBuilt &&
                            pd.gamesPlayed >= veteranRequirements.gamesPlayed){
                        Call.infoMessage(p.con, Utils.formatMessage(p, veteranRequirements.promotionMessage));
                        if (pd.rank < 3) pd.rank = 3;
                    }
//                    if(pd.rank <= 1 && pd.playTime >= generalRequirements.playtime && pd.buildingsBuilt >= generalRequirements.buildingsBuilt && pd.gamesPlayed >= generalRequirements.gamesPlayed){
//                        Call.infoMessage(p.con, Utils.formatMessage(p, promotionMessage));
//                        if (pd.rank < 2) pd.rank = 2;
//                    }
//                    if(pd.rank <= 2 && pd.playTime >= corporalRequirements.playtime && pd.buildingsBuilt >= corporalRequirements.buildingsBuilt && pd.gamesPlayed >= corporalRequirements.gamesPlayed){
//                        Call.infoMessage(p.con, Utils.formatMessage(p, promotionMessage));
//                        if (pd.rank < 3) pd.rank = 3;
//                    }
//                    if(pd.rank <= 3 && pd.playTime >= sargentRequirements.playtime && pd.buildingsBuilt >= sargentRequirements.buildingsBuilt && pd.gamesPlayed >= sargentRequirements.gamesPlayed){
//                        Call.infoMessage(p.con, Utils.formatMessage(p, promotionMessage));
//                        if (pd.rank < 4) pd.rank = 4;
//                    }
                    setData(p.uuid(), pd);
                    ioMain.playerDataGroup.put(p.uuid(), tdata); // update tdata with the new stuff
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