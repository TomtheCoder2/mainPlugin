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
import mindustry.plugin.data.TileInfo;
import mindustry.plugin.effect.EffectHelper;
import mindustry.plugin.effect.EffectObject;
import mindustry.plugin.minimods.Discord;
import mindustry.plugin.utils.ContentHandler;
import mindustry.plugin.utils.Utils;
import mindustry.plugin.utils.Rank;
import mindustry.world.Tile;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.TextChannel;
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

import mindustry.plugin.database.Database;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordVars;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;

import static arc.util.Log.*;
import static mindustry.Vars.*;
import static mindustry.plugin.effect.EffectHelper.getEffect;
import static mindustry.plugin.utils.DiscordLog.logConnections;
import static mindustry.plugin.utils.Utils.*;
import static org.javacord.api.util.logging.FallbackLoggerConfiguration.setDebug;
import static org.javacord.api.util.logging.FallbackLoggerConfiguration.setTrace;

public class ioMain extends Plugin {
    //    public static final File prefsFile = new File("prefs.properties");
//    public static Net net = new Net();
//    public static Prefs prefs = new Prefs(prefsFile);
//    public GetMap map = new GetMap();
    public static final Fi pluginDir = new Fi("./config/mods/");
    public static final long CDT = 300L;
    public static final ObjectMap<Long, String> CommandCooldowns = new ObjectMap<>(); // uuid
    private static final String lennyFace = "( \u0361\u00B0 \u035C\u0296 \u0361\u00B0)";
    public static String apiKey = "";
    public static String discordInviteLink;
    public static int effectId = 0; // effect id for the snowball
    public static ArrayList<String> joinedPlayers = new ArrayList<>();
    public static List<String> leftPlayers = new ArrayList<>();
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
    //    public ObjectMap<String, TextChannel> discChannels = new ObjectMap<>();
    //    private final String fileNotFoundErrorMessage = "File not found: config\\mods\\settings.json";
    public static ObjectMap<String, Role> discRoles = new ObjectMap<>();
    public static NetServer.ChatFormatter chatFormatter = (player, message) -> player == null ? message : "[coral][[" + player.coloredName() + "[coral]]:[white] " + message;

    protected MiniMod[] minimods = new MiniMod[]{
            new mindustry.plugin.minimods.RTV(),
            new mindustry.plugin.minimods.JS(),
            new mindustry.plugin.minimods.Communication(),
            new mindustry.plugin.minimods.Management(),
            new mindustry.plugin.minimods.Discord(),
            new mindustry.plugin.minimods.Info(),
            new mindustry.plugin.minimods.Ranks(),
            new mindustry.plugin.minimods.Moderation(),
            new mindustry.plugin.minimods.Kick(),
            new mindustry.plugin.minimods.Rainbow(),
    };

    // register event handlers and create variables in the constructor
    public ioMain() {
        info("Starting Discord Plugin...");
        info(lennyFace);
        // disable debug logs from javacord (doesnt work tho, idk why)
        setDebug(false);
        FallbackLoggerConfiguration.setDebug(false);
        FallbackLoggerConfiguration.setTrace(false);

        DiscordApi api;
        DiscordRegistrar registrar = null;
        // read settings
        try {
            String pureJson = Core.settings.getDataDirectory().child("mods/settings.json").readString();
            JSONObject data = new JSONObject(new JSONTokener(pureJson));

            // url to connect to the MindServ
            maps_url = data.getString("maps_url");

            JSONObject discordData = data.getJSONObject("discord");
            discordInviteLink = discordData.getString("invite");
            String discordToken = discordData.getString("token");
            try {
                api = new DiscordApiBuilder().setToken(discordToken).login().join();
                Log.info("Logged in as: " + api.getYourself());
            } catch (Exception e) {
                Log.err("Couldn't log into discord.");
            }
            Channels.load(api, discordData.getJSONObject("channels"));
            Roles.load(api, discordData.getJSONObject("roles"));
            String discordPrefix = discordData.getString("prefix");
            registrar = new DiscordRegistrar(discordPrefix);

            // iplookup api key
            apapi_key = data.getString("ipapi_key");
            previewSchem = data.getBoolean("previewSchem");
            if (data.has("enableMapRatingPopups")) {
                enableMapRatingPopups = data.getBoolean("enableMapRatingPopups");
            }

            // connect to database
            JSONObject databaseData = data.getJSONObject("database");
            String dbURL = databaseData.getString("url");
            String dbUser = databaseData.getString("user");
            String dbPwd = databaseData.getString("password");
            System.out.printf("url: %s, user: %s, password: %s%n\n", dbURL, dbUser, dbPwd);

            try { // test connection
                Database.connect(dbURL, dbUser, dbPwd);
            } catch (Exception e) {
                err(e.toString());
                err("Could not login to PostgresSQL database!");
            }
        } catch (Exception e) {
            Log.err("Couldn't read settings.json file.");
            Log.err(e.toString());
            return;
        }

        for (MiniMod mod : minimods) {
            mod.registerDiscordCommands(registrar);
        }
        api.addMessageCreateListener(evt -> {
            registrar.dispatchEvent(evt);
        });
        DiscordVars.api = api;

        Utils.init();
        EffectHelper.init();        
        FallbackLoggerConfiguration.setDebug(false);
        FallbackLoggerConfiguration.setTrace(false);

        // Live Chat
        Events.on(EventType.PlayerChatEvent.class, event -> {
            if (event.message.charAt(0) != '/') {
                Player player = event.player;
                assert player != null;
                StringBuilder sb = new StringBuilder(event.message);
                for (int i = event.message.length() - 1; i >= 0; i--) {
                    if (sb.charAt(i) >= 0xF80 && sb.charAt(i) <= 0x107F) {
                        sb.deleteCharAt(i);
                    }
                }
                Channels.CHAT.sendMessage("**" + Utils.escapeEverything(event.player.name) + "**: " + sb);
            }
        });

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
            Database.Player pd = Database.getPlayerData(player.uuid());

            // put the player in the playerDataGroup, which saves player data while the player is online
            if (!playerDataGroup.containsKey(player.uuid())) {
                PersistentPlayerData data = new PersistentPlayerData();
                playerDataGroup.put(player.uuid(), data);
            }

            // check if he's impersonating a rank
            // remove all color codes, so it's not possible to just change the color of the rank symbol
            String escapedName = escapeColorCodes(player.name).replaceAll("\\[accent\\]", "");
            for (int i = 0; i < Rank.all.length; i++) {
                if (i == 0) continue;

                Rank rank = Rank.all[i];
                if (escapedName.toLowerCase().contains(escapeColorCodes(rank.tag).replaceAll("\\[accent\\]", ""))) {
                    player.con.kick("[scarlet]Dont impersonate a rank.");
                    Log.warn("Player " + escapedName + " tried to impersonate rank: " + rank.name);
                    return;
                }
            }

            // check for ban & give name
            if (pd != null) {
                if (pd.banned || pd.bannedUntil > Instant.now().getEpochSecond()) {
                    player.con.kick("[scarlet]You are banned.[accent] Reason:\n" + pd.banReason + "\n[white] If you what to appeal join our discord server: [cyan]" + discordInviteLink);
                    return;
                }

                Rank rank = Rank.all[pd.rank];
                Call.sendMessage("[#" + rank.color.toString().substring(0, 6) + "]" + rank.name + "[] " + player.name + "[accent] joined the front!");
                player.name = rank.tag + player.name;

                // Give Marshals admin
                if (pd.rank == Rank.all.length - 1) {
                    player.admin = true;
                }
            } else { // not in database
                info("New player connected: " + escapeColorCodes(event.player.name));
                Database.setPlayerData(new Database.Player(player.uuid(), 0));

                Rank rank = Rank.all[0];
                Call.sendMessage("[#" + rank.color.toString().substring(0, 6) + "]" + rank.name + "[] " + player.name + "[accent] joined the front!");
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
            update(log_channel, api);

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

            info("Registered all filters.");
        });

        for (MiniMod minimod : minimods) {
            minimod.registerEvents();
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

    // TODO: Needs to be called
    public static void update(TextChannel log_channel, DiscordApi api) {
        try {
            if ((logCount & 5) == 0) {
                // log player joins
                logConnections(log_channel, joinedPlayers, "join");

                logConnections(log_channel, leftPlayers, "leave");
            }
            logCount++;

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

            debug("Updated database!");
        } catch (Exception e) {
            err("There was an error in the update loop: ");
            e.printStackTrace();
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("update", "Update the database with the new current data", arg -> {
            TextChannel log_channel = getTextChannel("882342315438526525");
            update(log_channel, api);
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

        for (MiniMod mod : minimods) {
            mod.registerServerCommands(handler);
        }
    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler) {
        for (MiniMod minimod : minimods) {
            minimod.registerCommands(handler);
        }
    }
}