package mindustry.plugin;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.util.Timer;
import arc.util.*;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.net.Administration.Config;
import mindustry.world.Tile;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.awt.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.*;

import static mindustry.Vars.*;
import static mindustry.Vars.player;
import static mindustry.plugin.Utils.*;

public class ioMain extends Plugin {
//    public static final File prefsFile = new File("prefs.properties");
//    public static Net net = new Net();
//    public static Prefs prefs = new Prefs(prefsFile);
//    public GetMap map = new GetMap();

    //    public static JedisPool pool;
    public static DiscordApi api = null;
    public static String prefix = ".";
    public static String live_chat_channel_id = "";
    public static String bot_channel_id = null;
    public static String apprentice_bot_channel_id = null;
    public static String staff_bot_channel_id = null;
    public static String admin_bot_channel_id = null;
    public static String serverName = "<untitled>";
    public static JSONObject data; //token, channel_id, role_id
    public static String apiKey = "";
    public static List<Player> joinedPlayer = new ArrayList<>();
//    static Gson gson = new Gson();

    public static HashMap<String, PersistentPlayerData> playerDataGroup = new HashMap<>(); // uuid(), data
    private final long CDT = 300L;
    //    private final String fileNotFoundErrorMessage = "File not found: config\\mods\\settings.json";
    public ObjectMap<String, Role> discRoles = new ObjectMap<>();
    //    public ObjectMap<String, TextChannel> discChannels = new ObjectMap<>();
//    protected Interval timer = new Interval(1);
    //cooldown between votes
    int voteCooldown = 120;
    private ObjectMap<Long, String> cooldowns = new ObjectMap<>(); //uuid
    private JSONObject alldata;
    public NetServer.ChatFormatter chatFormatter = (player, message) -> player == null ? message : "[coral][[" + player.coloredName() + "[coral]]:[white] " + message;


    // register event handlers and create variables in the constructor
    public ioMain() {
        try {
            String pureJson = Core.settings.getDataDirectory().child("mods/settings.json").readString();
            JSONObject alldata;
            JSONObject data;
            data = alldata = new JSONObject(new JSONTokener(pureJson));
            // url, username and password to connect to the database
            url = alldata.getString("url");
            user = alldata.getString("user");
            password = alldata.getString("password");
            // url to connect to the MindServ
            maps_url = alldata.getString("maps_url");
            // for the live chat between the discord server and the mindustry server
            live_chat_channel_id = alldata.getString("live_chat_channel_id");
            // iplookup api key
            apapi_key = alldata.getString("apapi_key");
            // bot channels
            bot_channel_id = alldata.getString("bot_channel_id");
            apprentice_bot_channel_id = alldata.getString("apprentice_bot_channel");
            staff_bot_channel_id = alldata.getString("staff_bot_channel_id");
            admin_bot_channel_id = alldata.getString("admin_bot_channel_id");
            System.out.printf("url: %s, user: %s, password: %s%n", url, user, password);
        } catch (Exception e) {
            Log.err("Couldn't read settings.json file.");
        }
        try {
            // doesn't work on the first couple tries but after that it works, IDK why
            connect();
            connect();
            connect();
            connect();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            System.out.println(throwables);
        }
        Utils.init();

        try {
            String pureJson = Core.settings.getDataDirectory().child("mods/settings.json").readString();
            data = alldata = new JSONObject(new JSONTokener(pureJson));
        } catch (Exception e) {
            Log.err("Couldn't read settings.json file.");
        }
        try {
            api = new DiscordApiBuilder().setToken(alldata.getString("token")).login().join();
            Log.info("logged in as: " + api.getYourself());
        } catch (Exception e) {
            Log.err("Couldn't log into discord.");
        }
        BotThread bt = new BotThread(api, Thread.currentThread(), alldata);
        bt.setDaemon(false);
        bt.start();

        Rainbow rainbowThread = new Rainbow(Thread.currentThread());
        rainbowThread.setDaemon(false);
        rainbowThread.start();

        TextChannel tc = getTextChannel("881300954845179914");
        if (!Objects.equals(live_chat_channel_id, "")) {
            tc = getTextChannel(live_chat_channel_id);
        } else {
            System.err.println("couldn't fin live_chat_channel_id!");
        }
        if (tc != null) {
            TextChannel finalTc = tc;
            Events.on(EventType.PlayerChatEvent.class, event -> {
                if (event.message.charAt(0) != '/') {
                    Player player = event.player;
                    assert player != null;
                    PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
                    assert tdata != null;
                    if (!tdata.muted) {
                        finalTc.sendMessage("**" + escapeEverything(event.player.name) + "**: " + event.message);
                    } else {
                        player.sendMessage("[cyan]You are muted!");
                    }
                }
            });
        }


        // database
        try {
//            pool = new JedisPool(new JedisPoolConfig(), "localhost");
//            pool = new JedisPool();
//            Log.info("jedis database loaded");
        } catch (Exception e) {
            e.printStackTrace();
            Core.app.exit();
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

        // update every tick

        // player joined
        TextChannel log_channel = getTextChannel("882342315438526525");
        Events.on(EventType.PlayerJoin.class, event -> {
            Player player = event.player;
            if (bannedNames.contains(player.name)) {
                player.con.kick("[scarlet]Download the game from legitimate sources to join.\n[accent]https://anuke.itch.io/mindustry");
                return;
            }
//            EmbedBuilder eb = new EmbedBuilder();
//            eb.setTitle("Player Join Log");
//            eb.setDescription(String.format("`%s` • `%d `:%s", player.uuid(), player.id, escapeColorCodes(player.name)));
//            assert log_channel != null;
//            log_channel.sendMessage(eb);
            if (!joinedPlayer.contains(player)) {
                joinedPlayer.add(player);
            }
            PlayerData pd = getData(player.uuid());
            System.out.println(pd);

            if (!playerDataGroup.containsKey(player.uuid())) {
                PersistentPlayerData data = new PersistentPlayerData();
                playerDataGroup.put(player.uuid(), data);
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
                    player.con.kick("[scarlet]You are banned.[accent] Reason:\n" + pd.banReason + "\n[white] If you what to appeal join our discord server: [cyan]" + "https://discord.gg/qtjqCUbbdR");
                }
                int rank = pd.rank;
                Call.sendMessage("[#" + Integer.toHexString(rankNames.get(rank).color.getRGB()).substring(2) + "]" + rankNames.get(rank).name + " [] " + player.name + "[accent] joined the front!");
                player.name = rankNames.get(rank).tag + player.name;
            } else { // not in database
                System.out.println("new player connected: " + escapeColorCodes(event.player.name));
                setData(player.uuid(), new PlayerData(0));
//                Call.infoMessage(player.con, formatMessage(player, welcomeMessage));
                Call.sendMessage("[#" + Integer.toHexString(rankNames.get(0).color.getRGB()).substring(2) + "]" + rankNames.get(0).name + " [] " + player.name + "[accent] joined the front!");
            }
//
//            CompletableFuture.runAsync(() -> {
//                if(verification) {
//                    if (pd != null && !pd.verified) {
//                        Log.info("Unverified player joined: " + player.name);
//                        String url = "http://api.vpnblocker.net/v2/json/" + player.con.address + "/" + apiKey;
//                        String pjson = ClientBuilder.newClient().target(url).request().accept(MediaType.APPLICATION_JSON).get(String.class);
//
//                        JSONObject json = new JSONObject(new JSONTokener(pjson));
//                        if (json.has("host-ip")) {
//                            if (json.getBoolean("host-ip")) { // verification failed
//                                Log.info("IP verification failed for: " + player.name);
//                                Call.onInfoMessage(player.con, verificationMessage);
//                            } else {
//                                Log.info("IP verification success for: " + player.name);
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
        });

//        Events.on(EventType.d)

        // player built building
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.unit.getPlayer() == null) return;
            if (event.breaking) return;
            PlayerData pd = getData(event.unit.getPlayer().uuid());
            PersistentPlayerData td = (playerDataGroup.getOrDefault(event.unit.getPlayer().uuid(), null));
            if (pd == null || td == null) return;
            if (event.tile.block() != null) {
                if (!bannedBlocks.contains(event.tile.block())) {
                    td.bbIncrementor++;
//                    System.out.println(escapeColorCodes(event.unit.getPlayer().name) + " built a block");
//                    pd.buildingsBuilt++;
//                    setData(event.unit.getPlayer().uuid(), pd);
                }
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

        Events.on(EventType.GameOverEvent.class, event -> {
            for (Player p : Groups.player) {
                PlayerData pd = getData(p.uuid());
                if (pd != null) {
                    pd.gamesPlayed++;
                    Call.infoMessage(p.con, "[accent]+1 games played");
                }
            }
        });

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
        });
//        Core.app.post(this::loop);

    }

    public static Timer.Task loop() {
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

//        Core.app.post(this::loop);
        return null;
    }

    //    Core.app.post(this::loop);
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

    public static TextChannel getTextChannel(String id) {
        Optional<Channel> dc = api.getChannelById(id);
        if (dc.isEmpty()) {
            Log.err("[ERR!] discordplugin: channel not found! " + id);
            return null;
        }
        Optional<TextChannel> dtc = dc.get().asTextChannel();
        if (dtc.isEmpty()) {
            Log.err("[ERR!] discordplugin: textchannel not found! " + id);
            return null;
        }
        return dtc.get();
    }


    //register commands that run on the server
    @Override
    public void registerServerCommands(CommandHandler handler) {

    }


    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler) {
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
        if (api != null) {
            handler.removeCommand("t");
            handler.<Player>register("t", "<message...>", "Send a message only to your teammates.", (args, player) -> {
                String message = args[0];
                if (!checkChatRatelimit(message, player)) return;
                String raw = "[#" + player.team().color.toString() + "]<T> " + chatFormatter.format(player, message);
                Groups.player.each(p -> p.team() == player.team(), o -> o.sendMessage(raw, player, message));
            });

            handler.<Player>register("js", "<script...>", "Run arbitrary Javascript.", (arg, player) -> {
                PlayerData pd = getData(player.uuid());
                if (player.admin && Objects.requireNonNull(pd).rank == 9) {
                    player.sendMessage(mods.getScripts().runConsole(arg[0]));
                } else {
                    player.sendMessage("[scarlet]This command is restricted to admins!");
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

            handler.<Player>register("freeze", "<player> [reason...]", "Freeze a player. To unfreeze just use this command again.", (args, player) -> {
                Player target = findPlayer(args[0]);
                PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
                assert tdata != null;
                tdata.frozen = !tdata.frozen;
                assert target != null;
                player.sendMessage("[cyan]Successfully " + (tdata.frozen ? "froze" : "thawed") + " " + escapeEverything(target));
                Call.infoMessage(target.con, "[cyan]You got " + (tdata.frozen ? "frozen" : "thawed") + " by a moderator. " + (args.length > 1 ? "Reason: " + args[1] : ""));
            });

            handler.<Player>register("mute", "<player> [reason...]", "Mute a player. To unmute just use this command again.", (args, player) -> {
                Player target = findPlayer(args[0]);
                PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
                assert tdata != null;
                tdata.muted = !tdata.muted;
                assert target != null;
                player.sendMessage("[cyan]Successfully " + (tdata.muted ? "muted" : "unmuted") + " " + escapeEverything(target));
                Call.infoMessage(target.con, "[cyan]You got " + (tdata.muted ? "muted" : "unmuted") + " by a moderator. " + (args.length > 1 ? "Reason: " + args[1] : ""));
            });



            handler.<Player>register("bug", "[description...]", "Send a bug report to the discord server. (Please do not spam, because this command pings developers)", (args, player) -> {
                for (Long key : cooldowns.keys()) {
                    if (key + CDT < System.currentTimeMillis() / 1000L) {
                        cooldowns.remove(key);
                    } else if (player.uuid().equals(cooldowns.get(key))) {
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
                    cooldowns.put(System.currentTimeMillis() / 1000L, player.uuid());
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
                for (Long key : cooldowns.keys()) {
                    if (key + CDT < System.currentTimeMillis() / 1000L) {
                        cooldowns.remove(key);
                    } else if (player.uuid().equals(cooldowns.get(key))) {
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
                                new MessageBuilder()
                                        .setEmbed(new EmbedBuilder()
                                                .setTitle("Potential griefer online")
//                                                .setDescription("<@&861523420076179457>")
                                                .addField("name", escapeColorCodes(found.name))
                                                .addField("reason", args[1])
                                                .setColor(Color.RED)
                                                .setFooter("Reported by " + player.name))
                                        .send(tc_c);
                                assert tc_c != null;
                                tc_c.sendMessage("<@&882340213551140935>");
                            } else {
                                Role ro = discRoles.get("861523420076179457");
                                new MessageBuilder()
                                        .setEmbed(new EmbedBuilder()
                                                .setTitle("Potential griefer online")
//                                                .setDescription("<@&861523420076179457>")
                                                .addField("name", escapeColorCodes(found.name))
                                                .setColor(Color.RED)
                                                .setFooter("Reported by " + player.name))
                                        .send(tc_c);
                                assert tc_c != null;
                                tc_c.sendMessage("<@&882340213551140935>");
                            }
                            Call.sendMessage(found.name + "[sky] is reported to discord.");
                            cooldowns.put(System.currentTimeMillis() / 1000L, player.uuid());
                        }
                    } else {
                        player.sendMessage("[scarlet]No player[orange] '" + args[0] + "'[scarlet] found.");
                    }
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

            handler.<Player>register("rainbow", "[sargeaNt+] Give your username a rainbow animation", (args, player) -> {
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

            handler.<Player>register("info", "Display your stats.", (args, player) -> { // self info
                PlayerData pd = getData(player.uuid());
                if (pd != null) {
                    Call.infoMessage(player.con, formatMessage(player, statMessage));
                }
            });

            handler.<Player>register("rules", "Server rules. Please read carefully.", (args, player) -> { // self info
//                PlayerData pd = getData(player.uuid());

                Call.infoMessage(player.con, ruleMessage);

            });

            handler.<Player>register("event", "Join an ongoing event (if there is one)", (args, player) -> { // self info
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

            VoteSession[] currentlyKicking = {null};

            handler.<Player>register("changemap", "<map...>", " Vote to change to a specific map.", (args, player) -> {
                if (!state.rules.pvp || player.admin) {
//                    PlayerData pd = getData(player.uuid());
//                    if (pd != null && pd.rank >= 2) {

                    mindustry.maps.Map found = getMapBySelector(args[0]);

                    if (found != null) {
                        if (!vtime.get()) {
                            player.sendMessage("[scarlet]You must wait " + voteCooldown / 20 + " minutes between nominations.");
                            return;
                        }

                        VoteSession session = new VoteSession(currentlyKicking, found);

                        session.vote(player, 1);
                        vtime.reset();
                        currentlyKicking[0] = session;
                    } else {
                        player.sendMessage("[scarlet]No map[orange]'" + args[0] + "'[scarlet] found.");
                    }
//                    } else {
//                        player.sendMessage(noPermissionMessage);
//                    }
                } else {
                    player.sendMessage("[scarlet]This command is disabled on pvp.");
                }
            });

            handler.<Player>register("rtv", "Vote to change the map.", (args, player) -> { // self info
                if (currentlyKicking[0] == null) {
                    player.sendMessage("[scarlet]No map is being voted on.");
                } else {
                    //hosts can vote all they want
                    if (player.uuid() != null && (currentlyKicking[0].voted.contains(player.uuid()) || currentlyKicking[0].voted.contains(netServer.admins.getInfo(player.uuid()).lastIP))) {
                        player.sendMessage("[scarlet]You've already voted. Sit down.");
                        return;
                    }

                    currentlyKicking[0].vote(player, 1);
                }
            });

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
                        PersistentPlayerData tdata = (playerDataGroup.getOrDefault(player.uuid(), null));
                        if (tdata == null) continue; // shouldn't happen, ever
                        tdata.doRainbow = false;
                        assert pd != null;
                        player.name = rankNames.get(pd.rank).tag + netServer.admins.getInfo(player.uuid()).names.get(0);
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