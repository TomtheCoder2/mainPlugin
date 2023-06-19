package mindustry.plugin.minimods;

import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.mod.Mods.LoadedMod;
import mindustry.mod.Plugin;
import mindustry.plugin.MiniMod;
import mindustry.plugin.discord.DiscordPalette;
import mindustry.plugin.discord.Roles;
import mindustry.plugin.discord.discordcommands.DiscordRegistrar;
import mindustry.plugin.utils.Utils;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class Mods implements MiniMod {
    private static LoadedMod findModByName(String name) {
        return Vars.mods.list().find(m -> {
            if (m.meta.displayName == null) {
                return Strings.stripColors(m.meta.name).equalsIgnoreCase(name);
            }
            return Strings.stripColors(m.meta.displayName).equalsIgnoreCase(name) ||
                    Strings.stripColors(m.meta.name).equalsIgnoreCase(name);
        });
    }

    @Override
    public void registerDiscordCommands(DiscordRegistrar handler) {
        handler.register("mod", "<mod...>",
                data -> {
                    data.help = "Get information about a specific mod";
                    data.category = "Mods";
                },
                ctx -> {
                    var mod = findModByName(ctx.args.get("mod"));
                    if (mod == null) {
                        ctx.error("No such mod", "Mod '" + ctx.args.get("mod") + "' is not loaded on the server");
                        return;
                    }

                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle(mod.meta.displayName)
                            .setColor(DiscordPalette.INFO)
                            .addInlineField("Internal Name", mod.name)
                            .addInlineField("Version", mod.meta.version)
                            .addInlineField("Author", mod.meta.author)
                            .addField("Description", mod.meta.description)
                            .addInlineField("Type", (mod.main != null && mod.main instanceof Plugin ? "Plugin" : "Mod"))
                            .addInlineField("Enabled", mod.enabled() + "");

                    if (mod.getRepo() != null) {
                        eb.addInlineField("Repo", mod.getRepo());
                    }

                    if (mod.dependencies.size != 0) {
                        eb.addField("Loaded Dependencies", mod.dependencies.toString("\n", x -> x.meta.name));
                    }
                    if (mod.missingDependencies.size != 0) {
                        eb.addField("Unmet Dependencies", mod.missingDependencies.toString("\n"));
                    }

                    ctx.sendEmbed(eb);
                }
        );


        handler.register("uploadmod", "[.zip|jar]", d -> {
            d.help = "Upload mod (include .zip file in message)";
            d.category = "Mods";
            d.roles = new long[]{Roles.ADMIN, Roles.DEV};
            d.aliases = new String[]{"umod"};
        }, ctx -> {
            Seq<MessageAttachment> ml = new Seq<>();
            for (MessageAttachment ma : ctx.event.getMessageAttachments()) {
                if (ma.getFileName().split("\\.")[ma.getFileName().split("\\.").length - 1].trim().equals("zip") ||
                        ma.getFileName().split("\\.")[ma.getFileName().split("\\.").length - 1].trim().equals("jar")) {
                    ml.add(ma);
                }
            }
            if (ml.size == 0) {
                ctx.error("Error", "Must have min one valid .zip|.jar file!");
                return;
            }
            for (MessageAttachment ma : ml) {

                if (Core.settings.getDataDirectory().child("mods").child(ma.getFileName()).exists()) {
                    ctx.error("Error", "Already a mod with this name!");
                    return;
                }

                CompletableFuture<byte[]> cf = ma.downloadAsByteArray();
                Fi fh = Core.settings.getDataDirectory().child("mods").child(ma.getFileName());

                try {
//                byte[] data = cf.get();
//                        if (!SaveIO.isSaveValid(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data))))) {
//                            eb.setTitle("Mod upload terminated.");
//                            eb.setColor(Pals.error);
//                            eb.setDescription("Mod file corrupted or invalid.");
//                            ctx.sendMessage(eb);
//                            return;
//                        }
                    fh.writeBytes(cf.get(), false);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Vars.maps.reload();
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("Mod upload completed. (" + ma.getFileName() + ")");
                ctx.sendEmbed(eb);
            }
            EmbedBuilder eb = new EmbedBuilder();
//            eb.setDescription(ml.get(0).getFileName() + " was added successfully into the mod folder!\n" +
//                    "Restart the server (`<restart <server>`) to activate the mod!");
            eb.setTitle("Server restart now");
            ctx.sendEmbed(eb);
            System.exit(0);
        });

        handler.register("removemod", "<modname/id...>", d -> {
            d.help = "Remove a mod from the folder";
            d.roles = new long[]{Roles.ADMIN, Roles.DEV};
            d.category = "Mods";
            d.aliases = new String[]{"rmod"};
        }, ctx -> {
            var mod = findModByName(ctx.args.get("modname/id"));
            if (mod == null) {
                ctx.error("Error", "Mod '" + ctx.args.get("modname/id") + "' not found");
                return;
            }
            Log.debug("Mod absolute path: " + mod.file.file().getAbsoluteFile().getAbsolutePath());
            Path path = Paths.get(mod.file.file().getAbsoluteFile().getAbsolutePath());
            String name = mod.name;
            mod.dispose();

            try {
                Files.delete(path);
            } catch (Exception e) {
                e.printStackTrace();
                ctx.error("Unknown error", e.getMessage());
                return;
            }

            if (mod.file.file().delete()) {
                ctx.success("Deleted Mod", "Successfully deleted mod " + Utils.escapeColorCodes(name) + ". Restart the server (`<restart <server>`) to deactivate the mod!");
            } else {
                ctx.error("Error", "Unable to delete mod? (The mod may still be deleted, not sure why the code deletes thrice).");
            }
        });

        handler.register("mods", "",
                data -> {
                    data.help = "List mods & versions";
                    data.aliases = new String[]{"version"};
                    data.category = "Mods";
                },
                ctx -> {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Mods")
                            .setColor(DiscordPalette.INFO);
                    if (Vars.mods.list().isEmpty()) {
                        eb.setDescription("None");
                    } else {
                        for (var mod : Vars.mods.list()) {
                            eb.addInlineField(mod.meta.displayName(), mod.meta.version);
                        }
                    }
                    ctx.sendEmbed(eb);
                }
        );
    }
}
