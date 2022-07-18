/*
package mindustry.plugin.discord.commands;

import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;
import mindustry.core.GameState;
import mindustry.game.Gamemode;
import mindustry.gen.Call;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.mod.Mods;
import mindustry.net.Administration;
import mindustry.net.Packets;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.Context;
import mindustry.plugin.discord.discordcommands.DiscordCommands;
import mindustry.plugin.discord.discordcommands.RoleRestrictedCommand;
import mindustry.plugin.requests.GetMap;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static arc.util.Log.*;
import static mindustry.Vars.*;
import static mindustry.plugin.ioMain.*;
import static mindustry.plugin.utils.Utils.Categories.*;
import static mindustry.plugin.utils.Utils.*;

public class Admin {
    private final JSONObject data;
    public GetMap map = new GetMap();

    public Admin(JSONObject data) {
        this.data = data;
    }

    public void registerCommands(DiscordCommands handler) {
        long adminRole = Roles.ADMIN;
        // TODO: make an update command to update the EI mod

        handler.registerCommand(new RoleRestrictedCommand("removemod") {
            {
                help = "Remove a mod from the folder";
                roles = new long[] { adminRole };
                usage = "<mapname/mapid>";
                category = mapReviewer;
                minArguments = 1;
                aliases.add("rmod");
            }

            @Override
            public void run(Context ctx) {
                EmbedBuilder eb = new EmbedBuilder();
                debug(ctx.message);
                Mods.LoadedMod mod = getMod(ctx.message);
                if (mod == null) {
                    eb.setTitle("Command terminated.");
                    eb.setColor(Utils.Pals.error);
                    eb.setDescription("Mod not found");
                    ctx.sendMessage(eb);
                    return;
                }
                debug(mod.file.file().getAbsoluteFile().getAbsolutePath());
                Path path = Paths.get(mod.file.file().getAbsoluteFile().getAbsolutePath());
                mod.dispose();

                try {
                    Files.delete(path);
                } catch (IOException e) {
                    e.printStackTrace();
                    eb.setTitle("There was an error performing this command.")
                            .setDescription(e.getMessage())
                            .setColor(new Color(0xff0000));
                    return;
                }
                if (mod.file.file().getAbsoluteFile().delete()) {
                    eb.setTitle("Command executed.");
                    eb.setDescription(mod.name + " was successfully removed from the folder.\nRestart the server to disable the mod (`<restart <server>`).");
                } else {
                    eb.setTitle("There was an error performing this command.")
                            .setColor(new Color(0xff0000));
                }
                ctx.sendMessage(eb);
            }
        });
    }
}
*/