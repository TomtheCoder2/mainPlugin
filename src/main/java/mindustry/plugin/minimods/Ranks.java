package mindustry.plugin.minimods;

import arc.Core;
import arc.Events;
import arc.math.geom.Point2;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Packets;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.*;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.*;
import mindustry.ui.Menus;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.environment.Prop;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.component.Button;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import static arc.util.Log.debug;
import static java.lang.Math.max;
import static mindustry.Vars.world;
import static mindustry.plugin.database.Database.getNames;
import static mindustry.plugin.discord.DiscordLog.moderationLogColonel;
import static mindustry.plugin.minimods.Moderation.frozen;
import static mindustry.plugin.utils.Utils.convertToSchemImage;
import static mindustry.plugin.utils.Utils.escapeEverything;

/**
 * Manages player ranks and other player information.
 */
public class Ranks implements MiniMod {
    /**
     * Uuid of players that have less than 60 minutes of playtime.
     */
    public static final ObjectMap<String, Utils.Pair<Integer, Integer>> newPlayers = new ObjectMap<>();
    public static final ObjectSet<String> warned = new ObjectSet<>();
    public static final ObjectSet<String> normalPlayers = new ObjectSet<>();
    public static final ObjectSet<SimpleBuild> deconstructionStarted = new ObjectSet<>();
    // creating a random key that gets appended to the button internal names for the discord interaction, so that its possible to run multiple servers with the same bot
    private static int randomKey = 0;
    private final static String promotionMessage = """
            [sky]%player%, you have been promoted to [sky]<%rank%>[]!
            [#4287f5]You reached a playtime of - %playtime% minutes!
            [#f54263]You played a total of %games% games!
            [#9342f5]You built a total of %buildings% buildings!
            [sky]Enjoy your time on the [white][#ff2400]P[#ff4900]H[#ff6d00]O[#ff9200]E[#ffb600]N[#ffdb00]I[#ffff00]X [white]Servers[sky]!""";
    /**
     * Blocks that do not count towards the stats for a player
     */
    private final static Block[] excludedBlocks = new Block[]{
            Blocks.conveyor,
            Blocks.titaniumConveyor,
            Blocks.plastaniumConveyor,
            Blocks.junction,
            Blocks.router,
            Blocks.underflowGate,
            Blocks.overflowGate
    };
    private static Block[] excludedBlocksAntiGriefSystem = {};
    /**
     * For auto ban system
     */
    private static final ObjectMap<String, ObjectSet<SimpleBuild>> buildingsDestroyedCache = new ObjectMap<>();
    /**
     * Number of buildings built that have not been stored to the database.
     */
    private final ObjectMap<String, Integer> buildingsBuiltCache = new ObjectMap<>();
    /**
     * Who has already rated the map
     */
    private final ObjectSet<String> hasRatedMap = new ObjectSet<>();
    private long mapStartTime = System.currentTimeMillis();

    public static void warnDiscord(Player p, String uuid, String reporter, String reason) {
        var t = new Thread(() -> {
            System.out.println("Future started");
            try {
                var minX = 0;
                var minY = 0;
                var maxX = 0;
                var maxY = 0;
                BufferedImage combinedImage = null;
                // render image of the destroyed blocks
                var schemImage = convertToSchemImage(buildingsDestroyedCache.get(uuid, new ObjectSet<>()));
                if (schemImage != null && schemImage.image != null) {
                    minX = schemImage.minX;
                    minY = schemImage.minY;
                    maxX = schemImage.maxX;
                    maxY = schemImage.maxY;

                    var n = 5;
                    // get all blocks in range minX - n to maxX + n and minY - n to maxY + n
                    // this will be the after picture
                    var blocks = new ObjectSet<SimpleBuild>();
                    for (int x = minX - n; x < maxX + n; x++) {
                        for (int y = minY - n; y < maxY + n; y++) {
                            var tile = world.tile(x, y);
                            if (tile != null && tile.build != null) {
                                blocks.add(new SimpleBuild(world.build(tile.x, tile.y)));
//                                debug("Added block: " + tile.build.block().localizedName + " at " + tile.x + ", " + tile.y);
                            }
                        }
                    }
                    var schemAfterImage = convertToSchemImage(blocks);
                    if (schemAfterImage != null) {
                        var afterImage = schemAfterImage.image;

                        // now we add the destroyed blocks to get the before picture
                        blocks.addAll(buildingsDestroyedCache.get(uuid, new ObjectSet<>()));
                        var schemBeforeImage = convertToSchemImage(blocks);
                        if (schemBeforeImage == null) {
                            // something went wrong
                            Log.err("[Anti-griefer-system] Something went wrong while rendering the schem image");
                            return;
                        }
                        var beforeImage = schemBeforeImage.image;

                        // now we combine the before and after image to get one single image and a line between the two
                        combinedImage = new BufferedImage(max(beforeImage.getWidth(), afterImage.getWidth()), beforeImage.getHeight() + afterImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        var g = combinedImage.createGraphics();
                        var lineThickness = 1f / 20f;
                        g.drawImage(beforeImage, 0, 0, null);
                        g.drawImage(afterImage, 0, beforeImage.getHeight(), null);
                        g.setColor(Color.RED);
                        // create a line the separates the 2 images
                        g.fillRect(0, (int) (beforeImage.getHeight() - lineThickness / 2 * beforeImage.getHeight()), beforeImage.getWidth(), (int) (lineThickness * beforeImage.getHeight()));
                        g.setFont(g.getFont().deriveFont(2.5f * lineThickness * beforeImage.getHeight()));
                        g.drawString("Before", 0, g.getFontMetrics().getHeight());
                        g.drawString("After", 0, beforeImage.getHeight() + g.getFontMetrics().getHeight());
                        g.dispose();
                    } else {
                        // something went terribly wrong
                        Log.err("[Anti-griefer-system] Something went terribly wrong while rendering the schem image");
                    }
                } else if (schemImage != null) {
                    minX = schemImage.minX;
                    minY = schemImage.minY;
                    maxX = schemImage.maxX;
                    maxY = schemImage.maxY;
                } else {
                    Log.info("[Anti-griefer-system] Error while rendering image of the destroyed blocks");
                }

                // warn mods
                EmbedBuilder eb = new EmbedBuilder().setTitle("Potential Griefer Online (" + reporter + ")")
                        .addField("Name", Utils.escapeEverything(p.name))
                        .addField("Phash", Utils.calculatePhash(uuid))
                        .addField("Coords", "Player: **" + p.tileX() + "**, **" + p.tileY() + "**\n" +
                                "Buildings: from **" + minX + "**, **" + minY + "** to **" + (maxX + minX) + "**, **" + (maxY + minY) + "**")
                        .setColor(DiscordPalette.WARN)
                        .setFooter("Reported by: " + reporter);
                if (combinedImage != null) {
                    eb.setImage(combinedImage);
                }
                if (reason != null) {
                    eb.addField("Reason", reason);
                }
                debug("randomKey: @", randomKey);
                new MessageBuilder()
                        .setEmbed(eb)
                        .setContent("<@&" + Roles.Auto + ">")
                        .addActionRow(
                                Button.danger("ban-" + randomKey, "Banish"),
                                Button.success("normal-" + randomKey, "Normal"),
                                Button.secondary("report-discuss-" + randomKey, "Report Discussion")
                        )
                        .send(Channels.GR_REPORT);

                // remove from warned after 10 minutes
                Timer.schedule(() -> {
                    if (warned.contains(uuid)) {
                        warned.remove(uuid);
                    }
                }, 10 * 60);
            } catch (Exception e) {
                Log.err("Something went wrong generating the image!", e);
            }
        });
        t.start();
    }

    @Override
    public void registerEvents() {
        // load random key from settings
        var randKey = Core.settings.getInt("randomKey");
        if (randKey == 0) {
            randKey = randomKey = ThreadLocalRandom.current().nextInt(0, 100000 + 1);
            Log.info("Set randomKey = " + randomKey);
            Core.settings.put("randomKey", randKey);
            Core.settings.autosave();
        } else {
            randomKey = randKey;
            Log.info("Loaded randomKey = " + randomKey);
        }

        Events.on(EventType.ContentInitEvent.class, contentInitEvent -> {
            excludedBlocksAntiGriefSystem = Vars.content.blocks().select(b -> b instanceof Prop).toArray(Block.class);
        });
        // -- MAP SECTION -- //
        Events.on(EventType.WorldLoadEvent.class, event -> {
            // reset start time
            mapStartTime = System.currentTimeMillis();

            // rate menu
            hasRatedMap.clear();
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    rateMenu(null);
                }
            }, 10 * 60);
        });

        Events.on(EventType.PlayerJoin.class, event -> {
            Vars.state.set(GameState.State.playing);
            Timer.schedule(() -> {
                if (event.player.con == null || !event.player.con.isConnected()) return;
                rateMenu(event.player);
            }, 10 * 60);
        });

        Events.on(EventType.GameOverEvent.class, event -> {
            // update time-based map stats
            String mapName = escapeEverything(Vars.state.map.name());
            Database.Map md = Database.getMapData(mapName);
            if (md == null) {
                md = new Database.Map(mapName);
            }
            md.highscoreWaves = max(md.highscoreWaves, Vars.state.stats.wavesLasted);
            long mapTime = ((System.currentTimeMillis() - mapStartTime) / 1000) / 60;
            md.highscoreTime = max(md.highscoreTime, mapTime);
            if (md.shortestGame != 0) {
                md.shortestGame = Math.min(md.shortestGame, mapTime);
            } else {
                md.shortestGame = mapTime;
            }
            md.playTime += mapTime;
            Database.setMapData(md);
        });

        // -- PLAYER SECTION -- //
        Events.on(EventType.GameOverEvent.class, event -> {
            // +1 games to everyone
            // updates player's gamesPlayed
            for (Player p : Groups.player) {
                Database.Player pd = Database.getPlayerData(p.uuid());
                if (pd == null) {
                    pd = new Database.Player(p.uuid(), 0);
                }
                pd.gamesPlayed++;
                Call.infoMessage(p.con, "[accent]+1 games played");
                Database.setPlayerData(pd);
            }

            promoteRanks();
        });

        DiscordVars.api.addButtonClickListener(event -> {
            var msg = event.getButtonInteraction().getMessage();
            var eb = msg.getEmbeds().get(0);
            switch (event.getButtonInteraction().getCustomId().replace("-" + randomKey, "")) {
                case "ban" -> {
                    event.getInteraction().respondLater(true)
                            .thenAccept(updater -> {
                                System.out.println(eb.getFields());
                                if (eb.getFields().size() < 2) return;
                                String phash = eb.getFields().get(1).getValue();
                                System.out.println(phash);
                                // ban phash
                                var pd = Database.getPlayerDataByPhash(phash);
                                if (pd == null) {
                                    msg.createUpdater()
                                            .setEmbed(
                                                    eb.toBuilder()
                                                            .setFooter("Player not found")
                                                            .setColor(DiscordPalette.ERROR))
                                            .removeAllComponents()
                                            .applyChanges();
                                    return;
                                }
                                // check if there are message attachments
                                BufferedImage image = null;
                                try {
                                    if (msg.getEmbeds().get(0).getImage().isPresent()) {
                                        try {
                                            image = msg.getEmbeds().get(0).getImage().get().downloadAsBufferedImage(msg.getApi()).get();
                                            debug("Got image: @x@", image.getWidth(), image.getHeight());
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                debug("Banning: @", pd.uuid);
                                // get player who reported him by the footer
                                String reporter = "";
                                if (eb.getFooter().isPresent()) {
                                    reporter = eb.getFooter().get().getText().get().replace("Reported by: ", "");
                                }
                                reporter = reporter + " (confirmed by " + event.getInteraction().getUser().getDiscriminatedName() + ")";
                                pd.banReason = "Banned by " + reporter;
                                // ban for 10080 minutes (7 days)
                                pd.bannedUntil = Instant.now().getEpochSecond() + 10080 * 60;
                                Database.setPlayerData(pd);
                                // kick player
                                for (Player p : Groups.player) {
                                    System.out.printf("checking: %s == %s\n", p.uuid(), pd.uuid);
                                    if (p.uuid().equals(pd.uuid)) {
                                        p.kick(Packets.KickReason.banned);
                                    }
                                }
                                long undoStart = msg.getCreationTimestamp().toEpochMilli() - 5 * 60 * 1000L;
                                Undo.instance.rollback(pd.uuid, undoStart);
                                msg.createUpdater()
                                        .setEmbed(
                                                eb.toBuilder()
                                                        .setFooter("Banned by: " + reporter)
                                                        .addField("Status", "Banned", true)
                                                        .setColor(DiscordPalette.ERROR))
                                        .removeAllComponents()
                                        .applyChanges();
                                // log to colonel and normal logs
                                moderationLogColonel("Banned", reporter, Vars.netServer.admins.getInfo(pd.uuid), pd.banReason, null, null, image);
                                DiscordLog.moderation("Banned", reporter, Vars.netServer.admins.getInfo(pd.uuid), pd.banReason, null);
                                updater.addEmbed(new EmbedBuilder().setTitle("Banned " + escapeEverything(Query.findPlayerInfo(pd.uuid).lastName)).setTimestampToNow().setColor(DiscordPalette.ERROR)).update();
                            });
                }
                case "normal" -> {
                    if (eb.getFields().size() < 1) return;
                    String phash = eb.getFields().get(1).getValue();
                    var pd = Database.getPlayerDataByPhash(phash);
                    if (pd != null) {
                        pd.verified = true;
                        Database.setPlayerData(pd);
                        normalPlayers.add(pd.uuid);
                        // and unfreeze
                        frozen.remove(pd.uuid);
                        msg.createUpdater()
                                .setEmbed(
                                        eb.toBuilder()
                                                .setFooter("Normal")
                                                .addField("Status", "Normal", true)
                                                .setColor(DiscordPalette.SUCCESS)
                                )
                                .removeAllComponents()
                                .applyChanges();
                        event.getInteraction().createImmediateResponder()
                                .addEmbed(new EmbedBuilder().setTitle("Set status of player " + escapeEverything(Query.findPlayerInfo(pd.uuid).lastName) + " to normal.").setTimestampToNow().setColor(DiscordPalette.SUCCESS))
                                .setFlags(MessageFlag.EPHEMERAL).respond();
                    } else {
                        msg.createUpdater()
                                .setEmbed(
                                        eb.toBuilder()
                                                .setFooter("Player not found")
                                                .setColor(DiscordPalette.ERROR))
                                .removeAllComponents()
                                .applyChanges();
                    }
                }
                case "report-discuss" -> {
                    if (eb.getFields().size() < 1) return;
                    String phash = eb.getFields().get(1).getValue();
                    // get the name of the player
                    var pd = Database.getPlayerDataByPhash(phash);
                    if (pd == null) return;
                    var name = Objects.requireNonNull(getNames(pd.uuid)).get(0);
                    try {
                        var thread = msg.createThread(name, 60).join();
                        thread.sendMessage(new EmbedBuilder().setTitle("Thread created").addField("Thread created by", "<@" + event.getInteraction().getUser().getIdAsString() + ">").setColor(DiscordPalette.SUCCESS));
                    } catch (Exception e) {
                        Log.info("Couldn't load Thread");
                        e.printStackTrace();
                    }
                    msg.createUpdater()
                            .removeAllComponents()
                            .applyChanges();
                    msg.createUpdater().addActionRow(
                            Button.danger("ban-" + randomKey, "Banish"),
                            Button.success("normal-" + randomKey, "Normal"),
                            Button.secondary("report-discuss-" + randomKey, "Report Discussion")
                    ).applyChanges();
                    event.getInteraction().createImmediateResponder()
                            .addEmbed(new EmbedBuilder().setTitle("Created discussion thread for " + name + ".").setColor(DiscordPalette.SUCCESS).setTimestampToNow())
                            .setFlags(MessageFlag.EPHEMERAL).respond();
                }
            }
        });

        // the problem is that the blocks where the deconstruction has already started have to be saved, else the name cant be know after the deconstruction of the block
        Events.on(EventType.TilePreChangeEvent.class, event -> {
            try {
                if (event.tile.build == null) return;
//                debug("TilePreChangeEvent, block: " + event.tile.build.block().localizedName + ", name: " + event.tile.block().name);
                deconstructionStarted.remove(new SimpleBuild(world.build(event.tile.x, event.tile.y)));
                deconstructionStarted.add(new SimpleBuild(world.build(event.tile.x, event.tile.y)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // clear the deconstructionStarted map after game over, so there are no problems with the next game and no memory leak
        Events.on(EventType.GameOverEvent.class, event -> {
            deconstructionStarted.clear();
            buildingsDestroyedCache.clear();
        });

        Events.on(EventType.WorldLoadEvent.class, event -> {
            try {
                // then iterate over all tiles and save them in this list
                for (Tile tile : world.tiles) {
                    if (tile.build != null) {
                        if (tile.build.team.isAI()) continue;
                        if (!Arrays.asList(excludedBlocksAntiGriefSystem).contains(tile.block())) continue;
                        deconstructionStarted.add(new SimpleBuild(world.build(tile.x, tile.y)));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        ExecutorService es = Executors.newSingleThreadExecutor();

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            try {
                if (event.unit.getPlayer() == null) return;
                String uuid = event.unit.getPlayer().uuid();
//                debug("current size of deconstruction cache: " + deconstructionStarted.size);

                if (event.tile.block() != null) {
                    if (!event.breaking) {
                        var tile = event.tile;
                        // this will increase the buildingsbuilt stats but its more important to stop griefing
//                    if (!Arrays.asList(excludedBlocks).contains(event.tile.block())) {
                        buildingsBuiltCache.put(uuid, buildingsBuiltCache.get(uuid, 0) + 1);
                        deconstructionStarted.remove(new SimpleBuild(world.build(event.tile.x, event.tile.y)));
                        deconstructionStarted.add(new SimpleBuild(world.build(event.tile.x, event.tile.y)));
//                    }
                    } else {
                        if (!Arrays.asList(excludedBlocksAntiGriefSystem).contains(event.tile.block())) {
                            var cache = buildingsDestroyedCache.get(uuid, new ObjectSet<>());
                            // if the deconstruction has already started, the name of the block has to be saved
//                            debug(deconstructionStarted);
                            // search event.tile in deconstructionStarted
                            var found = false;
                            for (SimpleBuild build : deconstructionStarted) {
                                if (build.tileX == event.tile.x && build.tileY == event.tile.y) {
                                    if (build.block.localizedName.contains("build")) {
                                        // this is an invalid block so we remove it
                                        deconstructionStarted.remove(build);
                                        continue;
                                    }
                                    // if the tile is found, save the name of the block
                                    debug("DS: Building destroyed: " + deconstructionStarted.get(build));
                                    cache.add(deconstructionStarted.get(build));
                                    buildingsDestroyedCache.put(uuid, cache);
                                    deconstructionStarted.remove(build);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                debug("Building destroyed: " + world.build(event.tile.x, event.tile.y).block.localizedName);
                                cache.add(new SimpleBuild(world.build(event.tile.x, event.tile.y)));
                            }
                            buildingsDestroyedCache.put(uuid, cache);
                        }
                    }
//                    System.out.println("buildingsDestroyedCache.get(uuid, new HashMap<>()).size() = " + buildingsDestroyedCache.get(uuid, new ObjectMap<>()).size);

                    // check if its suspicious
                    if (newPlayers.containsKey(uuid) && !normalPlayers.contains(uuid)) {
                        if (buildingsBuiltCache.get(uuid, 0) < buildingsDestroyedCache.get(uuid, new ObjectSet<>()).size) {
                            int warnThreshold = max(10, (buildingsBuiltCache.get(uuid, 0) + newPlayers.get(uuid).second / 5) * newPlayers.get(uuid).first / 2);
//                            debug("calculation of warnThreshold: " + warnThreshold + " = max(10, (" + buildingsBuiltCache.get(uuid, 0) + " + " + newPlayers.get(uuid).second / 5 + ") * " + newPlayers.get(uuid).first / 2 + ")");
//                            debug("Warn threshold: " + warnThreshold + ", buildings destroyed: " + buildingsDestroyedCache.get(uuid, new ObjectSet<>()).size);
                            if (buildingsDestroyedCache.get(uuid, new ObjectSet<>()).size > warnThreshold) {
                                // freeze for 15 seconds
                                frozen.add(uuid);
                                Timer.schedule(() -> frozen.remove(uuid), 15);
                                if (!warned.contains(uuid)) {
                                    warned.add(uuid);
                                    // send a message to all players online
                                    for (Player p : Groups.player) {
                                        if (p == event.unit.getPlayer()) continue;
                                        if (warned.contains(p.uuid())) continue;
                                        p.sendMessage("[scarlet][Anti-griefer-system] Warning! Potential griefer found on the server, keep an eye on \"" + event.unit.getPlayer().name + "\"[scarlet]!" + (p.admin ? " [orange]Their UUID is " + uuid : ""));
                                    }
                                    // warn player
                                    event.unit.getPlayer().sendMessage("[scarlet]You are deconstructing too fast, to avoid mass grief you have been frozen for [cyan]15 seconds.[] This action will be reported to the moderators.");
                                    // log in console
                                    Log.info("[Anti-griefer-system] Warning! Potential griefer found on the server, keep an eye on \"" + event.unit.getPlayer().name + "\"[scarlet]!" + " Their UUID is " + uuid);
                                    // run in a thread cause it takes a while
                                    warnDiscord(event.unit.getPlayer(), uuid, "Auto Ban System", null);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.err("There was an error while saving block status: ");
                e.printStackTrace();
            }
        });

        Events.on(EventType.PlayerLeave.class, event -> {
            // remove from new players
            newPlayers.remove(event.player.uuid());
            // also from warned
            warned.remove(event.player.uuid());
            // pause game if no one is online
            debug("player online: @", Groups.player.size());
            if (Groups.player.size() <= 1) {
                debug("Pausing server cause no one is online");
                Vars.state.set(GameState.State.paused);
            }
        });

        Timer.schedule(new Timer.Task() {
            long timeStart = System.currentTimeMillis();

            @Override
            public void run() {
                int elapsedTimeMin = Math.round((System.currentTimeMillis() - timeStart) / 1000) / 60;

                for (Player player : Groups.player) {
                    Database.Player pd = Database.getPlayerData(player.uuid());
                    if (pd == null) {
                        pd = new Database.Player(player.uuid(), 0);
                    }
                    pd.playTime += elapsedTimeMin;
                    pd.buildingsBuilt += buildingsBuiltCache.get(pd.uuid, 0);
                    if (newPlayers.containsKey(player.uuid())) {
                        if (newPlayers.get(player.uuid()).first + elapsedTimeMin >= 60) {
                            newPlayers.remove(player.uuid());
                            pd.verified = true;
                        } else {
                            newPlayers.put(player.uuid(), new Utils.Pair<>(pd.playTime, pd.buildingsBuilt));
                            pd.verified = false;
                        }
                    }
                    Database.setPlayerData(pd);
                }

                timeStart = System.currentTimeMillis();
            }
        }, 120, 120);
    }

    /**
     * Popup a rate menu for the given player, or all players
     * If a player has already rated the current map, the rate menu is not shown
     *
     * @param p The player to popup, or null for all players
     * @return false if a player has already voted
     */
    private boolean rateMenu(Player p) {
        String mapName = escapeEverything(Vars.state.map.name());
        int id = Menus.registerMenu((player, selection) -> {
            Database.Map md = Database.getMapData(mapName);
            if (md == null) {
                md = new Database.Map(mapName);
            }
            if (selection == 0) {
                md.positiveRating += 1;
                player.sendMessage("Successfully gave a [green]positive [white]feedback for " + mapName + "[white]!");
            } else if (selection == 1) {
                md.negativeRating += 1;
                player.sendMessage("Successfully gave a [red]negative [white]feedback for " + mapName + "[white]!");
            } else if (selection == 2) {
                player.sendMessage("Successfully skipped voting for " + mapName + "[white]!");
            } else {
                return;
            }

            hasRatedMap.add(player.uuid());
            Database.setMapData(md);
        });

        Iterable<Player> players;
        if (p == null) {
            players = Seq.with(p);
        } else {
            players = Groups.player;
        }

        boolean alreadyVoted = false;
        for (var player : players) {
            if (player == null) continue; // ????
            if (hasRatedMap.contains(player.uuid())) {
                alreadyVoted = true;
                continue;
            }
            Call.menu(player.con, id,
                    "Rate this map! [pink]" + mapName,
                    "Do you like this map? Vote [green]yes [white]or [scarlet]no[white]:",
                    new String[][]{
                            new String[]{"[green]Yes", "[scarlet]No", "Skip"},
                            new String[]{"Later"}
                    }
            );
        }
        return !alreadyVoted;
    }

    /**
     * Check if anyone deserves to be promoted, and promote accordingly
     */
    private void promoteRanks() {
        // check if someone gets promoted
        for (Player player : Groups.player) {
            Database.Player pd = Database.getPlayerData(player.uuid());
            if (pd == null) {
                pd = new Database.Player(player.uuid(), 0);
            }

            for (var entry : Rank.reqs) {
                Rank.Req req = entry.value;
                if (pd.rank < entry.key && pd.playTime >= req.playTime
                        && pd.buildingsBuilt >= req.buildingsBuilt && pd.gamesPlayed >= req.gamesPlayed) {
                    Call.infoMessage(player.con, Utils.formatMessage(player, promotionMessage));
                    pd.rank = entry.key;
                    Log.info(escapeEverything(player.name) + " got promoted to " + Rank.all[pd.rank].name + "!");
                }
            }

            Database.setPlayerData(pd);
        }
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("ranks", "Show all ranks.", (args, player) -> { // self info
            StringBuilder sb = new StringBuilder("[accent]Ranks\n");
            for (int i = 0; i < Rank.all.length; i++) {
                Rank rank = Rank.all[i];
                sb.append(Utils.formatName(rank, "[white]" + rank.name) + "\n");
            }
            sb.append("\n[green]Type [sky]/req [green]to see the requirements for the ranks");
            Call.infoMessage(player.con, sb.toString());
        });
        handler.<Player>register("req", "Show the requirements for all ranks", (args, player) -> { // self info
            StringBuilder sb = new StringBuilder("[accent]List of all requirements:\n");
            for (int i = 0; i < Rank.all.length; i++) {
                Rank rank = Rank.all[i];
                sb.append("[#").append(rank.color.toString(), 0, 6).append("]")
                        .append(rank.name).append(" ");
                if (rank.requirements != null) {
                    sb.append("[] : [orange]").append(rank.requirements).append("\n");
                } else if (Rank.reqs.get(i) != null) {
                    Rank.Req req = Rank.reqs.get(i);
                    sb
                            .append("[] : [red]")
                            .append(Utils.formatInt(req.playTime))
                            .append(" mins[white] / [orange]")
                            .append(req.gamesPlayed)
                            .append(" games[white] / [yellow]")
                            .append(Utils.formatInt(req.buildingsBuilt))
                            .append(" built\n");
                }
            }

            Call.infoMessage(player.con, sb.toString());
        });
        handler.<Player>register("stats", "[player]", "Display stats of the specified player.", (args, player) -> {
            if (args.length > 0) {
                Player p = Query.findPlayerEntity(args[0]);
                if (p != null) {
                    Database.Player pd = Database.getPlayerData(p.uuid());
                    if (pd != null) {
                        Call.infoMessage(player.con, Utils.formatMessage(p, Utils.Message.stat()));
                    }
                } else {
                    player.sendMessage("[scarlet]Error: Player not found or offline");
                }
            } else {
                Call.infoMessage(player.con, Utils.formatMessage(player, Utils.Message.stat()));
            }
        });
        handler.<Player>register("rate", "", "Rate the current map", (args, player) -> {
            if (!rateMenu(player)) {
                player.sendMessage(GameMsg.error("Rate", "You've already rated this map"));
            }
        });
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("ranking", "<playtime|gamesplayed|buildingsbuilt> [offset]",
                data -> {
                    data.help = "Returns a ranking of players.";
                },
                ctx -> {
                    String column = ctx.args.get("playtime|gamesplayed|buildingsbuilt");
                    switch (column) {
                        case "p" -> {
                            column = "playtime";
                        }
                        case "g" -> {
                            column = "gamesplayed";
                        }
                        case "b" -> {
                            column = "buildingsbuilt";
                        }
                    }
                    int offset = ctx.args.getInt("offset", 0);
                    Database.PlayerRank[] ranking = Database.rankPlayers(10, column, offset);
                    if (ranking == null || ranking.length == 0) {
                        ctx.error("No players found", "Make sure the stat is valid.");
                        return;
                    }

                    String table = "```\n";
                    table += String.format("%3s %-20s %-10s\n", "", "Player", column);
                    for (int i = 0; i < ranking.length; i++) {
                        var info = Vars.netServer.admins.getInfoOptional(ranking[i].uuid);
                        String name = "<unknown>";
                        if (info != null) {
                            name = escapeEverything(info.lastName);
                        }
                        table += String.format("%3s %-20s %-10s\n", offset + i + 1, name, ranking[i].stat);
                    }
                    table += "```";

                    ctx.sendEmbed(new EmbedBuilder()
                            .setColor(DiscordPalette.INFO)
                            .setTitle("Player Ranking")
                            .setDescription(table)
                    );
                }
        );

        handler.register("mapranking", "<positiverating|negativerating|highscorewaves|playtime> [offset]",
                data -> {
                    data.help = "Returns a ranking of maps";
                },
                ctx -> {
                    String column = ctx.args.get("positiverating|negativerating|highscorewaves|playtime");
                    int offset = ctx.args.getInt("offset", 0);
                    Database.MapRank[] ranking = Database.rankMaps(10, column, offset);
                    if (ranking == null || ranking.length == 0) {
                        ctx.error("No maps found", "Make sure the stat is valid.");
                        return;
                    }

                    String table = "```\n";
                    table += String.format("%3s %-30s %-10s\n", "", "Map", column);
                    for (int i = 0; i < ranking.length; i++) {
                        if (Query.findMap(ranking[i].name) == null) continue;
                        table += String.format("%3s %-30s %-10s\n", offset + i + 1, Strings.stripColors(ranking[i].name), ranking[i].stat);
                    }
                    table += "```";

                    ctx.sendEmbed(new EmbedBuilder()
                            .setColor(DiscordPalette.INFO)
                            .setTitle("Map Ranking")
                            .setDescription(table)
                    );
                }
        );

        handler.register("setrank", "<player> <rank>",
                data -> {
                    data.help = "Set a player's in-game rank";
                    data.roles = new long[]{Roles.MOD, Roles.ADMIN};
                    data.category = "Management";
                },
                ctx -> {
                    Player target = Query.findPlayerEntity(ctx.args.get("player"));
                    if (target == null) {
                        ctx.error("Player not found", ctx.args.get("player") + " is not online");
                        return;
                    }

                    String rankQuery = ctx.args.get("rank");
                    int rank = -1;
                    for (int i = 0; i < Rank.all.length; i++) {
                        if (Integer.toString(i).equals(rankQuery) || Rank.all[i].name.equalsIgnoreCase(rankQuery)) {
                            rank = i;
                        }
                    }
                    if (rank == -1) {
                        ctx.error("Invalid rank", rankQuery + " is not a valid rank");
                        return;
                    }

                    var pd = Database.getPlayerData(target.uuid());
                    if (pd == null) {
                        pd = new Database.Player(target.uuid(), rank);
                    }
                    pd.rank = rank;
                    Database.setPlayerData(pd);

                    target.name = Utils.formatName(pd, target);
                    ctx.success("Rank set", Utils.escapeEverything(target) + "'s rank is now set to " + Rank.all[rank].name);

                    DiscordLog.moderation("Set rank", ctx.author(), Vars.netServer.admins.getInfo(pd.uuid), null, "Rank: " + Rank.all[rank].name + " (" + rank + ")");
                }
        );

        handler.register("subrank", "<list|add|remove> <player> [rank]",
                data -> {
                    data.help = "Set a player's in-game subrank \nList of all ranks:" + Arrays.toString(SubRank.all);
                    data.roles = new long[]{Roles.MOD, Roles.ADMIN};
                    data.category = "Management";
                },
                ctx -> {
                    Player target = Query.findPlayerEntity(ctx.args.get("player"));
                    if (target == null) {
                        ctx.error("Player not found", ctx.args.get("player") + " is not online");
                        return;
                    }

                    var pd = Database.getPlayerData(target.uuid());
                    if (pd == null) {
                        // player not found
                        ctx.error("Player not found", "Player data not found for " + Utils.escapeEverything(target));
                        return;
                    }

                    String action = ctx.args.get("list|add|remove");
                    String rankQuery;
                    int rank = -1;
                    if (!Objects.equals(action, "list")) {
                        rankQuery = ctx.args.get("rank");
                        for (int i = 0; i < SubRank.all.length; i++) {
                            if (Integer.toString(i).equals(rankQuery) || SubRank.all[i].name.equalsIgnoreCase(rankQuery)) {
                                rank = i;
                            }
                        }
                        if (rank == -1) {
                            ctx.error("Invalid rank", rankQuery + " is not a valid rank");
                            return;
                        }
                    }
                    switch (action) {
                        case "list" -> {
                            if (pd.subranks.isEmpty()) {
                                ctx.error("No subranks", Utils.escapeEverything(target) + " has no subranks");
                                return;
                            }

                            StringBuilder table = new StringBuilder("```\n");
                            table.append(String.format("%3s %-10s\n", "", "Subrank"));
                            for (int i = 0; i < pd.subranks.size(); i++) {
                                table.append(String.format("%3s %-10s\n", i + 1, SubRank.all[pd.subranks.get(i)].name));
                            }
                            table.append("```");

                            ctx.sendEmbed(new EmbedBuilder()
                                    .setColor(DiscordPalette.INFO)
                                    .setTitle("Subranks for " + Utils.escapeEverything(target))
                                    .setDescription(table.toString())
                            );
                        }
                        case "add" -> {
                            if (pd.subranks.contains(rank)) {
                                ctx.error("Subrank already exists", Utils.escapeEverything(target) + " already has the subrank " + SubRank.all[rank].name);
                                return;
                            }

                            pd.subranks.add(rank);
                            Database.setPlayerData(pd);

                            target.name = Utils.formatName(pd, target);
                            ctx.success("Subrank added", Utils.escapeEverything(target) + " now has the subrank " + SubRank.all[rank].name);

                            DiscordLog.moderation("Added subrank", ctx.author(), Vars.netServer.admins.getInfo(pd.uuid), null, "Subrank: " + SubRank.all[rank].name + " (" + rank + ")");
                        }
                        case "remove" -> {
                            if (!pd.subranks.contains(rank)) {
                                ctx.error("Subrank not found", Utils.escapeEverything(target) + " does not have the subrank " + SubRank.all[rank].name);
                                return;
                            }

                            pd.subranks.remove((Integer) rank);
                            Database.setPlayerData(pd);

                            target.name = Utils.formatName(pd, target);
                            ctx.success("Subrank removed", Utils.escapeEverything(target) + " no longer has the subrank " + SubRank.all[rank].name);

                            DiscordLog.moderation("Removed subrank", ctx.author(), Vars.netServer.admins.getInfo(pd.uuid), null, "Subrank: " + SubRank.all[rank].name + " (" + rank + ")");
                        }
                    }
                }
        );

        handler.register("setstats", "<player> <playtime> <buildingsbuilt> <gamesplayed>",
                data -> {
                    data.help = "Set a player's game statistics";
                    data.category = "Management";
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD};
                },
                ctx -> {
                    var info = Query.findPlayerDiscord(ctx.args.get("player"), ctx);
                    if (info == null) {
//                        ctx.error("No such player", "There is no such player in the database");
                        return;
                    }

                    var pd = Database.getPlayerData(info.id);
                    if (pd == null) {
                        ctx.error("No such player", "There is no such player in the database");
                        return;
                    }

                    int playtime = ctx.args.getInt("playtime");
                    int buildingsbuilt = ctx.args.getInt("buildingsbuilt");
                    int gamesplayed = ctx.args.getInt("gamesplayed");
                    pd.playTime = playtime;
                    pd.buildingsBuilt = buildingsbuilt;
                    pd.gamesPlayed = gamesplayed;

                    Database.setPlayerData(pd);

                    ctx.success("Set player stats", "Play time: " + playtime + " min\nBuildings built: " + buildingsbuilt + "\nGames played: " + gamesplayed);
                }
        );
        // todo: add a verify command
    }

    public static class SimpleBuild {

        public Block block;
        public int tileX, tileY;
        public Block realBlock;
        public Object config;
        public int rotation;

        public SimpleBuild(Building building) {
            this.block = building.block;
            this.tileX = building.tileX();
            this.tileY = building.tileY();
            realBlock = building == null ? null : building instanceof ConstructBlock.ConstructBuild cons ? cons.current : building.block;
            config = building instanceof ConstructBlock.ConstructBuild cons ? cons.lastConfig : building.config();
            rotation = building.rotation;
        }

        public int pos() {
            return Point2.pack(tileX, tileY);
        }

        public int tileX() {
            return tileX;
        }

        public int tileY() {
            return tileY;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SimpleBuild that)) return false;
            return tileX == that.tileX && tileY == that.tileY;
        }

        @Override
        public String toString() {
            return "SimpleBuild{" +
                    "block=" + block +
                    ", tileX=" + tileX +
                    ", tileY=" + tileY +
                    ", realBlock=" + realBlock +
                    ", config=" + config +
                    ", rotation=" + rotation +
                    '}';
        }
    }
}
