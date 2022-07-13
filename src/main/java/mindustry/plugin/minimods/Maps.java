package mindustry.plugin.minimods;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.zip.InflaterInputStream;

import org.javacord.api.entity.message.MessageAttachment;

import arc.Core;
import arc.files.Fi;
import mindustry.Vars;
import mindustry.io.SaveIO;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;

/** Implements commands relating to map management */
public class Maps implements MiniMod {
    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("addmap", "", 
            data -> {
                data.usage = "<.msav attachment>";
                data.help = "Upload a map (attach an msav file)";
                data.roles = new long [] {Roles.MAP_SUBMISSIONS};
                data.aliases = new String[] { "uploadmap", "updatemap" };
            },
            ctx -> {
                if (ctx.event.getMessageAttachments().size() != 1) {
                    ctx.error("Wrong Number of Attachments", "Please provide one and only one attachment");
                    return;
                }
                
                MessageAttachment attachment= ctx.event.getMessageAttachments().get(0);
                byte[] data = attachment.downloadAsByteArray().join();
                if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
                    ctx.error("Invalid Save File", "`" + attachment.getFileName() + "` is corrupted or invalid.");
                    return;
                }
                
                Fi file = Core.settings.getDataDirectory().child("maps").child(attachment.getFileName());
                boolean didExist = file.exists();
                if (didExist) file.delete();
                file.writeBytes(data);
                
                Vars.maps.reload();

                ctx.success((didExist ? "Updated" : "Uploaded") + " New Map", "Filename: `" + attachment.getFileName() + "`");
            }    
        );
    }
}
