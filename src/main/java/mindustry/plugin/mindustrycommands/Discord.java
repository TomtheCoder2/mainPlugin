package mindustry.plugin.mindustrycommands;

import arc.util.CommandHandler;
import mindustry.content.Blocks;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.ioMain;
import mindustry.plugin.data.PersistentPlayerData;
import mindustry.plugin.database.Database;
import mindustry.plugin.utils.Rank;
import mindustry.plugin.utils.Utils;
import mindustry.world.Tile;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import static mindustry.Vars.world;
import static mindustry.plugin.ioMain.CDT;
import static mindustry.plugin.ioMain.CommandCooldowns;
import static mindustry.plugin.utils.Utils.escapeEverything;
import static mindustry.plugin.utils.Utils.getTextChannel;

import java.util.concurrent.ExecutionException;

public class Discord implements MiniMod {
    @Override
    public void registerCommands(CommandHandler handler) {
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


        handler.<Player>register("redeem", "<key>", "Verify the redeem command (Discord)", (arg, player) -> {
            try {
                PersistentPlayerData tdata = (ioMain.playerDataGroup.getOrDefault(player.uuid(), null));
                if (tdata.redeemKey != -1) {
                    if (Integer.parseInt(arg[0]) == tdata.redeemKey) {
                        StringBuilder roleList = new StringBuilder();
                        Database.Player pd = Database.getPlayerData(player.uuid());
                        for (var entry: Rank.roles) {
                            long roleID = entry.key;
                            assert pd != null;
                            if (entry.value <= pd.rank) {
                                System.out.println("add role: " + ioMain.api.getRoleById(roleID).get());
                                roleList.append("<@").append(ioMain.api.getRoleById(roleID).get().getIdAsString()).append(">\n");
                                ioMain.api.getUserById(tdata.redeem).get().addRole(ioMain.api.getRoleById(roleID).get());
                            }
                        }
                        System.out.println(roleList);
                        Utils.getTextChannel(ioMain.log_channel_id).sendMessage(new EmbedBuilder().setTitle("Updated roles!").addField("Discord Name", ioMain.api.getUserById(tdata.redeem).get().getName(), true).addField("In Game Name", tdata.origName, true).addField("In Game UUID", player.uuid(), true).addField("Added roles", roleList.toString(), true));
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
    }
}
