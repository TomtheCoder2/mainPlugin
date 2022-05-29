package mindustry.plugin;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.core.GameState;
import mindustry.core.NetServer;
import mindustry.entities.Effect;
import mindustry.game.EventType;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.graphics.Pal;
import mindustry.maps.Map;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.net.Administration.Config;
import mindustry.plugin.data.PersistentPlayerData;
import mindustry.plugin.data.PlayerData;
import mindustry.plugin.data.TileInfo;
import mindustry.plugin.database.MapData;
import mindustry.plugin.effect.EffectHelper;
import mindustry.plugin.effect.EffectObject;
import mindustry.plugin.mapChange.MapChange;
import mindustry.plugin.requests.Translate;
import mindustry.plugin.utils.*;
import mindustry.plugin.utils.ranks.Rank;
import mindustry.world.Tile;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.util.logging.FallbackLoggerConfiguration;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import mindustry.plugin.utils.ranks.Requirement;

import static arc.util.Log.*;
import static mindustry.Vars.*;
import static mindustry.plugin.database.Utils.*;
import static mindustry.plugin.discordcommands.DiscordCommands.error_log_channel;
import static mindustry.plugin.effect.EffectHelper.getEffect;
import static mindustry.plugin.utils.CustomLog.logConnections;
import static mindustry.plugin.utils.Utils.*;
import static mindustry.plugin.utils.ranks.Utils.*;
import static org.javacord.api.util.logging.FallbackLoggerConfiguration.setDebug;
import static org.javacord.api.util.logging.FallbackLoggerConfiguration.setTrace;

public class ioMain extends Plugin {
    //    public static final File prefsFile = new File("prefs.properties");
//    public static Net net = new Net();
//    public static Prefs prefs = new Prefs(prefsFile);
//    public GetMap map = new GetMap();
    public static final Fi pluginDir = new Fi("./config/mods/");
    private static final String lennyFace = "( \u0361\u00B0 \u035C\u0296 \u0361\u00B0)";
    public static DiscordApi api = null;
    public static String prefix = ".";
    public static String live_chat_channel_id = "";
    public static String map_rating_channel_id = "";
    public static String log_channel_id = "";
    public static String bot_channel_id = null;
    public static String apprentice_bot_channel_id = null;
    public static String staff_bot_channel_id = null;
    public static String admin_bot_channel_id = null;
    public static String discordInviteLink = null;
    public static String serverName = "<untitled>";
    public static JSONObject data; //token, channel_id, role_id
    public static String apiKey = "";
    public static int effectId = 0; // effect id for the snowball
    public static ArrayList<String> joinedPlayers = new ArrayList<>();
    public static List<String> leftPlayers = new ArrayList<>();
    public static long passedMapTime = 0;
    //    public Timer.Task rateMapTask; // popup to force map rating
    public static ContentHandler contentHandler; // map and schem handler
    public static boolean previewSchem = false; // preview schem or not
    //    static Gson gson = new Gson();
    public static HashMap<String, PersistentPlayerData> playerDataGroup = new HashMap<>(); // uuid(), data
    public static Boolean enableJs = false; // whether js is enabled for everyone
    public static Timer.Task enableJsTask;
    public static HashMap<Tile, TileInfo> tileInfoHashMap = new HashMap<>();
    public static int logCount = 0; // only log join/leaves every 5 minutes
    public static TextChannel live_chat_channel;
    public static TextChannel log_channel;
    public static Boolean enableMapRatingPopups = true;
    private final long CDT = 300L;
    private final ObjectMap<Long, String> CommandCooldowns = new ObjectMap<>(); // uuid
    //    private final String fileNotFoundErrorMessage = "File not found: config\\mods\\settings.json";
    public ObjectMap<String, Role> discRoles = new ObjectMap<>();
    public NetServer.ChatFormatter chatFormatter = (player, message) -> player == null ? message : "[coral][[" + player.coloredName() + "[coral]]:[white] " + message;
    public MapChange mapChange = new MapChange();
    //    public ObjectMap<String, TextChannel> discChannels = new ObjectMap<>();
//    protected Interval timer = new Interval(1);
    //cooldown between votes
    float voteCooldown = 120;
    // register commands that run on the server
    // cool-downs per player
    ObjectMap<String, Timekeeper> cooldowns = new ObjectMap<>();
    // current kick sessions
    VoteSession[] currentlyKicking = {null};

    // register event handlers and create variables in the constructor
    public ioMain() {
        info("Starting Discord Plugin...");
        info(lennyFace);
        // disable debug logs from javacord (doesnt work tho, idk why)
        setDebug(false);
        FallbackLoggerConfiguration.setDebug(false);
        FallbackLoggerConfiguration.setTrace(false);
        JSONObject allData;
        try { // read settings
            String pureJson = Core.settings.getDataDirectory().child("mods/settings.json").readString();
            allData = new JSONObject(new JSONTokener(pureJson));
            // url, username and password to connect to the database
            url = allData.getString("url");
            user = allData.getString("user");
            password = allData.getString("password");
            // url to connect to the MindServ
            maps_url = allData.getString("maps_url");
            // for the live chat between the discord server and the mindustry server
            live_chat_channel_id = allData.getString("live_chat_channel_id");
            // log joins bans etc
            log_channel_id = allData.getString("log_channel_id");
            // channel to give feedback for maps
            map_rating_channel_id = allData.getString("map_rating_channel_id");
            // iplookup api key
            apapi_key = allData.getString("apapi_key");
            // bot channels
            bot_channel_id = allData.getString("bot_channel_id");
            apprentice_bot_channel_id = allData.getString("apprentice_bot_channel");
            staff_bot_channel_id = allData.getString("staff_bot_channel_id");
            admin_bot_channel_id = allData.getString("admin_bot_channel_id");
            // link to join our discord server
            discordInviteLink = allData.getString("discordInviteLink");
            previewSchem = allData.getBoolean("previewSchem");
            if (allData.has("enableMapRatingPopups")) {
                enableMapRatingPopups = allData.getBoolean("enableMapRatingPopups");
            }
            System.out.printf("url: %s, user: %s, password: %s%n", url, user, password);
        } catch (Exception e) {
            Log.err("Couldn't read settings.json file.");
            return;
        }
        try { // test connection
            connect();
        } catch (Exception e) {
            err("Could not login to PostgresSQL database!");
        }
        Utils.init();
        EffectHelper.init();

        try {
            String pureJson = Core.settings.getDataDirectory().child("mods/settings.json").readString();
            data = allData = new JSONObject(new JSONTokener(pureJson));
        } catch (Exception e) {
            Log.err("Couldn't read settings.json file.");
        }
        try {
            api = new DiscordApiBuilder().setToken(allData.getString("token")).login().join();
            Log.info("Logged in as: " + api.getYourself());
        } catch (Exception e) {
            Log.err("Couldn't log into discord.");
        }
        // start bot thread for handling commands and other messages
        BotThread bt = new BotThread(api, Thread.currentThread(), allData);
        bt.setDaemon(false);
        bt.start();

        FallbackLoggerConfiguration.setDebug(false);
        FallbackLoggerConfiguration.setTrace(false);

        /**
         * start rainbow command to change color of the names\n
         * This runs as a thread for performance reasons
         * */
        Rainbow rainbowThread = new Rainbow(Thread.currentThread());
        rainbowThread.setDaemon(false);
        rainbowThread.start();


        // set the channels
        live_chat_channel = getTextChannel(live_chat_channel_id);
        log_channel = getTextChannel(log_channel_id);

        TextChannel tc = getTextChannel("881300954845179914");
        if (!Objects.equals(live_chat_channel_id, "")) {
            tc = getTextChannel(live_chat_channel_id);
        } else {
            System.err.println("couldn't find live_chat_channel_id!");
        }
        if (tc != null) {
            // if there's a live channel create a chat filter to send all messages to Discord
            TextChannel finalTc = tc;
            Events.on(EventType.PlayerChatEvent.class, event -> {
                if (event.message.charAt(0) != '/') {
                    Player player = event.player;
                    assert player != null;
                    PersistentPlayerData tdata = playerDataGroup.get(player.uuid());
                    assert tdata != null;
                    if (!tdata.muted) {
                        StringBuilder sb = new StringBuilder(event.message);
                        for (int i = event.message.length() - 1; i >= 0; i--) {
                            if (sb.charAt(i) >= 0xF80 && sb.charAt(i) <= 0x107F) {
                                sb.deleteCharAt(i);
                            }
                        }
                        finalTc.sendMessage("**" + escapeEverything(event.player.name) + "**: " + sb);
                    } else {
                        player.sendMessage("[cyan]You are muted!");
                    }
                }
            });
        }

        // setup prefix
        if (data.has("prefix")) {
            prefix = String.valueOf(data.getString("prefix").charAt(0));
        } else {
            Log.warn("Prefix not found, using default '.' prefix.");
        }

        // setup name
        if (data.has("server_name")) {
            serverName = String.valueOf(data.getString("server_name"));
        } else {
            Log.warn("No server name setting detected!");
        }

        if (data.has("api_key")) {
            apiKey = data.getString("api_key");
            Log.info("api_key set successfully");
        } else {
            warn("No api key for ip lookups (ipapi).");
        }

        // display on screen messages
        float duration = 10f;
        int start = 450;
        int increment = 30;

        Timer.schedule(() -> {
            int currentInc = 0;
            for (String msg : onScreenMessages) {
                Call.infoPopup(msg, duration, 20, 50, 20, start + currentInc, 0);
                currentInc = currentInc + increment;
            }
        }, 0, 10);

        // force map votes with popup
        if (enableMapRatingPopups) {
            Timer.schedule(() -> {
                for (Player player : Groups.player) {
                    if (player == null) continue; // ...idk how even
                    PersistentPlayerData tdata = playerDataGroup.get(player.uuid());
                    if (tdata != null) {
                        if (!tdata.votedMap) {
                            rateMenu(player);
                            tdata.votedMap = true;
                        }
                    }
                }
            }, 0, 120);
        }

        Events.on(EventType.ServerLoadEvent.class, event -> {
//            contentHandler = new ContentHandler();
            Log.info("Everything's loaded !");
        });

        // update every tick
        // player disconnected
        Events.on(EventType.PlayerLeave.class, event -> {
            String uuid = event.player.uuid();
            // free ram
            if (playerDataGroup.get(uuid) != null) {
                playerDataGroup.remove(uuid);
            }

            if (!leftPlayers.contains(uuid)) {
                leftPlayers.add(uuid);
            }

            if (currentlyKicking[0] != null) {
                if (currentlyKicking[0].target == event.player) {
                    currentlyKicking[0].left();
                }
            }
        });


        // player joined
        TextChannel log_channel = getTextChannel("882342315438526525");
        Events.on(EventType.PlayerJoin.class, event -> {
            Player player = event.player;
            if (bannedNames.contains(player.name)) {
                player.con.kick("[scarlet]Please change your name.");
                return;
            }
            // check if the player is already in the database
            PlayerData pd = getData(player.uuid());

            // put the player in the playerDataGroup, which saves player data while the player is online
            if (!playerDataGroup.containsKey(player.uuid())) {
                PersistentPlayerData data = new PersistentPlayerData();
                playerDataGroup.put(player.uuid(), data);
            }

            // check if he's impersonating a rank
            // remove all color codes, so it's not possible to just change the color of the rank symbol
            String escapedName = escapeColorCodes(player.name).replaceAll("\\[accent\\]", "");
            for (java.util.Map.Entry<Integer, Rank> rank : rankNames.entrySet()) {
                if (rank.getKey() == 0) continue;
                // compare the player's potential escaped rank symbol with the escaped symbols from the utils
                if (escapedName.toLowerCase().startsWith(escapeColorCodes(rank.getValue().tag).replaceAll("\\[accent\\]", ""))) {
                    player.con.kick("[scarlet]Dont impersonate a rank.");
                    info("Player " + escapedName + " tried to impersonate rank: " + rank.getValue().name);
                    return;
                }
            }

            if (pd != null) {
//                try {
//                    if (pd.discordLink == null) {
//                        pd.reprocess();
//                        setData(player.uuid(), pd);
//                    }
//                } catch (Exception ignored) {
//                    pd.reprocess();
//                    setData(player.uuid(), pd);
//                }
                if (pd.banned || pd.bannedUntil > Instant.now().getEpochSecond()) {
                    player.con.kick("[scarlet]You are banned.[accent] Reason:\n" + pd.banReason + "\n[white] If you what to appeal join our discord server: [cyan]" + discordInviteLink);
                    return;
                }
                int rank = pd.rank;
                Call.sendMessage("[#" + Integer.toHexString(rankNames.get(rank).color.getRGB()).substring(2) + "]" + rankNames.get(rank).name + " [] " + player.name + "[accent] joined the front!");
                player.name = rankNames.get(rank).tag + player.name;
                // just give marshals admin when they join
                if (rank == rankNames.size() - 1) {
                    player.admin = true;
                }
            } else { // not in database
                info("New player connected: " + escapeColorCodes(event.player.name));
                setData(player.uuid(), new PlayerData(0));
//                Call.infoMessage(player.con, formatMessage(player, welcomeMessage));
                Call.sendMessage("[#" + Integer.toHexString(rankNames.get(0).color.getRGB()).substring(2) + "]" + rankNames.get(0).name + " [] " + player.name + "[accent] joined the front!");
            }
//
//            CompletableFuture.runAsync(() -> {
//                if(verification) {
//                    if (pd != null && !pd.verified) {
//                        CustomLog.info("Unverified player joined: " + player.name);
//                        String url = "http://api.vpnblocker.net/v2/json/" + player.con.address + "/" + apiKey;
//                        String pjson = ClientBuilder.newClient().target(url).request().accept(MediaType.APPLICATION_JSON).get(String.class);
//
//                        JSONObject json = new JSONObject(new JSONTokener(pjson));
//                        if (json.has("host-ip")) {
//                            if (json.getBoolean("host-ip")) { // verification failed
//                                CustomLog.info("IP verification failed for: " + player.name);
//                                Call.onInfoMessage(player.con, verificationMessage);
//                            } else {
//                                CustomLog.info("IP verification success for: " + player.name);
//                                pd.verified = true;
//                                setData(player.uuid(), pd);
//                            }
//                        } else { // site doesn't work for some reason  ?
//                            pd.verified = true;
//                            setData(player.uuid(), pd);
//                        }
//                    }
//                }
//            });
//            player.sendMessage(welcomeMessage);
            Call.infoMessage(player.con, welcomeMessage);

            if (!joinedPlayers.contains(player.uuid())) {
                joinedPlayers.add(player.uuid());
            }
        });

//        Events.on(EventType.d)

        // player built building
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            try {
                if (event.unit.getPlayer() == null) return;
                if (event.breaking) return;
                PersistentPlayerData td = playerDataGroup.get(event.unit.getPlayer().uuid());
                if (td == null) return;
                if (event.tile.block() != null) {
                    if (!bannedBlocks.contains(event.tile.block())) {
                        td.bbIncrementor++;
                    }
                }
            } catch (Exception e) {
                err("There was an error while saving block status: ");
                e.printStackTrace();
            }
        });

        // log all tile taps
        Events.on(EventType.TapEvent.class, tapEvent -> {
            if (tapEvent.tile != null) {
                Player player = tapEvent.player;
                PersistentPlayerData ppd = (playerDataGroup.getOrDefault(player.uuid(), null));

                Tile t = tapEvent.tile;
                if (ppd == null) return;
                ppd.tapTile = t;
                if (ppd.inspector) {
                    player.sendMessage("\n");
                    Call.effect(player.con, Fx.placeBlock, t.worldx(), t.worldy(), 0.75f, Pal.accent);
                    player.sendMessage("[orange]--[] [accent]tile [](" + t.x + ", " + t.y + ")[accent] block:[] " + ((t.block() == null || t.block() == Blocks.air) ? "[#545454]none" : t.block().name) + " [orange]--[]");
                    TileInfo info = tileInfoHashMap.getOrDefault(t, new TileInfo());
                    if (info.placedBy != null) {
                        String pBy = (player.admin() ? info.placedByUUID + " " + info.placedBy : info.placedBy);
                        player.sendMessage("[accent]last placed by:[] " + escapeColorCodes(pBy));
                    }
                    if (info.destroyedBy != null) {
                        String dBy = (player.admin() ? info.destroyedByUUID + " " + info.destroyedBy : info.destroyedBy);
                        player.sendMessage("[accent]last [scarlet]deconstructed[] by:[] " + escapeColorCodes(dBy));
                    }
                    if (t.block() == Blocks.air && info.wasHere != null) {
                        player.sendMessage("[accent]block that was here:[] " + info.wasHere);
                    }
                    if (info.configuredBy != null) {
                        String cBy = (player.admin() ? info.configuredByUUID + " " + info.configuredBy : info.configuredBy);
                        player.sendMessage("[accent]last configured by:[] " + escapeColorCodes(cBy));
                    }
                }
            }
        });

        Events.on(EventType.BuildSelectEvent.class, event -> {
            if (event.builder.isPlayer()) {
                if (event.tile != null) {
                    Player player = event.builder.getPlayer();
                    TileInfo info = tileInfoHashMap.getOrDefault(event.tile, new TileInfo());
                    if (!event.breaking) {
                        info.placedBy = player.name;
                        info.placedByUUID = player.uuid();
                        info.wasHere = (event.tile.block() != Blocks.air ? event.tile.block().localizedName : "[#545454]none");
                    } else {
                        info.destroyedBy = player.name;
                        info.destroyedByUUID = player.uuid();
                    }
                    tileInfoHashMap.put(event.tile, info);
                }
            }
        });

        Events.on(EventType.TapEvent.class, event -> {
            if (event.tile != null & event.player != null) {
                TileInfo info = tileInfoHashMap.getOrDefault(event.tile, new TileInfo());
                Player player = event.player;
                info.configuredBy = player.name;
                info.configuredByUUID = player.uuid();
                tileInfoHashMap.put(event.tile, info);
            }
        });


        Events.on(EventType.ServerLoadEvent.class, event -> {
            // action filter
            Vars.netServer.admins.addActionFilter(action -> {
                Player player = action.player;
                if (player == null) return true;

                if (player.admin) return true;

                return action.type != Administration.ActionType.rotate;
            });
        });

        Events.on(EventType.Trigger.update.getClass(), event -> {
            for (Player p : Groups.player) {
                PersistentPlayerData tdata = (playerDataGroup.getOrDefault(p.uuid(), null));
                if (tdata != null && tdata.bt != null && p.shooting()) {
                    Call.createBullet(tdata.bt, p.team(), p.getX(), p.getY(), p.unit().rotation, tdata.sclDamage, tdata.sclVelocity, tdata.sclLifetime);
                }
                if (tdata != null && tdata.snowBall && p.shooting()) {
//                    Effect.create(new Effect(), p.getX(), p.getY(), p.unit().rotation, new arc.graphics.Color(0xffffff), null);
//                    EffectHelper.onMove(p);

                    String key = "snowball";

                    final String name = EffectHelper.properties.get(key + ".name", "none");
                    if (name.equals("none")) {
                        debug("cant find effect " + key);
                        return;
                    }

                    final String color = EffectHelper.properties.get(key + ".color", "#ffffff");
                    final int rotation = Integer.parseInt(EffectHelper.properties.get(key + ".rotation", "0"));

                    final Effect eff = getEffect(name);
                    final EffectObject place = new EffectObject(eff, p.getX(), p.getY(), rotation, arc.graphics.Color.valueOf(color));

                    EffectHelper.on(key, player, (p.x / 8) + "," + (p.y / 8));
                }
            }
        });

//        rateMapTask = Timer.schedule(this::rateMenu, 120); // for the rateMenu to appear after 2 minutes after start
        Events.on(EventType.GameOverEvent.class, event -> {
            debug("Game over!");
            for (Player p : Groups.player) {
                PlayerData pd = getData(p.uuid());
                if (pd != null) {
                    pd.gamesPlayed++;
                    Call.infoMessage(p.con, "[accent]+1 games played");
                    setData(pd.uuid, pd);
                }
                PersistentPlayerData tdata = (playerDataGroup.getOrDefault(p.uuid(), null));
                if (tdata != null) {
                    tdata.votedMap = false;
                }

            }
            update(log_channel, api);

            // maybe update the highscore
            String mapName = state.map.name();
            MapData mapData = getMapData(mapName);
            if (mapData == null) {
                mapData = new MapData(mapName);
                mapData.playtime = 1;
            }
            mapData.highscoreWaves = Math.max(mapData.highscoreWaves, state.stats.wavesLasted);
            mapData.highscoreTime = Math.max(mapData.highscoreTime, passedMapTime);
            if (mapData.shortestGame != 0) {
                mapData.shortestGame = Math.min(mapData.shortestGame, passedMapTime);
            } else {
                mapData.shortestGame = passedMapTime;
            }
            rateMap(mapName, mapData);

            passedMapTime = 0;

            // log the game over
            assert log_channel != null;
            if (Groups.player.size() > 0) {
                EmbedBuilder gameOverEmbed = new EmbedBuilder().setTitle("Game over!").setDescription("Map " + escapeEverything(state.map.name()) + " ended with " + state.wave + " waves and " + Groups.player.size() + " players!").setColor(new Color(0x33FFEC));
                log_channel.sendMessage(gameOverEmbed);
                live_chat_channel.sendMessage(gameOverEmbed);
            }

//            // force map vote
//            rateMapTask.cancel(); // cancel the task if gameover before 5 min
//            rateMapTask = Timer.schedule(this::rateMenu, 120);
        });


        // TODO: log dangerous actions from players

//        Events.on(EventType.WorldLoadEvent.class, event -> {
//            Timer.schedule(MapRules::run, 5); // idk
//        });

        // TODO: remove this when MapRules is back in use
        Events.on(EventType.ServerLoadEvent.class, event -> {
            // action filter
            Vars.netServer.admins.addActionFilter(action -> {
                Player player = action.player;
                if (player == null) return true;

                // disable checks for admins
                if (player.admin) return true;

                PersistentPlayerData tdata = (playerDataGroup.getOrDefault(action.player.uuid(), null));
                if (tdata == null) { // should never happen
                    player.sendMessage("[scarlet]You may not build right now due to a server error, please tell an administrator");
                    return false;
                }

                switch (action.type) {
                    case rotate -> {
                        boolean hit = tdata.rotateRatelimit.get();
                        if (hit) {
                            player.sendMessage("[scarlet]Rotate ratelimit exceeded, please rotate slower");
                            return false;
                        }
                    }
                    case configure -> {
                        boolean hit = tdata.configureRatelimit.get();
                        if (hit) {
                            player.sendMessage("[scarlet]Configure ratelimit exceeded, please configure slower");
                            return false;
                        }
                    }
                }
                return true;
            });
            netServer.admins.addChatFilter((player, message) -> {
                assert player != null;
                PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
                assert tdata != null;
                if (tdata.muted) {
                    return null;
                }
                return message;
            });
            netServer.admins.addActionFilter(action -> {
                assert action.player != null;
                Player player = action.player;
                PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
                assert tdata != null;
                if (tdata.frozen) {
                    player.sendMessage("[cyan]You are frozen!");
                }
                return !tdata.frozen;
            });
            info("Registered all filters.");
        });
    }

    // rainbow
    public static void loop() {
        for (Player player : Groups.player) {
            try {
                PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
                if (tdata == null) {
                    continue;
                }
                if (tdata.doRainbow) {
                    int rank = Objects.requireNonNull(getData(player.uuid())).rank;
                    // update rainbows
                    String playerNameUnmodified = tdata.origName;
                    int hue = tdata.hue;
                    if (hue < 360) {
                        hue = hue + 5;
                    } else {
                        hue = 0;
                    }

                    String hex = "#" + Integer.toHexString(Color.getHSBColor(hue / 360f, 1f, 1f).getRGB()).substring(2);
                    if (rank < rankNames.size() && rank > -1) {
                        player.name = "[" + hex + "]" + escapeColorCodes(rankNames.get(rank).tag) + "[" + hex + "]" + escapeEverything(player.name);
                    }
                    tdata.setHue(hue);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean checkChatRatelimit(String message, Player player) {
        // copied almost exactly from mindustry core, will probably need updating
        // will also update the user's global chat ratelimits
        long resetTime = Config.messageRateLimit.num() * 1000L;
        if (Config.antiSpam.bool() && !player.isLocal() && !player.admin) {
            //prevent people from spamming messages quickly
            if (resetTime > 0 && Time.timeSinceMillis(player.getInfo().lastMessageTime) < resetTime) {
                //supress message
                player.sendMessage("[scarlet]You may only send messages every " + Config.messageRateLimit.num() + " seconds.");
                player.getInfo().messageInfractions++;
                //kick player for spamming and prevent connection if they've done this several times
                if (player.getInfo().messageInfractions >= Config.messageSpamKick.num() && Config.messageSpamKick.num() != 0) {
                    player.con.kick("You have been kicked for spamming.", 1000 * 60 * 2);
                }
                return false;
            } else {
                player.getInfo().messageInfractions = 0;
            }

            // prevent players from sending the same message twice in the span of 50 seconds
            if (message.equals(player.getInfo().lastSentMessage) && Time.timeSinceMillis(player.getInfo().lastMessageTime) < 1000 * 50) {
                player.sendMessage("[scarlet]You may not send the same message twice.");
                return false;
            }

            player.getInfo().lastSentMessage = message;
            player.getInfo().lastMessageTime = Time.millis();
        }
        return true;
    }

    public static void update(TextChannel log_channel, DiscordApi api) {
        try {
            if ((logCount & 5) == 0) {
                // log player joins
                logConnections(log_channel, joinedPlayers, "join");

                logConnections(log_channel, leftPlayers, "leave");
            }
            logCount++;
            for (Player p : Groups.player) {
                PlayerData pd = getData(p.uuid());
                if (pd == null) return;
//
                // update buildings built
                PersistentPlayerData tdata = (ioMain.playerDataGroup.getOrDefault(p.uuid(), null));
                if (tdata != null) {
                    if (tdata.bbIncrementor > 0) {
                        pd.buildingsBuilt = pd.buildingsBuilt + tdata.bbIncrementor;
                        tdata.bbIncrementor = 0;
                    }
                }
//
//
                pd.playTime++;
                // check if someone gets promoted
                for (var entry : rankRequirements.entrySet()) {
                    if (pd.rank <= entry.getKey() - 1 && pd.playTime >= entry.getValue().playtime && pd.buildingsBuilt >= entry.getValue().buildingsBuilt && pd.gamesPlayed >= entry.getValue().gamesPlayed) {
                        Call.infoMessage(p.con, Utils.formatMessage(p, promotionMessage));
                        if (pd.rank < entry.getKey()) pd.rank = entry.getKey();
                        info(escapeEverything(p) + " got promoted to " + rankNames.get(pd.rank).name + "!");
                    }
                }

                setData(p.uuid(), pd);
                playerDataGroup.put(p.uuid(), tdata); // update tdata with the new stuff
            }

            if (state.is(GameState.State.playing)) {
                if (Mathf.chance(0.01f)) {
                    api.updateActivity(lennyFace);
                    System.out.println(lennyFace);
                } else {
                    api.updateActivity("with " + Groups.player.size() + (netServer.admins.getPlayerLimit() == 0 ? "" : "/" + netServer.admins.getPlayerLimit()) + " players");
                }
            } else {
                api.updateActivity("Not hosting. Please Host a game. Ping an admin");
                // restart the server:
                Map result;
                Gamemode preset = Gamemode.survival;
                result = maps.getShuffleMode().next(preset, state.map);
                info("Randomized next map to be @.", result.name());
                world.loadMap(result, result.applyRules(preset));
                state.rules = result.applyRules(preset);
                logic.play();
                assert error_log_channel != null;
                error_log_channel.sendMessage(" <@770240444466069514> ");
                error_log_channel.sendMessage(new EmbedBuilder().setColor(new Color(0xff0000)).setTitle("Server crashed. Restarting!"));
                String command = "sh shellScripts/restart.sh";
                return;
//            try {
////                execute(command);
//                ProcessBuilder processBuilder = new ProcessBuilder("nohup", "sh", "./shellScripts/restart.sh");
//                try {
//                    System.out.println(processBuilder.command());
//                    processBuilder.directory(new File(System.getProperty("user.dir")));
//                    processBuilder.redirectErrorStream(false);
////                    processBuilder.start();
////                    net.dispose();
////                    Core.app.exit();
////                    System.exit(1);
//                    restartApplication();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            } catch (Exception e) {
//                error_log_channel.sendMessage(new EmbedBuilder()
//                        .setColor(new Color(0xff0000))
//                        .setTitle("Failed to restart server!")
//                        .setDescription(e.getMessage()));
//            }
            }

            // update the playtime of the current map
            String mapName = state.map.name();
            MapData mapData = getMapData(mapName);
            if (mapData != null) {
                mapData.playtime++;
            } else {
                mapData = new MapData(mapName);
                mapData.playtime = 1;
            }
            rateMap(mapName, mapData);
            passedMapTime++;

            debug("Updated database!");
        } catch (Exception e) {
            err("There was an error in the update loop: ");
            e.printStackTrace();
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        mapChange.registerServerCommands(handler);
        handler.register("update", "Update the database with the new current data", arg -> {
            TextChannel log_channel = getTextChannel("882342315438526525");
            update(log_channel, api);
        });
        handler.register("rate", "Force everyone to vote on the current map.", arg -> {
//            rateMapTask.cancel();
            rateMenu();
        });
        handler.register("vote", "<y/n/c>", "Vote for current votekick", arg -> {
            if (currentlyKicking[0] == null) {
                info("[scarlet]Nobody is being voted on.");
            } else {
                if (arg[0].equalsIgnoreCase("c")) {
                    currentlyKicking[0].map[0] = null;
                    currentlyKicking[0].task.cancel();
                    Call.sendMessage("[scarlet]Server []canceled the kick.");
                }

                int sign = switch (arg[0].toLowerCase()) {
                    case "y", "yes" -> 1;
                    case "n", "no" -> -1;
                    default -> 0;
                };

                if (sign == 0) {
                    info("[scarlet]Vote either 'y' (yes) or 'n' (no).");
                    return;
                }

                currentlyKicking[0].vote(sign);
            }
        });

        handler.register("logging", "<trace/debug> <true/false>", "Enable or disable logging for javacord.", args -> {
            if (!Objects.equals(args[1], "false") && !Objects.equals(args[1], "true")) {
                err("Second argument has to be true or false!");
            }
            switch (args[0]) {
                case "trace", "t" -> {
                    setTrace(Objects.equals(args[1], "true"));
                    info("Set trace logging to " + args[1]);
                }
                case "debug", "d" -> {
                    setDebug(Objects.equals(args[1], "true"));
                    info("Set debug to " + args[1]);
                }
                default -> {
                    err("Please select either trace or debug!");
                }
            }
        });
    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler) {
        if (api != null) {
            handler.removeCommand("t");
            handler.<Player>register("t", "<message...>", "Send a message only to your teammates.", (args, player) -> {
                String message = args[0];
                if (!checkChatRatelimit(message, player)) return;
                String raw = "[#" + player.team().color.toString() + "]<T> " + chatFormatter.format(player, message);
                Groups.player.each(p -> p.team() == player.team(), o -> o.sendMessage(raw, player, message));
            });

            handler.<Player>register("inspector", "Toggle inspector.", (args, player) -> {
                PersistentPlayerData pd = (playerDataGroup.getOrDefault(player.uuid(), null));
                pd.inspector = !pd.inspector;
                player.sendMessage((pd.inspector ? "Enabled" : "Disabled") + " the inspector.");
            });

            handler.<Player>register("enablejs", "<true/false> [time]", "Enable/Disable js command for everyone. (Time in minutes)", (arg, player) -> {
                PlayerData pd = getData(player.uuid());
                if (arg.length > 1) {
                    try {
                        Integer.parseInt(arg[1]);
                    } catch (Exception e) {
                        player.sendMessage("[scarlet]Second argument has to be an Integer!");
                    }
                }
                if (player.admin && Objects.requireNonNull(pd).rank >= 10) {
                    switch (arg[0]) {
                        case "true", "t" -> {
                            enableJs = true;
                            if (enableJsTask != null) {
                                enableJsTask.cancel();
                            }
                            enableJsTask = Timer.schedule(() -> {
                                enableJs = false;
                                Call.sendMessage("[accent]js command disabled for everyone!");
                            }, arg.length > 1 ? Integer.parseInt(arg[1]) * 60 : 10 * 60);
                            Call.sendMessage("[accent]Marshal " + player.name + "[accent] enabled the js command for everyone" + (" for " + (enableJs ? (arg.length > 1 ? arg[1] : "10") + " minutes!" : "!")) + "Do [cyan]/js <script...>[accent] to use it.");
                        }
                        case "false", "f" -> {
                            enableJs = false;
                            Call.sendMessage("[accent]js command disabled for everyone!");
                        }
                        default -> {
                            player.sendMessage("[scarlet]Second argument has to be true or false.");
                            return;
                        }
                    }
                    player.sendMessage((enableJs ? "[green]Enabled[accent]" : "[scarlet]Disabled[accent]") + " js for everyone" + (" for " + (enableJs ? (arg.length > 1 ? arg[1] : "10") + " minutes!" : "!")));
                } else {
                    player.sendMessage("[scarlet]This command is restricted to admins!");
                }
            });

            handler.<Player>register("js", "<script...>", "Run arbitrary Javascript.", (arg, player) -> {
                PlayerData pd = getData(player.uuid());
                if ((player.admin && Objects.requireNonNull(pd).rank >= 9) || enableJs) {
                    player.sendMessage(mods.getScripts().runConsole(arg[0]));
                } else {
                    player.sendMessage("[scarlet]This command is restricted to admins!");
                }
            });

            handler.<Player>register("votekick", "[player...]", "votekick a player.", (args, player) -> {
//               CustomLog.debug("vk @.", args[0]);
                if (!Config.enableVotekick.bool()) {
                    player.sendMessage("[scarlet]Vote-kick is disabled on this server.");
                    return;
                }

                if (Groups.player.size() < 3) {
                    player.sendMessage("[scarlet]At least 3 players are needed to start a votekick.");
                    return;
                }

                if (player.isLocal()) {
                    player.sendMessage("[scarlet]Just kick them yourself if you're the host.");
                    return;
                }

                if (currentlyKicking[0] != null) {
                    player.sendMessage("[scarlet]A vote is already in progress.");
                    return;
                }

                if (args.length == 0) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("[orange]Players to kick: \n");

                    Groups.player.each(p -> !p.admin && p.con != null && p != player, p -> {
                        builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id()).append(")\n");
                    });
                    player.sendMessage(builder.toString());
                } else {
                    Player found = findPlayer(args[0]);
                    if (found == null) {
                        if (args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))) {
                            int id = Strings.parseInt(args[0].substring(1));
                            found = Groups.player.find(p -> p.id() == id);
                        } else {
                            found = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
                        }
                    }

                    if (found != null) {
                        if (found == player) {
                            player.sendMessage("[scarlet]You can't vote to kick yourself.");
                        } else if (found.admin) {
                            player.sendMessage("[scarlet]Did you really expect to be able to kick an admin?");
                        } else if (found.isLocal()) {
                            player.sendMessage("[scarlet]Local players cannot be kicked.");
                        } else if (found.team() != player.team()) {
                            player.sendMessage("[scarlet]Only players on your team can be kicked.");
                        } else if (Objects.equals(found.uuid(), "VA8X0BlqyTsAAAAAFkLMBg==")) {
                            player.sendMessage("[scarlet]Did you really expect to be able to kick [cyan]Nautilus[scarlet]?");
                        } else {
                            Timekeeper vtime = cooldowns.get(player.uuid(), () -> new Timekeeper(voteCooldown));

                            if (!vtime.get()) {
                                player.sendMessage("[scarlet]You must wait " + voteCooldown / 60 + " minutes between votekicks.");
                                return;
                            }

                            VoteSession session = new VoteSession(currentlyKicking, found);
                            session.vote(player, 1);

                            // freeze the player
                            PersistentPlayerData tdata = playerDataGroup.get(found.uuid());
                            if (tdata != null) {
                                tdata.frozen = !tdata.frozen;
                                player.sendMessage("[cyan]Successfully " + (tdata.frozen ? "froze" : "thawed") + " " + escapeEverything(found));
                                found.sendMessage("[cyan]You got " + (tdata.frozen ? "frozen" : "thawed") + " during the votekick!");
                            }

                            vtime.reset();
                            currentlyKicking[0] = session;
                        }
                    } else {
                        player.sendMessage("[scarlet]No player [orange]'" + args[0] + "'[scarlet] found.");
                    }
                }
            });

            handler.<Player>register("vote", "<y/n/c>", "Vote to kick the current player. Or cancel the current kick.", (arg, player) -> {
                if (currentlyKicking[0] == null) {
                    player.sendMessage("[scarlet]Nobody is being voted on.");
                } else {
                    if (arg[0].equalsIgnoreCase("c")) {
                        if (currentlyKicking[0].startedVk == player || player.admin) {
                            currentlyKicking[0].cancel(player);
                        } else {
                            player.sendMessage("[scarlet]This command is restricted to the player who started the votekick and admins");
                        }
                        return;
                    }

                    if (player.isLocal()) {
                        player.sendMessage("[scarlet]Local players can't vote. Kick the player yourself instead.");
                        return;
                    }

                    //hosts can vote all they want
                    if ((currentlyKicking[0].voted.contains(player.uuid()) || currentlyKicking[0].voted.contains(netServer.admins.getInfo(player.uuid()).lastIP))) {
                        player.sendMessage("[scarlet]You've already voted. Sit down.");
                        return;
                    }

                    if (currentlyKicking[0].target == player) {
                        player.sendMessage("[scarlet]You can't vote on your own trial.");
                        return;
                    }

                    if (currentlyKicking[0].target.team() != player.team()) {
                        player.sendMessage("[scarlet]You can't vote for other teams.");
                        return;
                    }

                    int sign = switch (arg[0].toLowerCase()) {
                        case "y", "yes" -> 1;
                        case "n", "no" -> -1;
                        default -> 0;
                    };

                    if (sign == 0) {
                        player.sendMessage("[scarlet]Vote either 'y' (yes) or 'n' (no).");
                        return;
                    }

                    currentlyKicking[0].vote(player, sign);
                }
            });

            handler.<Player>register("redeem", "<key>", "Verify the redeem command (Discord)", (arg, player) -> {
                try {
                    PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
                    if (tdata.redeemKey != -1) {
                        if (Integer.parseInt(arg[0]) == tdata.redeemKey) {
                            StringBuilder roleList = new StringBuilder();
                            for (java.util.Map.Entry<String, Integer> entry : rankRoles.entrySet()) {
                                PlayerData pd = getData(player.uuid());
                                assert pd != null;
                                if (entry.getValue() <= pd.rank) {
                                    System.out.println("add role: " + api.getRoleById(entry.getKey()).get());
                                    roleList.append("<@").append(api.getRoleById(entry.getKey()).get().getIdAsString()).append(">\n");
                                    ioMain.api.getUserById(tdata.redeem).get().addRole(api.getRoleById(entry.getKey()).get());
                                }
                            }
                            System.out.println(roleList);
                            getTextChannel(log_channel_id).sendMessage(new EmbedBuilder().setTitle("Updated roles!").addField("Discord Name", ioMain.api.getUserById(tdata.redeem).get().getName(), true).addField("In Game Name", tdata.origName, true).addField("In Game UUID", player.uuid(), true).addField("Added roles", roleList.toString(), true));
                            player.sendMessage("Successfully redeem to account: [green]" + ioMain.api.getUserById(tdata.redeem).get().getName());
                            tdata.task.cancel();
                        } else {
                            player.sendMessage("[scarlet]Wrong code!");
                        }

                        tdata.redeemKey = -1;
                    } else {
                        player.sendMessage("Please use the redeem command on the discord server first");
                    }
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    player.sendMessage("[scarlet]There was an error: " + e.getMessage());
                }
            });

            handler.<Player>register("snowball", "<id>", "Shoot snowballs!", (arg, player) -> {
                PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
                if (tdata != null) {
                    tdata.snowBall = !tdata.snowBall;
                    effectId = Integer.parseInt(arg[0]);
                }
            });

            handler.<Player>register("translate", "<language> <text...>", "Translate your message", (arg, player) -> {
                try {
                    JSONObject res = new JSONObject(Translate.translate(escapeEverything(arg[1]), arg[0]));
                    if (res.has("translated") && res.getJSONObject("translated").has("text")) {
                        String translated = res.getJSONObject("translated").getString("text");
                        debug(translated);
                        Call.sendMessage("<translated>[orange][[[accent]" + player.name + "[orange]][white]: " + translated);
                        TextChannel tc = getTextChannel(live_chat_channel_id);
                        assert tc != null;
                        tc.sendMessage("<translated>**" + escapeEverything(player.name) + "**: " + translated);
                    } else {
                        debug(res);
                        player.sendMessage("[scarlet]There was an error: " + (res.has("error") ? res.getString("error") : "No more information, ask Nautilus on discord!"));
                    }
                } catch (Exception e) {
                    player.sendMessage("[scarlet]There was an error: " + e.getMessage());
                }
            });

//            handler.<Player>register("d", "<text...>", "Sends a message to moderators. Use when no moderators are online and there's a griefer.", (args, player) -> {
//                if (!data.has("warnings_chat_channel_id")) {
//                    player.sendMessage("[scarlet]This command is disabled.");
//                } else {
//                    String message = args[0];
//                    if (!checkChatRatelimit(message, player)) return;
//                    TextChannel tc = getTextChannel(data.getString("warnings_chat_channel_id"));
//                    if (tc == null) {
//                        player.sendMessage("[scarlet]This command is disabled.");
//                        return;
//                    }
//                    tc.sendMessage(escapeCharacters(player.name) + " *@mindustry* : `" + message + "`");
//                    player.sendMessage("[scarlet]Successfully sent message to moderators.");
//                }
//            });

            handler.<Player>register("rate", "<positive|negative|advice> [advice...]", "Rate the current map.", (args, player) -> {
                String mapName = Vars.state.map.name();
                TextChannel map_rating_channel = getTextChannel(map_rating_channel_id);
                PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
                switch (args[0]) {
                    case "positive" -> {
                        if (!tdata.votedMap) {
                            if (!state.gameOver) {
                                ratePositive(mapName, player);
                                tdata.votedMap = true;
                            } else {
                                player.sendMessage("[scarlet]The game is already over!");
                            }
                        } else {
                            player.sendMessage("[scarlet]You already voted in this game!");
                        }
                    }
                    case "negative" -> {
                        if (!tdata.votedMap) {
                            if (!state.gameOver) {
                                rateNegative(mapName, player);
                                tdata.votedMap = true;
                            } else {
                                player.sendMessage("[scarlet]The game is already over!");
                            }
                        } else {
                            player.sendMessage("[scarlet]You already voted in this game!");
                        }
                    }
                    case "advice" -> {
                        EmbedBuilder eb = new EmbedBuilder().setTitle("Feedback for map " + escapeEverything(mapName) + "!").addField("Advice", args[1]).addField("By", escapeEverything(player.name));
                        assert map_rating_channel != null;
                        map_rating_channel.sendMessage(eb);
                        player.sendMessage("Successfully gave an [cyan]advice [white]for " + mapName + "[white]!");
                    }
                    default -> {
                        player.sendMessage("[white]Select [cyan]/rate [green]positive[white], [scarlet]negative [white]or [white]advice[white]!");
                    }
                }
            });

            handler.<Player>register("freeze", "<player> [reason...]", "Freeze a player. To unfreeze just use this command again.", (args, player) -> {
                if (player.admin()) {
                    Player target = findPlayer(args[0]);
                    if (target != null) {
                        PersistentPlayerData tdata = (playerDataGroup.getOrDefault(target.uuid(), null));
                        assert tdata != null;
                        tdata.frozen = !tdata.frozen;
                        player.sendMessage("[cyan]Successfully " + (tdata.frozen ? "froze" : "thawed") + " " + escapeEverything(target));
                        Call.infoMessage(target.con, "[cyan]You got " + (tdata.frozen ? "frozen" : "thawed") + " by a moderator. " + (args.length > 1 ? "Reason: " + args[1] : ""));
                    } else {
                        player.sendMessage("Player not found!");
                    }
                } else {
                    player.sendMessage(noPermissionMessage);
                }
            });

            handler.<Player>register("mute", "<player> [reason...]", "Mute a player. To unmute just use this command again.", (args, player) -> {
                if (player.admin()) {
                    Player target = findPlayer(args[0]);
                    if (target != null) {
                        PersistentPlayerData tdata = (playerDataGroup.getOrDefault(target.uuid(), null));
                        assert tdata != null;
                        tdata.muted = !tdata.muted;
                        player.sendMessage("[cyan]Successfully " + (tdata.muted ? "muted" : "unmuted") + " " + escapeEverything(target));
                        Call.infoMessage(target.con, "[cyan]You got " + (tdata.muted ? "muted" : "unmuted") + " by a moderator. " + (args.length > 1 ? "Reason: " + args[1] : ""));
                    } else {
                        player.sendMessage("Player not found!");
                    }
                } else {
                    player.sendMessage(noPermissionMessage);
                }
            });


            handler.<Player>register("bug", "[description...]", "Send a bug report to the discord server. (Please do not spam, because this command pings developers)", (args, player) -> {
                for (Long key : CommandCooldowns.keys()) {
                    if (key + CDT < System.currentTimeMillis() / 1000L) {
                        CommandCooldowns.remove(key);
                    } else if (player.uuid().equals(CommandCooldowns.get(key))) {
                        player.sendMessage("[scarlet]This command is on a 5 minute cooldown!");
                        return;
                    }
                }

                if (args.length == 0) {
                    player.sendMessage("[orange]Please describe exactly what the bug is or how you got it!\n");
                } else {
                    TextChannel bugReportChannel = getTextChannel("864957934513684480");
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("Player " + escapeEverything(player) + " reported a bug!");
                    eb.setDescription("Description: " + String.join(" ", args));
                    assert bugReportChannel != null;
                    bugReportChannel.sendMessage(" <@770240444466069514> ");
                    bugReportChannel.sendMessage(eb);
                    Call.sendMessage("[sky]The bug is reported to discord.");
                    CommandCooldowns.put(System.currentTimeMillis() / 1000L, player.uuid());
                }
            });

            handler.<Player>register("players", "Display all players and their ids", (args, player) -> {
                StringBuilder builder = new StringBuilder();
                builder.append("[orange]List of players: \n");
                for (Player p : Groups.player) {
                    if (p.admin) {
                        builder.append("[accent]");
                    } else {
                        builder.append("[lightgray]");
                    }
                    builder.append(p.name).append("[accent] : ").append(p.id).append("\n");
                }
                player.sendMessage(builder.toString());
            });

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

            TextChannel tc_c = getTextChannel("881300595875643452");
            handler.<Player>register("gr", "[player] [reason...]", "Report a griefer by id (use '/gr' to get a list of ids)", (args, player) -> {
                //https://github.com/Anuken/Mindustry/blob/master/core/src/io/anuke/mindustry/core/NetServer.java#L300-L351
                for (Long key : CommandCooldowns.keys()) {
                    if (key + CDT < System.currentTimeMillis() / 1000L) {
                        CommandCooldowns.remove(key);
                    } else if (player.uuid().equals(CommandCooldowns.get(key))) {
                        player.sendMessage("[scarlet]This command is on a 5 minute cooldown!");
                        return;
                    }
                }

                if (args.length == 0) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("[orange]List or reportable players: \n");
                    for (Player p : Groups.player) {
                        if (p.admin() || p.con == null) continue;

                        builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id).append(")\n");
                    }
                    player.sendMessage(builder.toString());
                } else {
                    Player found = null;
                    if (args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))) {
                        int id = Strings.parseInt(args[0].substring(1));
                        for (Player p : Groups.player) {
                            if (p.id == id) {
                                found = p;
                                break;
                            }
                        }
                    } else {
                        found = findPlayer(args[0]);
                    }
                    if (found != null) {
                        if (found.admin()) {
                            player.sendMessage("[scarlet]Did you really expect to be able to report an admin?");
                        } else if (found.team() != player.team()) {
                            player.sendMessage("[scarlet]Only players on your team can be reported.");
                        } else {
                            //send message
                            if (args.length > 1) {
                                Role ro = discRoles.get("861523420076179457");
//                                Role role = .getRoleById(661155250123702302L);
                                new MessageBuilder().setEmbed(new EmbedBuilder().setTitle("Potential griefer online")
//                                                .setDescription("<@&861523420076179457>")
                                        .addField("name", escapeColorCodes(found.name)).addField("reason", args[1]).setColor(Color.RED).setFooter("Reported by " + player.name)).send(tc_c);
                                assert tc_c != null;
                                tc_c.sendMessage("<@&882340213551140935>");
                            } else {
                                Role ro = discRoles.get("861523420076179457");
                                new MessageBuilder().setEmbed(new EmbedBuilder().setTitle("Potential griefer online")
//                                                .setDescription("<@&861523420076179457>")
                                        .addField("name", escapeColorCodes(found.name)).setColor(Color.RED).setFooter("Reported by " + player.name)).send(tc_c);
                                assert tc_c != null;
                                tc_c.sendMessage("<@&882340213551140935>");
                            }
                            Call.sendMessage(found.name + "[sky] is reported to discord.");
                            CommandCooldowns.put(System.currentTimeMillis() / 1000L, player.uuid());
                        }
                    } else {
                        player.sendMessage("[scarlet]No player[orange] '" + args[0] + "'[scarlet] found.");
                    }
                }
            });

            handler.<Player>register("discord", "Place a message block below a player with links for our discord server.", (args, player) -> {
                float x = player.getX();
                float y = player.getY();
                Tile tile = world.tileWorld(x, y);
                if ((tile.block() == null || tile.block() == Blocks.air)) {
                    tile.setNet(Blocks.message, player.team(), 0);
                    tile.build.configure("https://discord.phoenix-network.dev\n\nor\n\nhttps://discord.gg/qtjqCUbbdR");
                    player.sendMessage("[green]Successfully placed a message block.");
                } else {
                    player.sendMessage("[scarlet]Cant place a message block here, because there is already a block here!");
                }
            });

//            handler.<Player>register("draugpet", "[active+] Spawn a draug mining drone for your team (disabled on pvp)", (args, player) -> {
//                if(!state.rules.pvp || player.isAdmin) {
//                    PlayerData pd = getData(player.uuid());
//                    if (pd != null && pd.rank >= 1) {
//                        PersistentPlayerData tdata = playerDataGroup.get(player.uuid());
//                        if (tdata == null) return;
//                        if (tdata.draugPets.size < pd.rank || player.isAdmin) {
//                            BaseUnit baseUnit = UnitTypes.draug.create(player.getTeam());
//                            baseUnit.set(player.getX(), player.getY());
//                            baseUnit.add();
//                            tdata.draugPets.add(baseUnit);
//                            Call.sendMessage(player.name + "[#b177fc] spawned in a draug pet! " + tdata.draugPets.size + "/" + pd.rank + " spawned.");
//                        } else {
//                            player.sendMessage("[#b177fc]You already have " + pd.rank + " draug pets active!");
//                        }
//                    } else {
//                        player.sendMessage(noPermissionMessage);
//                    }
//                } else {
//                    player.sendMessage("[scarlet]This command is disabled on pvp.");
//                }
//            });

//            handler.<Player>register("lichpet", "[contributor+] Spawn yourself a lich defense pet (max. 1 per game, lasts 2 minutes, disabled on pvp)", (args, player) -> {
//                if(!state.rules.pvp || player.isAdmin) {
//                    PlayerData pd = getData(player.uuid());
//                    if (pd != null && pd.rank >= 3) {
//                        PersistentPlayerData tdata = playerDataGroup.get(player.uuid());
//                        if (tdata == null) return;
//                        if (!tdata.spawnedLichPet || player.isAdmin) {
//                            tdata.spawnedLichPet = true;
//                            BaseUnit baseUnit = UnitTypes.lich.create(player.getTeam());
//                            baseUnit.set(player.getClosestCore().x, player.getClosestCore().y);
//                            baseUnit.health = 200f;
//                            baseUnit.add();
//                            Call.sendMessage(player.name + "[#ff0000] spawned in a lich defense pet! (lasts 2 minutes)");
//                            Timer.schedule(baseUnit::kill, 120);
//                        } else {
//                            player.sendMessage("[#42a1f5]You already spawned a lich defense pet in this game!");
//                        }
//                    } else {
//                        player.sendMessage(noPermissionMessage);
//                    }
//                } else {
//                    player.sendMessage("[scarlet]This command is disabled on pvp.");
//                }
//            });

//            handler.<Player>register("powergen", "[contributor+] Spawn yourself a power generator.", (args, player) -> {
//                if(!state.rules.pvp || player.isAdmin) {
//                    PlayerData pd = getData(player.uuid());
//                    if (pd != null && pd.rank >= 3) {
//                        PersistentPlayerData tdata = playerDataGroup.get(player.uuid());
//                        if (tdata == null) return;
//                        if (!tdata.spawnedPowerGen || player.isAdmin) {
//                            float x = player.getX();
//                            float y = player.getY();
//
//                            Tile targetTile = world.tileWorld(x, y);
//
//                            if (targetTile == null || !Build.validPlace(player.getTeam(), targetTile.x, targetTile.y, Blocks.rtgGenerator, 0)) {
//                                Call.onInfoToast(player.con, "[scarlet]Cannot place a power generator here.",5f);
//                                return;
//                            }
//
//                            tdata.spawnedPowerGen = true;
//                            targetTile.setNet(Blocks.rtgGenerator, player.getTeam(), 0);
//                            Call.onLabel("[accent]" + escapeCharacters(escapeColorCodes(player.name)) + "'s[] generator", 60f, targetTile.worldx(), targetTile.worldy());
//                            Call.onEffectReliable(Fx.explosion, targetTile.worldx(), targetTile.worldy(), 0, Pal.accent);
//                            Call.onEffectReliable(Fx.placeBlock, targetTile.worldx(), targetTile.worldy(), 0, Pal.accent);
//                            Call.sendMessage(player.name + "[#ff82d1] spawned in a power generator!");
//
//                            // ok seriously why is this necessary
//                            new Object() {
//                                private Task task;
//                                {
//                                    task = Timer.schedule(() -> {
//                                        if (targetTile.block() == Blocks.rtgGenerator) {
//                                            Call.transferItemTo(Items.thorium, 1, targetTile.drawx(), targetTile.drawy(), targetTile);
//                                        } else {
//                                            player.sendMessage("[scarlet]Your power generator was destroyed!");
//                                            task.cancel();
//                                        }
//                                    }, 0, 6);
//                                }
//                            };
//                        } else {
//                            player.sendMessage("[#ff82d1]You already spawned a power generator in this game!");
//                        }
//                    } else {
//                        player.sendMessage(noPermissionMessage);
//                    }
//                } else {
//                    player.sendMessage("[scarlet]This command is disabled on pvp.");
//                }
//            });

//            handler.<Player>register("spawn", "[active+]Skip the core spawning stage and spawn instantly.", (args, player) -> {
//                if(!state.rules.pvp || player.isAdmin) {
//                    PlayerData pd = getData(player.uuid());
//                    if (pd != null && pd.rank >= 1) {
//                        player.onRespawn(player.getClosestCore().tile);
//                        player.sendMessage("[accent]Spawned!");
//                    } else {
//                        player.sendMessage(noPermissionMessage);
//                    }
//                } else {
//                    player.sendMessage("[scarlet]This command is disabled on pvp.");
//                }
//            });

            handler.<Player>register("rainbow", "Give your username a rainbow animation", (args, player) -> {
                PlayerData pd = getData(player.uuid());
                if (pd == null) {
                    player.sendMessage("There was an error!");
                    return;
                }
                if (pd.rank >= 0) {
                    PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
                    if (tdata == null) return; // shouldn't happen, ever
                    if (tdata.doRainbow) {
                        player.sendMessage("[sky]Rainbow effect toggled off.");
                        tdata.doRainbow = false;
                        player.name = rankNames.get(pd.rank).tag + netServer.admins.getInfo(player.uuid()).names.get(0);
                    } else {
                        player.sendMessage("[sky]Rainbow effect toggled on.");
                        tdata.doRainbow = true;
                    }
                } else {
                    player.sendMessage(noPermissionMessage);
                }
            });

            handler.<Player>register("stats", "[player]", "Display stats of the specified player.", (args, player) -> {
                if (args.length > 0) {
                    Player p = findPlayer(args[0]);
                    if (p != null) {
                        PlayerData pd = getData(p.uuid());
                        if (pd != null) {
                            Call.infoMessage(player.con, formatMessage(p, statMessage));
                        }
                    } else {
                        player.sendMessage("[scarlet]Error: Player not found or offline");
                    }
                } else {
                    Call.infoMessage(player.con, formatMessage(player, statMessage));
                }
            });

            handler.<Player>register("info", "Display info about our server.", (args, player) -> {
                Call.infoMessage(player.con, infoMessage);
            });

            handler.<Player>register("rules", "Server rules. Please read carefully.", (args, player) -> {
                Call.infoMessage(player.con, ruleMessage);
            });

            handler.<Player>register("event", "Join an ongoing event (if there is one)", (args, player) -> {
                if (eventIp.length() > 0) {
                    Call.connect(player.con, eventIp, eventPort);
                } else {
                    player.sendMessage("[scarlet]There is no ongoing event at this time.");
                }
            });

            handler.<Player>register("maps", "[page]", "Display all maps in the playlist.", (args, player) -> { // self info
                if (args.length > 0 && !Strings.canParseInt(args[0])) {
                    player.sendMessage("[scarlet]'page' must be a number.");
                    return;
                }
                int commandsPerPage = 6;
                int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
                int pages = Mathf.ceil((float) Vars.maps.customMaps().size / commandsPerPage);

                page--;

                if (page >= pages || page < 0) {
                    player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] " + pages + "[scarlet].");
                    return;
                }

                StringBuilder result = new StringBuilder();
                result.append("[orange]-- Maps Page[lightgray] ").append(page + 1).append("[gray]/[lightgray]").append(pages).append("[orange] --\n\n");

                for (int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), Vars.maps.customMaps().size); i++) {
                    mindustry.maps.Map map = Vars.maps.customMaps().get(i);
                    result.append("[white] - [accent]").append(escapeColorCodes(map.name())).append("\n");
                }
                player.sendMessage(result.toString());
            });

            Timekeeper vtime = new Timekeeper(voteCooldown);

            final MapVoteSession[] currentMapVoting = {null};

//            handler.<Player>register("changemap", "[map...]", " Vote to change to a specific map.", (args, player) -> {
//                if (!state.rules.pvp || player.admin) {
//                    if (currentMapVoting[0][0] != null) {
//                        player.sendMessage("[scarlet]There is already a map being voted on. Type /rtv to vote.");
//                        return;
//                    }
//                    mindustry.maps.Map found;
//                    if (args.length > 0) {
//                        found = getMapBySelector(args[0]);
//                    } else {
//                        found = getMapBySelector(String.valueOf((int) (Math.random() * 5)));
//                    }
//
//                    if (found != null) {
//                        if (!vtime.get()) {
//                            player.sendMessage("[scarlet]You must wait " + voteCooldown / 20 + " minutes between nominations.");
//                            return;
//                        }
//
//                        MapVoteSession session = new MapVoteSession(currentMapVoting[0], found);
//
//                        session.vote(player, 1);
//                        vtime.reset();
//                        currentMapVoting[0][0] = session;
//                    } else {
//                        player.sendMessage("[scarlet]No map[orange]'" + args[0] + "'[scarlet] found.");
//                    }
////                    } else {
////                        player.sendMessage(noPermissionMessage);
////                    }
//                } else {
//                    player.sendMessage("[scarlet]This command is disabled on pvp.");
//                }
//            });

            handler.<Player>register("rtv", "[map...]", "Vote to change the map.", (args, player) -> {
                if (currentMapVoting[0] == null) {
                    mindustry.maps.Map found;
                    if (args.length > 0) {
                        found = getMapBySelector(args[0]);
                        if (found == null) {
                            String targetMap = escapeEverything(args[0]);
                            for (Map map : maps.customMaps()) {
                                if (escapeEverything(map.name()).startsWith(targetMap)) {
                                    found = map;
                                    break;
                                }
                            }
                        }
                    } else {
                        found = getMapBySelector(String.valueOf((int) (Math.random() * 5)));
                    }
                    if (found != null) {
                        if (!vtime.get()) {
                            player.sendMessage("[scarlet]You must wait " + voteCooldown / 20 + " minutes between nominations.");
                            return;
                        }

                        MapVoteSession session = new MapVoteSession(currentMapVoting[0], found);

                        if (!session.vote(player, 1)) {
                            currentMapVoting[0] = session;
                        } else {
                            currentMapVoting[0] = null;
                        }
                        vtime.reset();
                    } else {
                        player.sendMessage("[scarlet]No map[orange]'" + args[0] + "'[scarlet] found.");
                    }
                } else {
                    //hosts can vote all they want
                    if (player.uuid() != null &&
                            (currentMapVoting[0].voted.contains(player.uuid()) ||
                                    currentMapVoting[0].voted.contains(netServer.admins.getInfo(player.uuid()).lastIP))) {
                        player.sendMessage("[scarlet]You've already voted. Sit down.");
                        return;
                    }

                    if (currentMapVoting[0].vote(player, 1)) {
                        currentMapVoting[0] = null;
                    }
                }
            });

//            mapChange.registerClientCommands(handler);

//            handler.<Player>register("rtv", "Vote to change the map.", (args, player) -> { // self info
//                if (currentMapVoting[0][0] == null) {
//                    player.sendMessage("[scarlet]No map is being voted on.");
//                } else {
//                    //hosts can vote all they want
//                    if (player.uuid() != null && (currentMapVoting[0][0].voted.contains(player.uuid()) || currentMapVoting[0][0].voted.contains(netServer.admins.getInfo(player.uuid()).lastIP))) {
//                        player.sendMessage("[scarlet]You've already voted. Sit down.");
//                        return;
//                    }
//
//                    currentMapVoting[0][0].vote(player, 1);
//                }
//            });

            handler.<Player>register("req", "Show the requirements for all ranks", (args, player) -> { // self info
//                for (Map.Entry<Integer, Rank> rank : rankNames.entrySet()) {
////                    if(rank.getValue().name.equals(args[1])) {
////                        player.sendMessage("");
////                    }
//                }
//                Call.infoMessage(player.con, formatMessage(player, reqMessage));
                Call.infoMessage(player.con, formatMessage(player, listRequirements()));
            });

            handler.<Player>register("reset", "Set everyone's name back to the original name.", (args, player) -> {
                if (player.admin) {
                    for (Player p : Groups.player) {
                        PlayerData pd = getData(p.uuid());
                        PersistentPlayerData tdata = (playerDataGroup.getOrDefault(p.uuid(), null));
                        if (tdata == null) continue; // shouldn't happen, ever
                        tdata.doRainbow = false;
                        if (pd == null) continue;
                        p.name = rankNames.get(pd.rank).tag + netServer.admins.getInfo(p.uuid()).lastName;
                    }
                    player.sendMessage("[cyan]Reset names!");
                } else {
                    player.sendMessage(noPermissionMessage);
                }
            });

            handler.<Player>register("ranks", "Show for all ranks.", (args, player) -> { // self info
//                for (Map.Entry<Integer, Rank> rank : rankNames.entrySet()) {
////                    if(rank.getValue().name.equals(args[1])) {
////                        player.sendMessage("");
////                    }
//                }
//                Call.infoMessage(player.con, formatMessage(player, rankMessage));
                Call.infoMessage(player.con, formatMessage(player, inGameListRanks()));
            });

            handler.<Player>register("label", "<duration> <text...>", "[admin only] Create an in-world label at the current position.", (args, player) -> {
                if (args[0].length() <= 0 || args[1].length() <= 0)
                    player.sendMessage("[scarlet]Invalid arguments provided.");
                if (player.admin) {
                    float x = player.getX();
                    float y = player.getY();

                    Tile targetTile = world.tileWorld(x, y);
                    Call.label(args[1], Float.parseFloat(args[0]), targetTile.worldx(), targetTile.worldy());
                } else {
                    player.sendMessage(noPermissionMessage);
                }
            });

        }

    }
}