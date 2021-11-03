package mindustry.plugin;

import arc.Core;
import arc.math.Mathf;
//import mindustry.entities.type.Player;
import arc.util.Timer;
import mindustry.core.GameState;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.JSONObject;

import static mindustry.Vars.state;
import static mindustry.plugin.ioMain.*;

import mindustry.plugin.discordcommands.DiscordCommands;

import java.util.List;
import static arc.util.Log.debug;

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
//        Timer.schedule(ioMain.loop(), 0.5F);
        TextChannel log_channel = getTextChannel("882342315438526525");
        while (this.mt.isAlive()){
            try {
                Thread.sleep(60 * 1000);

//                System.out.println(joinedPlayer);

                update(log_channel, api);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        api.disconnect();
    }
}