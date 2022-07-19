package mindustry.plugin.minimods;

import arc.struct.ObjectMap;
import arc.util.CommandHandler;
import mindustry.gen.Player;
import mindustry.plugin.MiniMod;
import mindustry.plugin.database.Database;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.DiscordVars;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.GameMsg;
import mindustry.plugin.utils.Rank;

import java.util.Optional;

import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

public class Redeem implements MiniMod {
    /**
     * Map of key => discord ID
     */
    private ObjectMap<String, Long> keys = new ObjectMap<>();

    @Override
    public void registerCommands(CommandHandler handler) {
        handler.<Player>register("redeem", "<key>", "Confirm redeem on discord", (args, player) -> {
            String key = args[0];
            if (!keys.containsKey(key)) {
                player.sendMessage(GameMsg.error("Redeem", "Not a valid code. Try using the redeem command in Discord again."));
                return;
            }
            long discordId = keys.get(key).longValue();
            keys.remove(key);

            Optional<Server> serverOpt = DiscordVars.api.getServers().stream().filter(s -> s.getMemberById(discordId).isPresent()).findAny();
            if (!serverOpt.isPresent()) {
                player.sendMessage(GameMsg.error("Redeem", "Player with id " + discordId + " could not be found."));
            }
            Server server = serverOpt.get();

            // update database
            Database.Player pd = Database.getPlayerData(player.uuid());
            if (pd == null) {
                pd = new Database.Player(player.uuid(), 0);
            }
            pd.discord = discordId;
            Database.setPlayerData(pd);

            // update discord roles
            var updater = server.createUpdater();
            User user = server.getMemberById(discordId).get();
            for (var entry : Rank.roles) {
                long roleID = entry.key;
                int rankIdx = entry.value;
                if (rankIdx <= pd.rank) {
                    updater.addRoleToUser(user, server.getRoleById(roleID).get());
                }
            }
            updater.update().join();

            player.sendMessage(GameMsg.success("Redeem", "Successfully linked discord account"));
        });
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("redeem", "",
                data -> {
                    data.help = "Redeem a key";
                },
                ctx -> {
                    String key = Long.toString((long) (Math.random() * 0xffffffffffL), 36).substring(0, 4);
                    keys.put(key, ctx.author().getId());
                    ctx.success("Redeem", "Check your DMs for next steps.");
                    ctx.author().sendMessage(new EmbedBuilder()
                            .setTitle("Redeem")
                            .setDescription("Redeem Key: " + key + "\n\nType `/redeem " + key + "` on the Mindustry server.")
                            .setColor(DiscordPalette.INFO)
                    );
                }
        );
    }
}
