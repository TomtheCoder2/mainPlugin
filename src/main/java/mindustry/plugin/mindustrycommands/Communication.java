package mindustry.plugin.mindustrycommands;

import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.ioMain;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.channel.TextChannel;
import org.json.JSONObject;
import org.json.JSONTokener;

import static arc.util.Log.debug;
import static mindustry.plugin.ioMain.*;
import static mindustry.plugin.utils.Utils.escapeEverything;
import static mindustry.plugin.utils.Utils.getTextChannel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Communication implements MiniMod {
    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("w", "<player> <text...>", "Whisper text to another player.", (args, player) -> {
            //find player by name
//            Player other = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));

            Player other = Utils.findPlayer(args[0]);

            // give error message with scarlet-colored text if player isn't found
            if (other == null) {
                player.sendMessage("[scarlet]No player by that name found!");
                return;
            }

            // send the other player a message, using [lightgray] for gray text color and [] to reset color
            other.sendMessage("[orange][[[gray]whisper from [#ffd37f]" + Strings.stripColors(player.name) + "[orange]]: [gray]" + args[1]);
            player.sendMessage("[orange][[[gray]whisper to [#ffd37f]" + Strings.stripColors(other.name) + "[orange]]: [gray]" + args[1]);
        });
        handler.<Player>register("translate", "<language> <text...>", "Translate your message", (arg, player) -> {
            Translate.Resp result = Translate.translate(arg[1], "auto", arg[0]);
            if (result.error != null) {
                player.sendMessage(GameMsg.error("TR", "Translation failed: " + result.error + " (host [sky]" + result.host + "[scarlet])"));
            } else {
                player.sendMessage(GameMsg.info("TR", "[white]'" + arg[1] + "'[lightgray]  is translated as [orange]" + result.text + "[lightgray] (powered by [sky]" + result.host + "[lightgray])"));
            }
        });

        handler.<Player>register("en", "<language> <text...>", "Send a message in English", (arg, player) -> {
            Translate.Resp result = Translate.translate(arg[1], arg[0], "en");
            if (result.error != null) {
                player.sendMessage(GameMsg.error("TR", "Translation failed: " + result.error + " (host [sky]" + result.host + "[scarlet])"));
            } else {
                Call.sendMessage(GameMsg.custom("TR", "white", "([orange]" + player.name() + "[white]): " + result.text + " [lightgray](powered by [sky]" + result.host + "[lightgray])"));
            }
        });
        handler.removeCommand("t");
        handler.<Player>register("t", "<message...>", "Send a message only to your teammates.", (args, player) -> {
            String message = args[0];
            if (!checkChatRatelimit(message, player)) return;
            String raw = "[#" + player.team().color.toString() + "]<T> " + chatFormatter.format(player, message);
            Groups.player.each(p -> p.team() == player.team(), o -> o.sendMessage(raw, player, message));
        });

    }
}

class Translate {
    // Randomly cycle through servers to use translate.
    // That way we decrease the load on any single server. 
    // https://github.com/LibreTranslate/LibreTranslate#mirrors
    private final static String[] SERVERS = new String[] {
        "libretranslate.de",
        "translate.argosopentech.com",
        "translate.api.skitzen.com",
        "libretranslate.pussthecat.org",
        "translate.fortytwo-it.com",
        "translate.terraprint.co",
        "lt.vern.cc"
    };

    private static int serverIdx = 0;


    private static String getHost(String server) {
        String[] parts = server.split("\\.");
        if (parts.length <= 1) {
            return server;
        } else {
            return parts[parts.length-2] + "." + parts[parts.length-1];
        }
    }

    public static class Resp {
        public String text;
        public String error;
        public String host;

        public Resp(String error, String host, boolean eeek) {
            this.error = error;
            this.host = host;
        }

        public Resp(String text, String host) {
            this.text = text;
            this.host = host;
        }
    }

    /** Translates a piece of text
     */
    public static Resp translate(String text, String fromLang, String toLang) {
        String server = SERVERS[serverIdx++];

        try {
            JSONObject reqObj = new JSONObject()
                .put("q", text)
                .put("source", fromLang)
                .put("target", toLang);

            HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(reqObj.toString()))
                .uri(URI.create("https://" + server + "/translate"))
                .setHeader("User-Agent", ioMain.class.getCanonicalName())
                .setHeader("Content-Type", "application/json")
    
                .build();
            HttpResponse<String> resp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(req, HttpResponse.BodyHandlers.ofString());
            JSONObject respObj = new JSONObject(new JSONTokener(resp.body()));
            if (respObj.has("error")) {
                return new Resp(respObj.getString("error"), getHost(server), false);
            }
            return new Resp (respObj.getString("translatedText"), getHost(server));
        } catch(Exception e) {
            Log.err("Translate error for server: " +server);
            e.printStackTrace();
            return new Resp(e.toString(), getHost(server), false);
        }
    }
}