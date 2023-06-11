package mindustry.plugin.minimods;

import arc.Core;
import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.StringMap;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.serialization.Jval;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.DiscordLog;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.PluginConfig;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class Translate implements MiniMod {
    private final ObjectSet<String> langs = ObjectSet.with("en", "ar", "az", "zh", "cs", "da", "nl", "eo", "fi", "fr", "de", "el", "he", "hi", "hu", "id", "ga", "it", "ja", "ko", "fa", "pl", "pt", "ru", "sk", "es", "sv", "tr", "uk", "vi");
    private final ObjectMap<String, ObjectSet<String>> playerLangs = new ObjectMap<>();

    private TranslateThread thread;

    @Override
    public void registerEvents() {
        thread = new TranslateThread();
        thread.start();

        Events.on(EventType.PlayerChatEvent.class, event -> {
            if (event.player == null) return;
            if (event.message.startsWith("/")) return; //don't translate commands

            // remove unnecessary language entries
            ObjectSet<String> langsToRemove = new ObjectSet<>();
            for (var entry : playerLangs) {
                if (entry.value.size == 0) {
                    langsToRemove.add(entry.key);
                }
            }
            for (var lang : langsToRemove) {
                playerLangs.remove(lang);
            }

            if (playerLangs.size == 0) return; // if no one wants to translate, dont waste people's resources

            final String message = event.message;
            boolean success = thread.addDetect(message, lang -> {
                if (lang == null) return;

                for (final var entry : playerLangs) {
                    if (lang.equals(entry.key)) continue; // skip messages in same language
                    final ObjectSet<String> uuids = entry.value;
                    thread.addTranslate(message, lang, entry.key, resp -> {
                        if (resp == null) return;
                        if (resp.error == null) {
                            for (String uuid : uuids) {
                                Player p = Groups.player.find(x -> x.uuid().equals(uuid));
                                if (p == null) continue;
                                p.sendMessage(GameMsg.custom("TR", "white", "[coral][[[white]" + event.player.name + "[coral]]:[white] " + resp.text));
                            }
                        }
                    });
                }
            });

            if (!success) {
                DiscordLog.error("Translate Thread Full", ":(", null);
            }
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
                        if (resp == null) {
                            ctx.error("Translate error", "No response");
                        }
                        if (resp.error != null) {
                            ctx.error("Translate Error", resp.error);
                            return;
                        }
                        ctx.sendEmbed(new EmbedBuilder()
                                .setTitle("Translate")
                                .setColor(DiscordPalette.INFO)
                                .setDescription(resp.text)
                                .setFooter("Powered by LibreTranslate"));

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
                player.sendMessage(GameMsg.error("TR", "Language must be a two-letter country code (" + langs.toString(", ") + ")"));
                return;
            }

            // remove player from any other languages
            for (ObjectSet<String> uuids : playerLangs.values()) {
                uuids.remove(player.uuid());
            }

            // add player to specified language
            ObjectSet<String> uuids = playerLangs.get(lang);
            if (uuids == null) {
                uuids = new ObjectSet<>();
                playerLangs.put(lang, uuids);
            }
            uuids.add(player.uuid());

            player.sendMessage(GameMsg.info("TR", "Set language to [accent]" + lang));
        });
    }
}

/**
 * Runs the translations & returns callbacks.
 * All callbacks are run on the main thread.
 */
class TranslateThread extends Thread {
    protected BlockingQueue<Req> queue = new LinkedBlockingQueue<>(64);

    /**
     * Returns false if there are too many requests
     */
    public boolean addTranslate(String text, String from, String to, Consumer<TranslateApi.Resp> handler) {
        Req req = new Req();
        req.translateHandler = handler;
        req.text = text;
        req.fromLang = from;
        req.toLang = to;
        return this.queue.offer(req);
    }

    /**
     * Returns false if there are too many requests.
     */
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
            } catch (InterruptedException e) {
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

    private static class Req {
        Consumer<TranslateApi.Resp> translateHandler;
        Consumer<String> detectHandler;

        String text;
        String fromLang;
        String toLang;
    }
}

class TranslateApi {
    private final static String SERVER = "http://168.119.234.142:5000";

    /**
     * Translates a piece of text
     */
    public static Resp translate(String text, String fromLang, String toLang) {
        String response = null;
        try {
            Jval reqObj = Jval.newObject()
                    .put("q", text)
                    .put("source", fromLang)
                    .put("target", toLang);

            HttpRequest req = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(reqObj.toString()))
                    .uri(URI.create(SERVER + "/translate"))
                    .setHeader("User-Agent", PluginConfig.serverName)
                    .setHeader("Content-Type", "application/json")
                    .build();

            HttpResponse<String> resp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(req, HttpResponse.BodyHandlers.ofString());
            response = resp.body();
            Jval respObj = Jval.read(resp.body());
            if (respObj.has("error")) {
                DiscordLog.error("Translate: Translate Server Error", respObj.getString("error"), null);
                return new Resp(respObj.getString("error"), false);
            }
            return new Resp(respObj.getString("translatedText"));
        } catch (Exception e) {
            e.printStackTrace();
            DiscordLog.error("Translate: Translate Internal Error", e.getMessage(),
                    StringMap.of("Response", response == null ? "Unavailable" : "```\n" + response + "\n```"));
            return null;
        }
    }

    /**
     * Detects the language of a piece of text.
     */
    public static String detect(String text) {
        String response = null;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .POST(BodyPublishers.ofString("q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)))
                    .uri(URI.create(SERVER + "/detect"))
                    .setHeader("User-Agent", PluginConfig.serverName)
                    .setHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();
            HttpResponse<String> resp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(req, HttpResponse.BodyHandlers.ofString());
            response = resp.body();
            Jval respObj = Jval.read(response);
            if (respObj.isObject()) {
                String error = respObj.getString("error");
                Log.err("Translate error: " + error);
                DiscordLog.error("Translate: Detect Server Error", error, null);
                return null;
            }
            if (respObj.isArray()) {
                return respObj.asArray().get(0).getString("language");
            }
            return null;
        } catch (Exception error) {
            DiscordLog.error("Translate: Detect Internal Error", error.getMessage(),
                    StringMap.of("Response", response == null ? "Unavailable" : "```\n" + response + "\n```"));
            return null;
        }
    }

    public static class Resp {
        public String text;
        public String error;

        public Resp(String error, boolean eeek) {
            this.error = error;
        }

        public Resp(String text) {
            this.text = text;
        }
    }
}