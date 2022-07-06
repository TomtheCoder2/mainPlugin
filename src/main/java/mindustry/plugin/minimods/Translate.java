package mindustry.plugin.minimods;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;

import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import arc.Core;
import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.struct.StringMap;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.serialization.JsonValue;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.DiscordLog;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Config;
import mindustry.plugin.utils.GameMsg;

public class Translate implements MiniMod {
    private ObjectSet<String> langs = ObjectSet.with("en","ar","az","zh","cs","da","nl","eo","fi","fr","de","el","he","hi","hu","id","ga","it","ja","ko","fa","pl","pt","ru","sk","es","sv","tr","uk","vi");
    private ObjectMap<String, ObjectSet<String>> playerLangs = new ObjectMap<>(); 

    private TranslateThread thread;

    @Override
    public void registerEvents() {
        thread = new TranslateThread();
        thread.start();

        Events.on(EventType.PlayerChatEvent.class, event -> {
            if (event.player == null) return;
            if (playerLangs.size == 0) return; // if no one wants to translate, dont waste people's resources
            final String message =event.message;
            thread.addDetect(message, lang -> {
                if (lang == null) return;

                for (final var entry : playerLangs) {
                    final ObjectSet<String> uuids = entry.value;
                    thread.addTranslate(message, lang, entry.key, resp -> {
                        if (resp.error == null) {
                            for (String uuid : uuids) {
                                Player p = Groups.player.find(x -> x.uuid().equals(uuid));
                                if (p == null) continue;
                                p.sendMessage(GameMsg.custom("TR", "white", "[orange][[white]" + event.player.name + "[orange]]: "+  resp.text));
                            }
                        }
                    });
                }
            });
        });

        Events.on(EventType.PlayerLeave.class, event -> {
            if (event.player == null) return;
            
            for (var uuids : playerLangs.values()) {
                uuids.remove(event.player.uuid());
            }
        });
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("translate", "<lang> <text...>", 
            data -> {
                data.help = "Translate a message";
                data.category = "Communication";
            },
            ctx -> {
                if (!thread.addTranslate(ctx.args.get("text"), "auto", ctx.args.get("lang"), resp -> {
                    if (resp.error != null) {
                        ctx.error("Translate Error", resp.error);
                        return;
                    }
                    ctx.sendEmbed(new EmbedBuilder()
                        .setTitle("Translate")
                        .setColor(DiscordPalette.INFO)
                        .setDescription(resp.text)
                        .setFooter("Host: " + resp.host));
                        
                })) {
                    ctx.error("Translate Error", "Queue is full.");
                }
            }
        );
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("translate", "<lang>", "Translate chat", (args, player) -> {
            String lang = args[0];
            if (!langs.contains(lang)) {
                player.sendMessage(GameMsg.error("TR", "Language must be a two-letter country code (en, ru, zh, ...)"));
            }

            // remove player from any other languages
            for (ObjectSet<String> uuids : playerLangs.values()) {
                uuids.remove(player.uuid());
            }

            // add player to specified language
            ObjectSet<String> uuids = playerLangs.get(lang);
            if (uuids == null) {
                uuids = new ObjectSet<>();
            }
            uuids.add(player.uuid());

            player.sendMessage(GameMsg.info("TR", "Set language to [accent]" + lang));
        });
    }
}

/** Runs the translations & sends messages to discord and chat */
class TranslateThread extends Thread {
    private static class Req {
        Consumer<TranslateApi.Resp> translateHandler;
        Consumer<String> detectHandler;

        String text;
        String fromLang;
        String toLang;
    }

    protected BlockingQueue<Req> queue = new LinkedBlockingQueue<>(64);

    /** Returns false if there are too many requests */
    public boolean addTranslate(String text, String from, String to, Consumer<TranslateApi.Resp> handler) {
        Req req = new Req();
        req.translateHandler = handler;
        req.text = text;
        req.fromLang = from;
        req.toLang = to;
        return this.queue.offer(req);
    }

    /** Returns false if there are too many requests. */
    public boolean addDetect(String text, Consumer<String> handler) {
        Req req = new Req();
        req.detectHandler = handler;
        req.text = text;
        return this.queue.offer(req);
    }

    @Override
    public void run() {
        while (true) {
            final Req req;
            try {
                req = queue.take();
            } catch(InterruptedException e) {
                Log.err(e);
                continue;
            }

            if (req.translateHandler != null) {
                final TranslateApi.Resp resp = TranslateApi.translate(req.text, req.fromLang, req.toLang);            
                Core.app.post(() -> {
                    req.translateHandler.accept(resp);
                });
            } else if (req.detectHandler != null) {
                final String resp = TranslateApi.detect(req.text);
                Core.app.post(() -> {
                    req.detectHandler.accept(resp);
                });
            }
        }
    }
}

class TranslateApi {
    // Randomly cycle through servers to use translate.
    // That way we decrease the load on any single server. 
    // https://github.com/LibreTranslate/LibreTranslate#mirrors
    private final static String[] SERVERS = new String[] {
        "libretranslate.de",
        "translate.argosopentech.com",
//        "translate.api.skitzen.com", does not work
        "libretranslate.pussthecat.org",
        "translate.fortytwo-it.com",
//        "translate.terraprint.co", does not work.
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

    private static String getServer() {
        String server = SERVERS[serverIdx];
        serverIdx = (serverIdx + 1) % SERVERS.length;
        return server;
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
        String server = getServer();
        try {
            JSONObject reqObj = new JSONObject()
                .put("q", text)
                .put("source", fromLang)
                .put("target", toLang);

            HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(reqObj.toString()))
                .uri(URI.create("https://" + server + "/translate"))
                .setHeader("User-Agent", Config.serverName)
                .setHeader("Content-Type", "application/json")
    
                .build();
            HttpResponse<String> resp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(req, HttpResponse.BodyHandlers.ofString());
            JSONObject respObj = new JSONObject(new JSONTokener(resp.body()));
            if (respObj.has("error")) {
                DiscordLog.error("Translate: Translate Server Error", respObj.getString("error"), StringMap.of("Host", getHost(server)));
                return new Resp(respObj.getString("error"), getHost(server), false);
            }
            return new Resp (respObj.getString("translatedText"), getHost(server));
        } catch(Exception e) {
            Log.err("Translate error for server: " +server);
            e.printStackTrace();
            DiscordLog.error("Translate: Translate Internal Error", e.getMessage(), StringMap.of("Host", getHost(server)));
            return new Resp(e.toString(), getHost(server), false);
        }
    }

    /**
     * Detects the language of a piece of text.
     */
    public static String detect(String text) {
        String server = getServer();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .POST(BodyPublishers.ofString("q=" + URLEncoder.encode(text, "utf-8")))
                .uri(URI.create("https://" + server + "/detect"))
                .setHeader("User-Agent", Config.serverName)
                .setHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
            HttpResponse<String> resp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(req, HttpResponse.BodyHandlers.ofString());
            Object respObj = new JSONTokener(resp.body()).nextValue();
            if (respObj instanceof JSONObject) {
                String error = ((JSONObject)respObj).getString("error");
                Log.err("Translate error: " + error);
                DiscordLog.error("Translate: Detect Server Error", error, StringMap.of("Host", getHost(server)));
                return null;
            }
            JSONArray array = (JSONArray)respObj;
            return array.getJSONObject(0).getString("language");
        } catch(Exception error) {
            DiscordLog.error("Translate: Detect Internal Error", error.getMessage(), StringMap.of("Host", getHost(server)));
            return null;
        }
    }
}