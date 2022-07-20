package mindustry.plugin.minimods;

import arc.Core;
import arc.struct.IntSeq;
import arc.util.Http;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.net.Administration;
import mindustry.net.Packets;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.Channels;
import mindustry.plugin.discord.DiscordLog;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.DiscordVars;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Config;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.Embed;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.JSONObject;

import java.awt.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;

public class Management implements MiniMod {
    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("test", "[time]",
                data -> {
                    data.help = "Test server TPS stability";
                    data.roles = new long[]{Roles.APPRENTICE, Roles.MOD, Roles.ADMIN};
                    data.category = "Management";
                },
                ctx -> {
                    long time = ctx.args.getLong("time", 1000);

                    TestData data = new TestData();
                    final Runnable[] scanTPS = new Runnable[1];
                    final long endTime = System.currentTimeMillis() + time;
                    scanTPS[0] = () -> {
                        if (System.currentTimeMillis() > endTime) {
                            ctx.reply(new MessageBuilder()
                                    .addEmbed(
                                            new EmbedBuilder()
                                                    .setColor(Color.YELLOW)
                                                    .setTitle("Stability Test Results")
                                                    .setDescription(
                                                            "Min TPS: " + data.min() + "\n" +
                                                                    "Max TPS: " + data.max() + "\n" +
                                                                    "Avg TPS: " + data.avg() + "\n" +
                                                                    "Median TPS: " + data.med() + "\n"
                                                    ))
                                    .addAttachment(data.csv().getBytes(), "data.csv")
                            );
                        } else {
                            data.tpsMeasurements.add(Core.graphics.getFramesPerSecond());
                            Core.app.post(scanTPS[0]);
                        }
                    };
                    Core.app.post(scanTPS[0]);

                    ctx.success("Stability Test Started", "Results will come out in " + time + "ms");
                }
        );

        handler.register("gc", "",
                data -> {
                    data.help = "Trigger a garbage collection. Testing only.";
                    data.roles = new long[]{Roles.APPRENTICE, Roles.MOD, Roles.ADMIN};
                    data.category = "Management";
                },
                ctx -> {
                    double pre = (Core.app.getJavaHeap() / 1024.0 / 1024.0);
                    System.gc();
                    double post = (Core.app.getJavaHeap() / 1024.0 / 1024.0);

                    ctx.sendEmbed(new EmbedBuilder()
                            .setColor(Color.YELLOW)
                            .setTitle("Garbage Collected")
                            .setDescription((post - pre) + " MB of garbage collected")
                            .addInlineField("Pre-GC usage", pre + " MB")
                            .addInlineField("Post-GC usage", post + " MB")
                    );
                }
        );

        handler.register("syncserver", "", 
                data -> {
                    data.help = "Re-sync everyone on the server. May kick everyone, you never know!";
                    data.roles = new long[] {  Roles.MOD, Roles.ADMIN, Roles.APPRENTICE };
                    data.category = "Management";
                },
                ctx -> {
                    for (Player p : Groups.player) { 
                        p.getInfo().lastSyncTime = Time.millis();
                        Call.worldDataBegin(p.con);
                        Vars.netServer.sendWorldData(p);
                    }

                    ctx.success("Re-synced server", "Synced " + Groups.player.size() + " players");
                }
        );

        handler.register("config", "[name] [value...]", d -> {
            d.help = "Configure server settings (`Administration.Config`)";
            d.category = "Management";
            d.roles = new long[]{Roles.ADMIN};
        }, ctx -> {
            if (!ctx.args.containsKey("name")) {
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("All Config Values");
                for (Administration.Config c : Administration.Config.all) {
                    eb.addField(c.name + ": " + c.get(), c.description, true);
                }
                eb.setColor(Color.CYAN);
                ctx.sendEmbed(eb);
                return;
            }

            try {
                Administration.Config c = Administration.Config.all.find(conf -> conf.name.equalsIgnoreCase(ctx.args.get("name")));
                if (!ctx.args.containsKey("value")) {
                    ctx.sendEmbed(Color.CYAN, "Configuration", c.name + " is currently <" + c.getClass().getName() + "> " + c.get());
                    return;
                }

                Object previousValue = c.get();

                if (c.isBool()) {
                    c.set(ctx.args.get("value").equals("true") || ctx.args.get("value").equals("on"));
                } else if (c.isNum()) {
                    c.set(ctx.args.getInt("value", 0));
                } else {
                    c.set(ctx.args.get("value").replace("\\n", "\n"));
                }

                ctx.sendEmbed(Color.CYAN, "Configuration", c.name + " is now set to (`" + c.get().getClass().getSimpleName() + "`) " + c.get() + "\n\nPrevious Value: " + previousValue);
            } catch (IllegalArgumentException e) {
                ctx.error("Unknown Configuration", e.getMessage());
            }
        });

        handler.register("setting", "[name] [type] [value...]", 
            data -> {
                data.help = "Configure server settings (`Core.settings`). It is recommended to use " + DiscordVars.prefix + "config instead of this command.";
                data.category = "Management";
                data.roles = new long [] { Roles.ADMIN };
            },
            ctx -> {
                if (!ctx.args.containsKey("name")) {
                    StringBuilder data = new StringBuilder(String.format("%-48s, %-12s, %s\n", "Setting", "Type", "Value"));
                    int n = 0;
                    for (String key : Core.settings.keys()) {
                        Object value = Core.settings.get(key,null);
                        data.append(String.format("%-48s, %-12s, %s\n", key, value == null ? "Null" : value.getClass().getSimpleName(), value == null ? "null": value.toString()));
                        n++;
                    }
                    EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("Settings")
                        .setColor(DiscordPalette.INFO)
                        .setDescription("Number of settings: " + n);
                    ctx.sendMessage(
                        new MessageBuilder()
                           .addEmbed(eb)
                            .addAttachment(data.toString().getBytes(), "data.csv")
                        );
                    return;
                }

                String name = ctx.args.get("name");
                if (!ctx.args.containsKey("type")) {
                    Object o = Core.settings.get(name, null);                    
                    if (o == null) {
                        ctx.error("No such setting", "Setting " + name + " does not exist");
                        return;
                    }
                    
                    ctx.success("Success", "Setting **" + name + "** is currently (`" + o.getClass().getSimpleName() + "`) " + o.toString());
                    return;
                }

                if (!ctx.args.containsKey("value")) {
                    ctx.error("Must provide value", "Missing value");
                    return;
                }

                String typeName = ctx.args.get("type");
                String valueStr = ctx.args.get("value");
                Object value;
                if (typeName.equalsIgnoreCase("String")) {
                    value = valueStr;
                } else if (typeName.equalsIgnoreCase("int")) {
                    value = Strings.parseInt(valueStr, 0);
                } else if (typeName.equalsIgnoreCase("long")) {
                    value = Strings.parseLong(valueStr, 0);
                } else if (typeName.equalsIgnoreCase("double")) {
                    value = Strings.parseDouble(valueStr, 0);
                } else {
                    ctx.error("Unknown type", "Please contact a developer to add support for more types");
                    return;
                }
                Core.settings.put(name, value);

                ctx.success("Success", "Setting **" + name + "** was set to (`" + value.getClass().getSimpleName() + "`) " + value.toString());
            }
        );

        handler.register("start", "[map] [mode]", d -> {
            d.help = "Restart the server";
            d.roles = new long[]{Roles.ADMIN};
            d.category = "Management";
            d.aliases = new String[]{"restart"};
        }, ctx -> {
            Vars.net.closeServer();
            Vars.state.set(GameState.State.menu);

            Gamemode mode = Gamemode.survival;
            if (ctx.args.containsKey("mode")) {
                try {
                    mode = Gamemode.valueOf(ctx.args.get("mode"));
                } catch (IllegalArgumentException e) {
                    ctx.error("Invalid Gamemode", "'" + ctx.args.get("mode") + "' is not a valid game mode.");
                    return;
                }
            }

            Map map = Vars.maps.getShuffleMode().next(mode, Vars.state.map);
            if (ctx.args.containsKey("map")) {
                map = Utils.getMapBySelector(ctx.args.get("map"));
                if (map == null) {
                    ctx.error("Invalid Map", "Map '" + ctx.args.get("map") + "' does not exist");
                }
            }

            try {
                Vars.world.loadMap(map, map.applyRules(mode));
                Vars.state.rules = map.applyRules(mode);
                Vars.logic.play();
                Vars.netServer.openServer();

                ctx.success("Map Loaded", "Hosting map: " + map.name());
            } catch (MapException e) {
                ctx.error("Internal Error", e.map.name() + ": " + e.getMessage());
            }
        });

        handler.register("exit", "", d -> {
            d.help = "Close the server.";
            d.roles = new long[]{Roles.ADMIN};
            d.category = "Management";
        }, ctx -> {
            Channels.LOG.sendMessage(new EmbedBuilder()
                    .setTitle(ctx.author().getDisplayName(ctx.server()) + " closed the server!")
                    .setColor(new Color(0xff0000))).join();
            ctx.success("Success", "Closed the server");

            Log.info("&ly--SERVER RESTARTING--");
            Vars.netServer.kickAll(Packets.KickReason.serverRestarting);
            Time.runTask(5f, () -> {
                Core.app.exit();
            });
        });

        handler.register("iplookup", "<ip|player>", 
            data -> {
                data.help = "Make an IP lookup";
                data.roles = new long [] { Roles.MOD, Roles.ADMIN };
                data.category = "Management";
                data.aliases = new String[] { "il" };
            },
            ctx -> {
                String name = ctx.args.get("ip|player");
                var infos =  Vars.netServer.admins.searchNames(name); // strip colors is active
                if (infos.size > 1) {
                    ctx.error("Multiple players found that match name", infos.toSeq().toString("\n", i -> i.lastName));
                }
                String ip;
                if (infos.size == 1) {
                    ip = infos.iterator().next().lastIP;
                } else {
                    if (!name.matches("[a-f0-9:.]+")) {
                        ctx.error("Player not found", "'" + name + "' is neither a valid player or a valid IP");
                    }
                    ip = name;
                }
                
                Http.get("http://api.ipapi.com/" + ip + "?access_key=" + Config.ipApiKey, resp -> {
                    JSONObject json = new JSONObject(resp.getResultAsString());
                    EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("Lookup " + ip)
                        .setColor(DiscordPalette.INFO)
                        .addField("Continent", json.getString("continent_name"), true)
                        .addField("City", json.getString("city"), true)
                        .addField("Country", json.getString("country_name"), true)
                        .addField("Region", json.getString("region_name"), true)
                        .addField("Latitude", String.valueOf(Float.valueOf(BigDecimal.valueOf(json.getDouble("latitude")).floatValue())), true)
                        .addField("Longitude", String.valueOf(Float.valueOf(BigDecimal.valueOf(json.getDouble("longitude")).floatValue())), true);

                    if (json.has("zip")) {
                        eb.addInlineField("Zip Code", json.get("zip").toString());
                    }

                    ctx.sendEmbed(eb);
                }, err -> {
                    Log.err(err);
                    DiscordLog.error("IpApi lookup failed", err.getMessage(), null);
                });
            }
        );

        handler.register("admin", "<id|ip|name>", 
            data -> {
                data.help = "Toggle admin status of a player";
                data.roles = new long [] { Roles.ADMIN, Roles.MOD };
                data.category = "Management";
            },
            ctx -> {
                String q = ctx.args.get("id|ip|name");
                Player p = Utils.findPlayer(q);
                if (p == null) {
                    ctx.error("Player not found", "Player '" + q + "' does not exist");
                    return;
                }

                var info = p.getInfo();
                if (!p.admin) { 
                    Vars.netServer.admins.adminPlayer(info.id, info.adminUsid);
                    p.admin = true;
                    ctx.success("Success", "Promoted " + Utils.escapeEverything(p) + " to admin");
                } else {
                    Vars.netServer.admins.unAdminPlayer(info.id);
                    p.admin = false;
                    ctx.success("Success", "Demoted " + Utils.escapeEverything(p) + " from admin");
                }
                Vars.netServer.admins.save();
            }
        );
    }

    private static class TestData {
        public IntSeq tpsMeasurements;

        public TestData() {
            tpsMeasurements = new IntSeq();
        }

        public int min() {
            if (tpsMeasurements.size == 0) return 0;

            int min = Integer.MAX_VALUE;
            for (int i = 0; i < tpsMeasurements.size; i++) {
                int tps = tpsMeasurements.get(i);
                if (tps < min) min = tps;
            }
            return min;
        }

        public int max() {
            int max = 0;
            for (int i = 0; i < tpsMeasurements.size; i++) {
                int tps = tpsMeasurements.get(i);
                if (tps > max) max = tps;
            }
            return max;
        }

        public double avg() {
            if (tpsMeasurements.size == 0) return 0;

            return (double) tpsMeasurements.sum() / (double) tpsMeasurements.size;
        }

        /**
         * Returns the median TPS
         */
        public int med() {
            if (tpsMeasurements.size == 0) return 0;

            IntSeq s = new IntSeq(tpsMeasurements);
            s.sort();
            return s.get(s.size / 2);
        }

        /**
         * Returns the data as a CSV string
         */
        public String csv() {
            StringBuilder sb = new StringBuilder();
            sb.append("Iteration,TPS\n");
            int iter = 1;
            for (int i = 0; i < tpsMeasurements.size; i++) {
                int tps = tpsMeasurements.get(i);
                sb.append(iter);
                sb.append(",");
                sb.append(tps);
                sb.append("\n");

                iter++;
            }
            return sb.toString();
        }
    }
}
