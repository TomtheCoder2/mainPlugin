package mindustry.plugin;

import mindustry.plugin.commands.*;
import mindustry.plugin.discordcommands.DiscordCommands;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.TextChannel;
import org.json.JSONObject;

import static mindustry.plugin.ioMain.*;
import static mindustry.plugin.utils.Utils.getTextChannel;

public class BotThread extends Thread {
    public DiscordApi api;
    public DiscordCommands commandHandler = new DiscordCommands();
    private Thread mt;
    private JSONObject data;

    /**
     * start the bot thread
     *
     * @param api  the discordApi to operate with
     * @param mt   the main Thread
     * @param data the data from settings.json
     */
    public BotThread(DiscordApi api, Thread mt, JSONObject data) {
        this.api = api; //new DiscordApiBuilder().setToken(data.get(0)).login().join();
        this.mt = mt;
        this.data = data;

        // register commands
        this.api.addMessageCreateListener(commandHandler);
        new ComCommands().registerCommands(commandHandler);
//        new ServerCommands(data).registerCommands(commandHandler);
        new Admin(data).registerCommands(commandHandler);
        new Apprentice(data).registerCommands(commandHandler);
        new MapReviewer(data).registerCommands(commandHandler);
        new Moderator(data).registerCommands(commandHandler);
        new Public(data).registerCommands(commandHandler);
        //new MessageCreatedListeners(data).registerListeners(commandHandler);
    }

    public void run() {
//        Timer.schedule(ioMain.loop(), 0.5F);
        TextChannel log_channel = getTextChannel(log_channel_id);
        while (this.mt.isAlive()) {
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