package mindustry.plugin.utils;

import arc.Core;
import arc.Events;
import arc.func.Boolf;
import arc.struct.*;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import mindustry.game.EventType;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.input.Placement;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.discordcommands.Context;
import mindustry.plugin.minimods.Ranks;
import mindustry.world.Block;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.storage.CoreBlock;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static arc.util.Log.debug;
import static java.lang.Math.min;
import static mindustry.Vars.*;
import static mindustry.plugin.PhoenixMain.contentHandler;
//import java.sql.*;

public class Utils {
    public static int chatMessageMaxSize = 256;
    // whether ip verification is in place (detect VPNs, disallow their build rights)
    public static Boolean verification = false;
    public static Pattern ipValidationPattern;

    public static ObjectMap<String, String> hashCache = new ObjectMap<>();

    public static String[] split(String str, int chunkSize) {
//        return str.split("(?<=\\G.{" + chunkSize + "})");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < min(str.length(), chunkSize); i++) {
            sb.append(str.charAt(i));
        }
        assert sb.length() == min(str.length(), chunkSize);
        return new String[]{sb.toString()};
    }

    public static ArrayList<String> getArrayListFromString(String str) {
        String replace = str.replace("[", "");
        String replace1 = replace.replace("]", "");
        return new ArrayList<>(Arrays.asList(replace1.split(",")));
    }

    public static void init() {

        // setup regex for ip validation
        // Regex for digit from 0 to 255.
        String zeroTo255
                = "(\\d{1,2}|(0|1)\\"
                + "d{2}|2[0-4]\\d|25[0-5])";

        // Regex for a digit from 0 to 255 and
        // followed by a dot, repeat 4 times.
        // this is the regex to validate an IP address.
        String regex
                = zeroTo255 + "\\."
                + zeroTo255 + "\\."
                + zeroTo255 + "\\."
                + zeroTo255;

        // Compile the ReGex
        ipValidationPattern = Pattern.compile(regex);
    }

    public static String playerList(Boolf<Player> pred) {
        StringBuilder sb = new StringBuilder();
        sb.append("[orange]List of players: \n");
        for (Player p : Groups.player) {
            if (!pred.get(p)) continue;
            sb.append("[lightgray] ").append(p.name);
            sb.append("[accent] - ").append(Utils.calculatePhash(p.uuid()));
            sb.append(" (#").append(p.id()).append(")\n");
        }
        return sb.toString();
    }

    /**
     * replace all color codes, ` and @
     *
     * @param string the original string
     */
    public static String escapeCharacters(String string) {
        return escapeColorCodes(string.replaceAll("`", "").replaceAll("@", ""));
    }

    // region string modifiers

    /**
     * Remove all color codes
     *
     * @param string the input string (for example a name or a message)
     * @deprecated use {@link arc.util.Strings#stripColors Strings.stripColors} instead
     */
    @Deprecated
    public static String escapeColorCodes(String string) {
        return Strings.stripColors(string);
    }

    /**
     * remove rank symbol & colors
     *
     * @param name the player name (in most cases)
     */
    public static String escapeEverything(String name) {
        return Strings.stripColors(escapeRankTag(name));
    }

    public static String escapeFoosCharacters(String message) {
        StringBuilder sb = new StringBuilder(message);
        for (int i = message.length() - 1; i >= 0; i--) {
            if (sb.charAt(i) >= 0xF80 && sb.charAt(i) <= 0x107F) {
                sb.deleteCharAt(i);
            }
        }
        return sb.toString();
    }

    /**
     * remove rank symbols & colors
     *
     * @param player the player
     */
    public static String escapeEverything(Player player) {
        return escapeEverything(player.name);
    }

    /**
     * Return the rank marker, which includes the player tag
     */
    public static String rankMarker(Rank rank) {
        if (rank.tag == null) return "";
        return "[accent]|[#" + rank.color.toString().substring(0, 6) + "]" + rank.tag + "[accent]|";
    }

    public static String subRankMarker(ArrayList<Integer> ranks) {
        StringBuilder sb = new StringBuilder();
        for (Integer rank : ranks) {
            sb.append("[accent][#" + SubRank.all[rank].color.toString().substring(0, 6) + "]" + SubRank.all[rank].tag + "[accent]|");
        }
        return sb.toString();
    }

    /**
     * Format a player name
     *
     * @param name the player name, including color codes
     */
    public static String formatName(Rank rank, String name) {
        if (rank.tag == null) return name;
        return rankMarker(rank) + " [white]" + name;
    }

    /**
     * Format a player name
     */
    public static String formatName(Database.Player pd, Player player) {
        if (Rank.all[pd.rank].tag == null) return player.name;
        return rankMarker(Rank.all[pd.rank]) + subRankMarker(pd.subranks) + " [#" + player.color().toString().substring(0, 6) + "]" + escapeRankTag(player.name);
    }

    /**
     * Escape rank tag from a properly-formatted player's name
     */
    public static String escapeRankTag(String name) {
        for (Rank rank : Rank.all) {
            String prefix = rankMarker(rank);
            if (name.startsWith(prefix)) name = name.substring(prefix.length());
            if (name.startsWith(" ")) name = name.substring(1);
            prefix = Strings.stripColors(rankMarker(rank));
            if (name.startsWith(prefix)) name = name.substring(prefix.length());
            if (name.startsWith(" ")) name = name.substring(1);
        }
        // some regex magic to remove all color codes
        name = name.replaceAll("\\[accent]\\|\\[#.{7}.\\[accent\\]\\|", "");
        return name;
    }

    /**
     * Check if a string is an IP.
     * This function is wrong because it does not
     * take into account IPv6 addresses.
     */
    @Deprecated
    public static boolean isValidIPAddress(String ip) {
        // If the IP address is empty
        // return false
        if (ip == null) {
            return false;
        }

        // Pattern class contains matcher() method
        // to find matching between given IP address
        // and regular expression.
        Matcher m = ipValidationPattern.matcher(ip);

        // Return if the IP address
        // matched the ReGex
        return m.matches();
    }

    /**
     * Replace %player% with player name, %playtime% with play time etc
     *
     * @param message the message to replace
     * @param player  for the stats
     */
    public static String formatMessage(Player player, String message) {
        try {
            message = message.replace("%player%", escapeCharacters(player.name));
            message = message.replace("%map%", state.map.name());
            message = message.replace("%wave%", String.valueOf(state.wave));
            Database.Player pd = Database.getPlayerData(player.uuid());
            if (pd != null) {
                message = message.replace("%playtime%", String.valueOf(pd.playTime));
                message = message.replace("%games%", String.valueOf(pd.gamesPlayed));
                message = message.replace("%buildings%", String.valueOf(pd.buildingsBuilt));
                message = message.replace("%rank%", Rank.all[pd.rank].tag + " " + escapeColorCodes(Rank.all[pd.rank].name));
//                if(pd.discordLink.lengt > 0){
//                    User discordUser = api.getUserById(pd.discordLink).get(2, TimeUnit.SECONDS);
//                    if(discordUser != null) {
//                        message = message.replaceAll("%discord%", discordUser.getDiscriminatedName());
//                    }
//                } else{
                message = message.replaceAll("%discord%", "unlinked");
//                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        return message;
    }

    /**
     * Change the current map
     *
     * @param found map
     * @deprecated Anything that uses this should be in the RTV minimod
     */
    @Deprecated
    public static void changeMap(Map found) {
        Class<Maps> mapsClass = Maps.class;
        Field mapsField;
        try {
            mapsField = mapsClass.getDeclaredField("maps");
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException("Could not find field 'maps' of class 'mindustry.maps.Maps'");
        }
        mapsField.setAccessible(true);
        Field mapsListField = mapsField;

        Seq<Map> mapsList;
        try {
            mapsList = (Seq<Map>) mapsListField.get(maps);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("unreachable");
        }

        Seq<Map> tempMapsList = mapsList.removeAll(map -> !map.custom || map != found);

        try {
            mapsListField.set(maps, tempMapsList);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("unreachable");
        }

        Events.fire(new EventType.GameOverEvent(Team.crux));

        try {
            mapsListField.set(maps, mapsList);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("unreachable");
        }
        maps.reload();
    }

    /**
     * Format a number, adding "K" to the end if > 1000
     */
    public static String formatInt(int n) {
        if (n > 1000) {
            return String.format("%.1fK", n / 1000.0);
        } else {
            return Integer.toString(n);
        }
    }

    // region discord

//    /**
//     * Send a schematic to channel of the ctx
//     */
//    public static void sendSchem(Schematic schem, Context ctx) {
//        ItemSeq req = schem.requirements();
//        EmbedBuilder eb = new EmbedBuilder()
//                .setColor(new Color(0x00ffff))
//                .setAuthor(ctx.author)
//                .setTitle(schem.name());
//        StringBuilder sb = new StringBuilder();
//        for (ItemStack item : req) {
//            Collection<KnownCustomEmoji> emojis = api.getCustomEmojisByNameIgnoreCase(item.item.name.replaceAll("-", ""));
////            eb.addField(emoijs.iterator().next().getMentionTag(), String.valueOf(item.amount), true);
//            sb.append(emojis.iterator().next().getMentionTag()).append(item.amount).append("    ");
//        }
//        eb.setDescription(schem.description());
//        eb.addField("**Requirements:**", sb.toString());
//        // power emojis
//        String powerPos = api.getCustomEmojisByNameIgnoreCase("power_pos").iterator().next().getMentionTag();
//        String powerNeg = api.getCustomEmojisByNameIgnoreCase("power_neg").iterator().next().getMentionTag();
//        eb.addField("**Power:**", powerPos + "+" + schem.powerProduction() + "    " +
//                powerNeg + "-" + schem.powerConsumption() + "     \n" +
//                powerPos + "-" + powerNeg + (schem.powerProduction() - schem.powerConsumption()));
//
//        // preview schem
//        BufferedImage visualSchem;
//        File imageFile;
//        Fi schemFile;
//        try {
//            visualSchem = contentHandler.previewSchematic(schem);
//            imageFile = new File("temp/" + "image_" + schem.name().replaceAll("[^a-zA-Z0-9\\.\\-]", "_") + ".png");
//            ImageIO.write(visualSchem, "png", imageFile);
//            // crate the .msch file
//            schemFile = new Fi("temp/" + "file_" + schem.name().replaceAll("[^a-zA-Z0-9\\.\\-]", "_") + ".msch");
//            Schematics.write(schem, schemFile);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return;
//        }
//        eb.setImage("attachment://" + imageFile.getName());
//        MessageBuilder mb = new MessageBuilder();
//        mb.addEmbed(eb);
//        mb.addFile(imageFile);
//        mb.addAttachment(schemFile.file());
//        mb.send(ctx.channel);
//    }

    /**
     * Convert a long to formatted time.
     *
     * @param epoch the time in long.
     * @return formatted time
     */
    public static String epochToString(long epoch) {
        Date date = new Date(epoch * 1000L);
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
        return format.format(date) + " UTC";
    }

//    /**
//     * check if the message starts with the schematic prefix
//     */
//    public static boolean checkIfSchem(MessageCreateEvent event) {
//        // check if it's a schem encoded in base64
//        String message = event.getMessageContent();
//        if (event.getMessageContent().startsWith("bXNjaA")) {
//            try {
//                debug("send schem");
//                sendSchem(contentHandler.parseSchematic(message), new Context(event, null, null));
//                event.deleteMessage();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//        // ========= check if it's a schem file ============
//        // download all files
//        Seq<MessageAttachment> ml = new Seq<>();
//        Seq<MessageAttachment> txtData = new Seq<>();
//        for (MessageAttachment ma : event.getMessageAttachments()) {
//            if ((ma.getFileName().split("\\.", 2)[1].trim().equals("msch")) && !event.getMessageAuthor().isBotUser()) { // check if its a .msch file
//                ml.add(ma);
//            }
//            if ((ma.getFileName().split("\\.", 2)[1].trim().equals("txt")) && !event.getMessageAuthor().isBotUser()) { // check if its a .msch file
//                txtData.add(ma);
//            }
//        }
//
//        if (ml.size > 0) {
//            CompletableFuture<byte[]> cf = ml.get(0).downloadAsByteArray();
//            try {
//                byte[] data = cf.get();
//                Schematic schem = Schematics.read(new ByteArrayInputStream(data));
//                sendSchem(schem, new Context(event, null, null));
//                return true;
//            } catch (Exception e) {
//                assert error_log_channel != null;
//                error_log_channel.sendMessage(new EmbedBuilder().setTitle(e.getMessage()).setColor(new Color(0xff0000)));
//                e.printStackTrace();
//            }
//        }
//
//        if (txtData.size > 0) {
//            CompletableFuture<byte[]> cf = txtData.get(0).downloadAsByteArray();
//            try {
//                byte[] data = cf.get();
//                String base64Encoded = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)))
//                        .lines().parallel().collect(Collectors.joining("\n"));
//                Schematic schem = contentHandler.parseSchematic(base64Encoded);
//                sendSchem(schem, new Context(event, null, null));
//                event.deleteMessage();
//                return true;
//            } catch (Exception e) {
//                assert error_log_channel != null;
//                error_log_channel.sendMessage(new EmbedBuilder().setTitle(e.getMessage()).setColor(new Color(0xff0000)));
//                e.printStackTrace();
//            }
//        }
//        return false;
//    }

    /**
     * send the player not found message for discord commands
     */
    public static void playerNotFound(String name, EmbedBuilder eb, Context ctx) {
        eb.setTitle("Command terminated");
        eb.setDescription("Player `" + escapeEverything(name) + "` not found.");
        eb.setColor(new Color(0xff0000));
        ctx.channel().sendMessage(eb);
    }

//    /**
//     * if there are too few arguments for the command
//     */
//    public static void tooFewArguments(Context ctx, Command command) {
//        EmbedBuilder eb = new EmbedBuilder();
//        eb.setTitle("Too few arguments!")
//                .setDescription("Usage: " + ioMain.prefix + command.name + " " + command.usage)
//                .setColor(Pals.error);
//        ctx.sendMessage(eb);
//    }


//    /**
//     * Get a png from map (InputStream)
//     *
//     * @param imageFileName the name of the image where you want to save it
//     */
//    public static void mapToPng(InputStream stream, String imageFileName) throws IOException {
//        debug("start function mapToPng");
//        Http.post(maps_url + "/map").content(stream, stream.available()).block(res -> {
//            debug(res.getStatus());
//            debug("received data (mapToPng)");
//            var pix = new Pixmap(res.getResultAsStream().readAllBytes());
//            PixmapIO.writePng(new Fi("temp/" + "image_" + imageFileName), pix); // Write to a file
//            debug("image height: @", pix.height);
//            debug("image width: @", pix.height);
//        });
//    }

//    /**
//     * Send a map to the mindServ to get a png
//     *
//     * @param map        map File
//     * @param outputFile where the png should be saved
//     * @return whether it was successfully
//     */
//    public static boolean getMapImage(File map, File outputFile) {
//        try {
//            HttpRequest req = HttpRequest.post(maps_url + "/map");
//            req.contentType("application/octet-stream");
//            req.send(map);
//
//            if (req.ok()) {
//                req.receive(outputFile);
//                return true;
//            }
//            Log.warn("@:@", req.code(), req.body());
//            return false;
//        } catch (HttpRequest.HttpRequestException e) {
//            Log.err("Content Server is not running.\n", e);
//            return false;
//        } catch (Exception e) {
//            Log.err(e);
//            return false;
//        }
//    }
/*
    public static void attachMapPng(Map found, EmbedBuilder embed, Context ctx) throws IOException {
        Fi mapFile = found.file;
        attachMapPng(mapFile, embed, ctx);
    }

    public static void attachMapPng(Fi mapFile, EmbedBuilder embed, Context ctx) throws IOException {
        attachMapPng(mapFile, embed, ctx.channel);
    }

    public static void attachMapPng(Fi mapFile, EmbedBuilder embed, TextChannel channel) throws IOException {
        InputStream stream = mapFile.readByteStream();
        String imageFileName = mapFile.name().replaceAll("[^a-zA-Z0-9\\.\\-]", "_").replaceAll(".msav", ".png");
        Log.debug("File size of map: @", stream.readAllBytes().length);
        File imageFile = new File("temp/" + "image_" + imageFileName);
        MessageBuilder mb = new MessageBuilder();
        if (getMapImage(mapFile.file(), imageFile)) {
            debug("received image!");
            embed.setImage("attachment://" + "image_" + imageFileName);
            mb.addEmbed(embed);
            mb.addAttachment(imageFile);
            mb.addAttachment(mapFile.file());
            try {
                mb.send(channel).get();
            } catch (Exception e) {
                Log.err(e);
            }
//            imageFile.delete();
        } else {
            embed.setFooter("Content Server is not running.");
            mb.addEmbed(embed);
            mb.addAttachment(mapFile.file());
            mb.send(channel).join();
        }
    }*/

    public static void registerRankCommand(CommandHandler handler, String text, String params, int minRank, String description, CommandHandler.CommandRunner<Player> runner) {
        handler.<Player>register(text, params, description, (args, player) -> {
            int rank = 0;
            var pd = Database.getPlayerData(player.uuid());
            if (pd != null) {
                rank = pd.rank;
            }

            if (rank >= minRank || player.admin) {
                runner.accept(args, player);
            } else {
                player.sendMessage(GameMsg.noPerms(null));
            }
        });
    }


    // old function to get info from the database
//    public static PlayerData getData(String uuid) {
//        try(Jedis jedis = ioMain.pool.getResource()) {
//            String json = jedis.get(uuid);
//            if(json == null) return null;
//
//            try {
//                return gson.fromJson(json, PlayerData.class);
//            } catch(Exception e){
//                e.printStackTrace();
//                return null;
//            }
//        }
//    }
//
//    public static void setData(String uuid, PlayerData pd) {
//        try(Jedis jedis = ioMain.pool.getResource()) {
//            try {
//                String json = gson.toJson(pd);
//                jedis.set(uuid, json);
//            } catch(Exception e){
//                e.printStackTrace();
//            }
//        }
//    }

    /**
     * Parses color in RRGGBB or RRGGBBAA format
     *
     * @return the color, or {@code null} on failure
     */
    public static arc.graphics.Color parseColor(String rgb) {
        try {
            return arc.graphics.Color.valueOf(rgb);
        } catch (Exception e) {
            return null;
        }
    }

    // copied and pasted from the internet, hope it works
    public static boolean onlyDigits(String str) {
        // Regex to check string
        // contains only digits
        String regex = "[0-9]+";

        // Compile the ReGex
        Pattern p = Pattern.compile(regex);

        // If the string is empty
        // return false
        if (str == null) {
            return false;
        }

        // Find match between given string
        // and regular expression
        // using Pattern.matcher()
        Matcher m = p.matcher(str);

        // Return if the string
        // matched the ReGex
        return m.matches();
    }

    /**
     * WIP
     */
    public static void execute(String command) throws IOException {
        Process process = Runtime.getRuntime().exec(command);
//        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//        String line = "";
////        StringBuilder output = new StringBuilder();
//        while ((line = reader.readLine()) != null) {
//            System.out.println(line);
////            output.append(line);
//        }
    }

    /**
     * WIP
     */
    public static void restartApplication() throws IOException, URISyntaxException {
        final String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        final File currentJar = new File(Core.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        /* is it a jar file? */
        if (!currentJar.getName().endsWith(".jar"))
            return;

        /* Build command: java -jar application.jar */
        final ArrayList<String> command = new ArrayList<String>();
//        command.add("screen -d -r v7 -X stuff $'");
//        command.add("sleep");
//        command.add("0\n");
//        command.add("screen");
//        command.add("-d");
//        command.add("-r");
//        command.add("v7");
//        command.add("-X");
//        command.add("stuff");
//        command.add("java -jar server-release.jar\n");
        command.add(javaBin);
        command.add("-jar");
        command.add(currentJar.getPath());
//        command.add("'");

        final ProcessBuilder builder = new ProcessBuilder(command);
        System.out.println(builder.command());
        builder.start();
        System.exit(0);
    }

    public static String formatMapRanking(Database.MapRank[] ranks, String column) {
        StringBuilder sb = new StringBuilder("```\n");
        sb.append("   ").append(String.format("%9s ", column)).append("Name\n");

        for (int i = 0; i < ranks.length; i++) {
            sb.append(String.format("%2d ", i + 1));
            sb.append(String.format("%9d ", ranks[i].stat));
            sb.append(ranks[i].name);
            sb.append("\n");
        }
        sb.append("```");
        return sb.toString();
    }

    public static String formatPlayerRanking(Database.PlayerRank[] ranks, String column, boolean showUUID) {
        StringBuilder sb = new StringBuilder("```\n");
        sb.append("   ").append(String.format("%9s ", column));
        if (showUUID) {
            sb.append("UUID ");
        }
        sb.append("Name\n");
        for (int i = 0; i < ranks.length; i++) {
            sb.append(String.format("%2d ", i + 1));
            sb.append(String.format("%9d ", ranks[i].stat));

            if (showUUID) {
                sb.append(ranks[i].uuid).append(" ");
            }

            final String uuid = ranks[i].uuid;
            Player p = Groups.player.find(x -> x.uuid().equals(uuid));
            if (p != null) {
                sb.append(p.name);
            }
            sb.append("\n");
        }
        sb.append("```");
        return sb.toString();
    }

    /**
     * Calculates the Phash for a given UUID.
     *
     * @param uuid the UUID
     * @return the Phash
     */
    public static String calculatePhash(String uuid) {
        return hashCache.get(uuid, () -> {
            try {
                byte[] bytes = Base64.getDecoder().decode(uuid);
                MessageDigest messageDigest = MessageDigest.getInstance("SHA3-256");
                messageDigest.update(bytes);
                byte[] hash = messageDigest.digest();
                return Base64.getEncoder().encodeToString(hash).substring(0, 13).replace("+", "-").replace("/", "=");
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    public static SchemImage convertToSchemImage(ObjectSet<Ranks.SimpleBuild> tiles) {
        try {
            // convert the list of destroyed blocks to a schematic
            // first get max and min of x and y values
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = 0, maxY = 0;
            for (Ranks.SimpleBuild tile : tiles) {
                if (tile.tileX < minX) minX = tile.tileX;
                if (tile.tileY < minY) minY = tile.tileY;
                if (tile.tileX > maxX) maxX = tile.tileX;
                if (tile.tileY > maxY) maxY = tile.tileY;
            }
            minX -= 1;
            minY -= 1;
            maxX += 1;
            maxY += 1;
            // scale all the min and max values too
            maxX -= minX;
            maxY -= minY;
            Seq<Schematic.Stile> stiles = new Seq<>();
            IntSet counted = new IntSet();
            for (Ranks.SimpleBuild build : tiles) {
                // we first have to check if it's a multiblock, cause then we only need to specify the top left corner of the block, cause the block is larger than 1x1

                if (build.block.localizedName.contains("build")) continue;
//                debug("x: " + build.x + " y: " + build.y + " block: " + build.blockName);
                // (Block block, int x, int y, Object config, byte rotation
//                stiles.add(new Schematic.Stile(Vars.content.block(build.blockName), build.x - minX, build.y - minY, build.config, build.rotation));
                // log the place where the block was placed in the schem
                Block realBlock = build.realBlock;

                if (build != null && !counted.contains(build.pos()) && realBlock != null
                        && (realBlock.isVisible() || realBlock instanceof CoreBlock)) {
                    Object config = build.config;
                    stiles.add(new Schematic.Stile(realBlock, build.tileX - minX, build.tileY - minY, config, (byte) build.rotation));
                    counted.add(build.pos());
                }
//                                        Log.info("[Anti-griefer-system] " + event.unit.getPlayer().name + " destroyed " + building.block.localizedName + " at " + (tile.tileX - minX) + ", " + (tile.tileY - minY) + " rotation: " + building.rotation);
            }
            Schematic schematic = new Schematic(stiles, new StringMap(), maxX + 2, maxY + 2);
            debug(new Schematics().writeBase64(schematic));
            try {
                BufferedImage image;
                if (PluginConfig.autoBanSystem) {
                    // render the image
                    image = contentHandler.previewSchematic(schematic);
                } else {
                    image = contentHandler.getImage("error");
                }
                return new SchemImage(image, minX, minY, maxX + minX, maxY + minY);
            } catch (Exception e) {
                Log.err("Something went wrong creating SchemImage:\n", e);
                return new SchemImage(null, minX, minY, maxX + minX, maxY + minY);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Schematic create(int x, int y, int x2, int y2, Seq<Ranks.SimpleBuild> dBuilds) {
        Placement.NormalizeResult result = Placement.normalizeArea(x, y, x2, y2, 0, false, maxSchematicSize);
        x = result.x;
        y = result.y;
        x2 = result.x2;
        y2 = result.y2;

        int ox = x, oy = y, ox2 = x2, oy2 = y2;

        Seq<Schematic.Stile> tiles = new Seq<>();

        int minx = x2, miny = y2, maxx = x, maxy = y;
        boolean found = false;
        for (int cx = x; cx <= x2; cx++) {
            for (int cy = y; cy <= y2; cy++) {
                Building linked = world.build(cx, cy);
                Block realBlock = linked == null ? null : linked instanceof ConstructBlock.ConstructBuild cons ? cons.current : linked.block;

                if (linked != null && realBlock != null && (realBlock.isVisible() || realBlock instanceof CoreBlock)) {
                    int top = realBlock.size / 2;
                    int bot = realBlock.size % 2 == 1 ? -realBlock.size / 2 : -(realBlock.size - 1) / 2;
                    minx = Math.min(linked.tileX() + bot, minx);
                    miny = Math.min(linked.tileY() + bot, miny);
                    maxx = Math.max(linked.tileX() + top, maxx);
                    maxy = Math.max(linked.tileY() + top, maxy);
                    found = true;
                }
            }
        }

        for (Ranks.SimpleBuild tile : dBuilds) {
            Block realBlock = tile.realBlock;

            if (tile != null && realBlock != null && (realBlock.isVisible() || realBlock instanceof CoreBlock)) {
                int top = realBlock.size / 2;
                int bot = realBlock.size % 2 == 1 ? -realBlock.size / 2 : -(realBlock.size - 1) / 2;
                minx = Math.min(tile.tileX + bot, minx);
                miny = Math.min(tile.tileY + bot, miny);
                maxx = Math.max(tile.tileX + top, maxx);
                maxy = Math.max(tile.tileY + top, maxy);
                found = true;
            }
        }

        if (found) {
            x = minx;
            y = miny;
            x2 = maxx;
            y2 = maxy;
        } else {
            return new Schematic(new Seq<>(), new StringMap(), 1, 1);
        }

        int width = x2 - x + 1, height = y2 - y + 1;
        int offsetX = -x, offsetY = -y;
        IntSet counted = new IntSet();
        for (int cx = ox; cx <= ox2; cx++) {
            for (int cy = oy; cy <= oy2; cy++) {
                Building tile = world.build(cx, cy);
                Block realBlock = tile == null ? null : tile instanceof ConstructBlock.ConstructBuild cons ? cons.current : tile.block;

                if (tile != null && !counted.contains(tile.pos()) && realBlock != null
                        && (realBlock.isVisible() || realBlock instanceof CoreBlock)) {
                    Object config = tile instanceof ConstructBlock.ConstructBuild cons ? cons.lastConfig : tile.config();

                    tiles.add(new Schematic.Stile(realBlock, tile.tileX() + offsetX, tile.tileY() + offsetY, config, (byte) tile.rotation));
                    counted.add(tile.pos());
                }
            }
        }

        for (Ranks.SimpleBuild b : dBuilds) {
            Block realBlock = b.realBlock;

            if (b != null && !counted.contains(b.pos()) && realBlock != null
                    && (realBlock.isVisible() || realBlock instanceof CoreBlock)) {
                Object config = b.config;

                tiles.add(new Schematic.Stile(realBlock, b.tileX() + offsetX, b.tileY() + offsetY, config, (byte) b.rotation));
                counted.add(b.pos());
            }
        }

        return new Schematic(tiles, new StringMap(), width, height);
    }

    /**
     * Send message without response handling
     *
     * @param user    User to dm
     * @param content message
     */
    public void sendMessage(User user, String content) {
        user.openPrivateChannel().join().sendMessage(content);
    }

    public static class Message {
        public static String stat() {
            return Core.settings.getString("statMessage");
        }

        public static String welcome() {
            return Core.settings.getString("welcomeMessage");
        }

        public static String info() {
            return Core.settings.getString("infoMessage");
        }

        public static String rules() {
            return Core.settings.getString("ruleMessage");
        }
    }


//        public static getCore(Team team){
//        Tile[][] tiles = world.getTiles();
//        for (int x = 0; x < tiles.length; ++x) {
//            for(int y = 0; y < tiles[0].length; ++y) {
//                if (tiles[x][y] != null && tiles[x][y].entity != null) {
//                    TileEntity ent = tiles[x][y].ent();
//                    if (ent instanceof CoreBlock.CoreEntity) {
//                        if(ent.getTeam() == team){
//                            return (CoreBlock.CoreBuild) ent;
//                        }
//                    }
//                }
//            }
//        }
//        return null;
//    }

    public static class SchemImage {
        public BufferedImage image;
        public int minX;
        public int minY;
        public int maxX;
        public int maxY;

        public SchemImage(BufferedImage image, int minX, int minY, int maxX, int maxY) {
            this.image = image;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }

    public static class Categories {
        public static final String moderation = "Moderation";
        public static final String management = "Management";
        public static final String mapReviewer = "Map Reviewer";
    }

    public static class uMap {
        public String name, author, description;
        public ObjectMap<String, String> tags = new ObjectMap<>();
        public BufferedImage image;
        public BufferedImage terrain;
    }

    public static class Pair<T, K> {
        public T first;
        public K second;

        public Pair(T first, K second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public String toString() {
            return "Pair{" +
                    "first=" + first +
                    ", second=" + second +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pair<?, ?> pair)) return false;
            return first.equals(pair.first) && second.equals(pair.second);
        }

        @Override
        public int hashCode() {
            return Objects.hash(first, second);
        }
    }
}
