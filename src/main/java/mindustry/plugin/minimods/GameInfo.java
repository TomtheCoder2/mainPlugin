package mindustry.plugin.minimods;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.SaveIO;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Config;
import mindustry.plugin.utils.ContentServer;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.type.Item;
import mindustry.world.modules.ItemModule;
import org.javacord.api.entity.emoji.KnownCustomEmoji;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import static mindustry.plugin.discord.DiscordVars.api;
import static mindustry.plugin.utils.Utils.escapeEverything;

/**
 * Provides information relating to the current game.
 */
public class GameInfo implements MiniMod {
    private static long serverStartTime;

    static {
        serverStartTime = System.currentTimeMillis();
    }

    @Override
    public void registerEvents() {
        Events.on(EventType.ServerLoadEvent.class, event -> {
            serverStartTime = System.currentTimeMillis();
        });
    }

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("players", "Display all players and their ids", (args, player) -> {
            StringBuilder builder = new StringBuilder();
            builder.append("[orange]List of players: \n");
            for (Player p : Groups.player) {
                if (p.admin) {
                    builder.append("[accent]");
                } else {
                    builder.append("[lightgray]");
                }
                builder.append(p.name).append("[accent] : ").append(Utils.calculatePhash(p.uuid())).append("\n");
            }
            player.sendMessage(builder.toString());
        });
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("players", "",
                data -> {
                    data.help = "List online players and their IDs.";
                    data.aliases = new String[]{"p"};
                },
                ctx -> {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("Players: " + Groups.player.size());
                    String desc = "```\n";
                    for (Player p : Groups.player) {
                        var pd = Database.getPlayerData(p.uuid());
                        String phash = pd != null ? pd.phash : Utils.calculatePhash(p.uuid());

                        desc += String.format("%-13s : %s", phash, escapeEverything(p.name()));
                        if (pd != null && pd.rank != 0) {
                            desc += " (" + Rank.all[pd.rank].name + ")";
                        }
                        desc += "\n";
                    }
                    desc += "```";
                    eb.setDescription(desc);
                    eb.setColor(DiscordPalette.INFO);
                    ctx.sendEmbed(eb);
                }
        );

        handler.register("playerinfo", "",
                data -> {
                    data.help = "List online players, their IDs, IPs, and UUIDs";
                    data.aliases = new String[]{"playersinfo", "pi"};
                    data.roles = new long[]{Roles.ADMIN, Roles.MOD};
                    data.category = "Management";
                },
                ctx -> {
                    StringBuilder desc = new StringBuilder();
                    for (Player p : Groups.player) {
                        var pd = Database.getPlayerData(p.uuid());
                        String phash = pd != null ? pd.phash : Utils.calculatePhash(p.uuid());
                        desc.append(String.format("`%-13s : %-24s : %-16s :` %s%s\n", phash, p.uuid(), p.con.address, Utils.escapeEverything(p.name), 
                            pd != null && pd.rank != 0 ? " ("  + Rank.all[pd.rank].name + ")" : ""));
                    }
                    ctx.sendEmbed(DiscordPalette.INFO, "Players online: " + Groups.player.size(), desc.toString());
                }
        );

        handler.register("status", "",
                data -> {
                    data.help = "Show current status of the server";
                    data.aliases = new String[]{"s"};
                },
                ctx -> {
                    if (!Vars.state.is(GameState.State.playing)) {
                        ctx.error("Not Currently Hosting", "Please ping an admin.");
                        return;
                    }

                    long serverUptime = System.currentTimeMillis() - serverStartTime;
                    long serverUptimeSecs = serverUptime / 1000;
                    long serverUptimeMins = serverUptimeSecs / 60;
                    serverUptimeSecs -= serverUptimeMins * 60;
                    long serverUptimeHours = serverUptimeMins / 60;
                    serverUptimeMins -= serverUptimeHours * 60;
                    long serverUptimeDays = serverUptimeHours / 24;
                    serverUptimeHours -= serverUptimeDays * 24;

                    StringBuilder uptimeSb = new StringBuilder();
                    if (serverUptimeDays != 0) {
                        uptimeSb.append(serverUptimeDays).append("d");
                    }
                    if (serverUptimeHours != 0) {
                        uptimeSb.append(serverUptimeHours).append("h");
                    }
                    if (serverUptimeMins != 0) {
                        uptimeSb.append(serverUptimeMins).append("m");
                    }
                    if (serverUptimeSecs != 0) {
                        uptimeSb.append(serverUptimeSecs).append("s");
                    }

                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle(Config.serverName)
                            .addInlineField("Players", Groups.player.size() + "")
                            .addInlineField("Map", Vars.state.map.name())
                            .addInlineField("Wave", Vars.state.wave + "")
                            .addInlineField("TPS", Core.graphics.getFramesPerSecond() + "")
                            .addInlineField("Next wave in", Math.round(Vars.state.wavetime / (double) Core.graphics.getFramesPerSecond()) + " seconds")
                            .addInlineField("Server Uptime", uptimeSb.toString())
                            .setImage(ContentServer.renderGame())
                            .setColor(DiscordPalette.INFO);

                    Fi tempDir = new Fi("temp/");
                    Fi mapFile = tempDir.child(Config.serverName + "_" + escapeEverything(Vars.state.map.name()).replaceAll("[^a-zA-Z0-9\\.\\-]", "_") + ".msav");
                    Core.app.post(() -> {
                        try {
                            SaveIO.write(mapFile);

                            Log.info("Saved to @", mapFile);
                            Log.debug(mapFile.absolutePath());

                            ctx.sendMessage(new MessageBuilder()
                                    .addEmbed(eb)
                                    .addAttachment(mapFile.file())
                            );
                        } catch (Exception e) {
                            e.printStackTrace();
                            ctx.error("Internal Error", e.getMessage());
                        }
                    });
                }
        );

        HashMap<Item, KnownCustomEmoji> emojiHashMap = new HashMap<>();

        handler.register("resinfo", "[team]",
                data -> {
                    data.help = "Show amount of resources in core.";
                },
                ctx -> {
                    if (emojiHashMap.isEmpty()) {
                        for (Field field : Items.class.getDeclaredFields()) {
                            if (field.getType().equals(Item.class)) {
                                try {
                                    Item item = (Item) field.get(null);
                                    Collection<KnownCustomEmoji> emojis = api.getServerById(861522903522607124L).get().getCustomEmojisByNameIgnoreCase(item.name.replaceAll("-", ""));
                                    System.out.println(item.name.replaceAll("-", ""));
                                    System.out.println(Arrays.toString(emojis.toArray()));
                                    emojiHashMap.putIfAbsent(item, emojis.iterator().next());
                                } catch (IllegalAccessException e) {
                                    Log.err(e);
                                }
                            }
                        }
//                        System.out.println(emojiHashMap);
                    }
                    Team team = Vars.state.rules.defaultTeam;
                    if (ctx.args.containsKey("team")) {
                        team = Seq.with(Team.all).find(x -> x.name.equalsIgnoreCase(ctx.args.get("team")));
                        if (team == null) {
                            ctx.error("Error", "Team '" + ctx.args.get("team") + "' not found.");
                            return;
                        }
                    }
                    TeamData data = Vars.state.teams.get(team);
                    if (data.core() == null) {
                        ctx.error("Error", "Team " + team.name + " does not have any cores!");
                        return;
                    }
                    ItemModule items = data.core().items;
                    EmbedBuilder eb = new EmbedBuilder().setTitle("Core: " + team.name);
                    for (Field field : Items.class.getDeclaredFields()) {
                        if (field.getType().equals(Item.class)) {
                            try {
                                Item item = (Item) field.get(null);
                                eb.addInlineField(item.localizedName, items.get(item) + "");
//                                eb.addInlineField(emojiHashMap.get(item).getMentionTag(), items.get(item) + "");
                            } catch (IllegalAccessException e) {
                                Log.err(e);
                            }
                        }
                    }
                    eb.setColor(DiscordPalette.INFO);
                    ctx.sendEmbed(eb);
                }
        );
    }
}
